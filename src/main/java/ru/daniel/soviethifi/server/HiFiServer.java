package ru.daniel.soviethifi.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import ru.daniel.soviethifi.SovietHiFi;
import ru.daniel.soviethifi.block.*;
import ru.daniel.soviethifi.network.Network;
import ru.daniel.soviethifi.storage.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public final class HiFiServer {
    private static final Map<MinecraftServer,HiFiServer> INSTANCES = new IdentityHashMap<>();
    public static HiFiServer get(MinecraftServer server) { return INSTANCES.computeIfAbsent(server,HiFiServer::new); }
    public static void stop(MinecraftServer server) { var instance=INSTANCES.remove(server); if(instance!=null)instance.io.shutdownNow(); }
    private final MinecraftServer server;
    private final TrackStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {var t=new Thread(r,"Soviet Hi-Fi storage");t.setDaemon(true);return t;});
    private final Set<EquipmentEntity> decks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID,Pending> uploads = new HashMap<>();
    private final Map<UUID,Long> cooldown = new HashMap<>();
    private final Map<UUID,Long> controls = new HashMap<>();
    private final Map<UUID,Outgoing> downloads = new LinkedHashMap<>();
    private final Set<UUID> reading = new HashSet<>();
    private long ticks;
    private HiFiServer(MinecraftServer server) {
        this.server=server;
        try {store=new TrackStore(server.getWorldPath(LevelResource.ROOT).resolve("soviet-hifi/tracks"));}
        catch(IOException e){throw new IllegalStateException("Cannot open Soviet Hi-Fi music storage",e);}
    }
    public void track(EquipmentEntity deck) {decks.add(deck);}
    private void notice(ServerPlayer p,String message,int code){Network.send(p,new Network.Notice(message,code));}
    public void begin(ServerPlayer p,Network.Begin b) {
        if(!TrackRules.validId(b.id())||b.size()<32||b.size()>TrackRules.MAX_BYTES||b.seconds()<1||b.seconds()>TrackRules.MAX_SECONDS||!(b.format().equals("mp3")||b.format().equals("ogg"))) {notice(p,"Некорректный файл: максимум 16 МБ / 6 минут",-1);return;}
        InteractionHand hand=b.offHand()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
        ItemStack cassette=p.getItemInHand(hand);
        if(!cassette.is(SovietHiFi.CASSETTE.get())||cassette.isEmpty()){notice(p,"Держите кассету в руке до конца записи",-1);return;}
        if(uploads.containsKey(p.getUUID())){notice(p,"Предыдущая запись ещё выполняется",-1);return;}
        TrackStore.Meta existing=store.get(b.id());
        if(existing!=null){CassetteItem.finishRecording(p,hand,cassette,existing.id(),TrackRules.title(b.title()));notice(p,"Кассета записана — трек уже есть на сервере",2);return;}
        if(ticks<cooldown.getOrDefault(p.getUUID(),0L)){notice(p,"Новый трек можно загружать раз в минуту",-1);return;}
        if(uploads.size()>=4||store.used()+uploads.values().stream().mapToLong(x->x.begin.size()).sum()+b.size()>TrackRules.LIBRARY_BYTES){notice(p,"Музыкальное хранилище занято или заполнено",-1);return;}
        uploads.put(p.getUUID(),new Pending(b,cassette,hand,ticks)); cooldown.put(p.getUUID(),ticks+1200);
        notice(p,"Сервер готов к записи",1);
    }
    public void upload(ServerPlayer p,Network.Upload chunk) {
        Pending pending=uploads.get(p.getUUID());
        if(pending==null||pending.finishing)return;
        try {
            if(!pending.begin.id().equals(chunk.id())||p.getItemInHand(pending.hand)!=pending.cassette)throw new IOException("Кассета перемещена — запись отменена");
            if(pending.rateTick!=ticks){pending.rateTick=ticks;pending.packets=0;}
            if(++pending.packets>12)throw new IOException("Слишком быстрая передача");
            pending.data.append(chunk.offset(),chunk.bytes());pending.last=ticks;
            if(!pending.data.complete())return;
            byte[] bytes=pending.data.finish(chunk.id());pending.finishing=true;
            var b=pending.begin;
            io.execute(() -> {
                try {
                    store.put(new TrackStore.Meta(b.id(),TrackRules.title(b.title()),b.format(),b.size(),b.seconds()),bytes);
                    server.execute(() -> {
                        uploads.remove(p.getUUID());
                        if(CassetteItem.finishRecording(p,pending.hand,pending.cassette,b.id(),TrackRules.title(b.title()))) {
                            notice(p,"Кассета записана",2);
                        } else notice(p,"Трек сохранён. Возьмите кассету и повторите запись",-1);
                    });
                } catch(Exception e){server.execute(() -> {uploads.remove(p.getUUID());notice(p,"Ошибка сохранения: "+e.getMessage(),-1);});}
            });
        } catch(Exception e){uploads.remove(p.getUUID());notice(p,e.getMessage(),-1);}
    }
    public void fetch(ServerPlayer p,String id) {
        if(!TrackRules.validId(id)||downloads.containsKey(p.getUUID())||reading.contains(p.getUUID())||downloads.size()+reading.size()>=8)return;
        boolean audible=decks.stream().anyMatch(d -> valid(d)&&d.playing&&d.getLevel()==p.level()&&d.getBlockPos().distToCenterSqr(p.position())<80*80&&d.trackId().equals(id));
        TrackStore.Meta meta=store.get(id);if(!audible||meta==null)return;
        reading.add(p.getUUID());
        io.execute(() -> {
            try {byte[] data=store.read(id);server.execute(() -> {reading.remove(p.getUUID());if(server.getPlayerList().getPlayer(p.getUUID())==p)downloads.put(p.getUUID(),new Outgoing(meta,data));});}
            catch(Exception e){server.execute(() -> {reading.remove(p.getUUID());notice(p,"Не удалось прочитать трек",-2);});}
        });
    }
    private boolean valid(EquipmentEntity d) {return !d.isRemoved()&&d.getLevel()!=null&&d.getLevel().hasChunkAt(d.getBlockPos())&&d.getLevel().getBlockEntity(d.getBlockPos())==d;}
    public void tick() {
        ticks++;
        uploads.entrySet().removeIf(e -> {boolean expired=ticks-e.getValue().last>1200||server.getPlayerList().getPlayer(e.getKey())==null;if(expired){var p=server.getPlayerList().getPlayer(e.getKey());if(p!=null)notice(p,"Запись прервана по тайм-ауту",-1);}return expired;});
        int budget=16;
        var iterator=downloads.entrySet().iterator();
        while(iterator.hasNext()&&budget>0){var entry=iterator.next();ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());Outgoing o=entry.getValue();if(p==null){iterator.remove();continue;}
            for(int n=0;n<2&&o.offset<o.data.length&&budget>0;n++,budget--){int end=Math.min(o.data.length,o.offset+TrackRules.CHUNK);Network.send(p,new Network.Download(o.meta.id(),o.meta.format(),o.data.length,o.offset,Arrays.copyOfRange(o.data,o.offset,end)));o.offset=end;}
            if(o.offset==o.data.length)iterator.remove();
        }
        if(ticks%20!=0)return;
        decks.removeIf(d -> !valid(d));
        var states=new IdentityHashMap<EquipmentEntity,Network.DeckState>();
        for(EquipmentEntity deck:decks) {
            if(deck.getBlockState().getValue(EquipmentBlock.POWERED)!=deck.playing)deck.changed();
            if(!deck.playing)continue;
            var meta=store.get(deck.trackId());
            if(meta==null||deck.getLevel().getGameTime()-deck.started>=meta.seconds()*20L){deck.playing=false;deck.pausedTicks=0;deck.changed();continue;}
            EquipmentEntity amp=amplifier(deck);var speakers=amp==null?List.<BlockPos>of():speakers(amp);
            if(speakers.size()!=2){deck.pausedTicks=Math.max(0,deck.getLevel().getGameTime()-deck.started);deck.playing=false;deck.changed();continue;}
            states.put(deck,new Network.DeckState(deck.getBlockPos(),meta.id(),TrackRules.title(deck.tapes.getFirst().getHoverName().getString()),deck.started,amp.volume,amp.tape,speakers));
        }
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            var nearby=states.entrySet().stream().filter(e -> e.getKey().getLevel()==p.level()&&e.getKey().getBlockPos().distToCenterSqr(p.position())<80*80)
                .sorted(Comparator.comparingDouble(e -> e.getKey().getBlockPos().distToCenterSqr(p.position()))).limit(4).map(Map.Entry::getValue).toList();
            Network.send(p,new Network.Snapshot(p.level().getGameTime(),nearby));
        }
        if(ticks%1200==0){cooldown.keySet().removeIf(id -> server.getPlayerList().getPlayer(id)==null);controls.keySet().removeIf(id -> server.getPlayerList().getPlayer(id)==null);}
    }
    private List<EquipmentEntity> nearby(EquipmentEntity origin,EquipmentBlock.Kind kind,int radius) {
        var result=new ArrayList<EquipmentEntity>();var level=origin.getLevel();if(level==null)return result;
        for(BlockPos p:BlockPos.betweenClosed(origin.getBlockPos().offset(-radius,-radius,-radius),origin.getBlockPos().offset(radius,radius,radius)))
            if(level.hasChunkAt(p)&&level.getBlockEntity(p) instanceof EquipmentEntity e&&e.supports(kind)&&origin.getBlockPos().distSqr(p)<=radius*radius)result.add(e);
        result.sort(Comparator.<EquipmentEntity>comparingDouble(e->e.getBlockPos().distSqr(origin.getBlockPos())).thenComparingLong(e->e.getBlockPos().asLong()));return result;
    }
    private EquipmentEntity amplifier(EquipmentEntity deck) {
        if(deck.supports(EquipmentBlock.Kind.AMP))return deck;
        var amps=nearby(deck,EquipmentBlock.Kind.AMP,2);if(amps.isEmpty())return null;
        var amp=amps.getFirst();var owners=nearby(amp,EquipmentBlock.Kind.DECK,2);
        return !owners.isEmpty()&&owners.getFirst()==deck?amp:null;
    }
    private List<BlockPos> speakers(EquipmentEntity amp) {
        return nearby(amp,EquipmentBlock.Kind.SPEAKER,4).stream().filter(s -> {var a=nearby(s,EquipmentBlock.Kind.AMP,4);return !a.isEmpty()&&a.getFirst()==amp;}).limit(2)
            .sorted(Comparator.comparingDouble(s -> {
                var right=amp.getBlockState().getValue(EquipmentBlock.FACING).getClockWise();
                return s.getBlockPos().getX()*right.getStepX()+s.getBlockPos().getZ()*right.getStepZ();
            })).map(EquipmentEntity::getBlockPos).toList();
    }
    public void play(EquipmentEntity deck,Player player) {
        var meta=store.get(deck.trackId());var amp=amplifier(deck);
        if(meta==null){player.sendOverlayMessage(Component.literal("Трек этой кассеты отсутствует на сервере"));return;}
        if(amp==null||speakers(amp).size()!=2){player.sendOverlayMessage(Component.literal("Нужны Амфитон рядом с декой (2 блока) и две S-90 рядом с усилителем (4 блока)"));return;}
        if(deck.pausedTicks>=meta.seconds()*20L)deck.pausedTicks=0;
        deck.started=deck.getLevel().getGameTime()-deck.pausedTicks;deck.playing=true;deck.changed();
    }
    public void control(ServerPlayer p,Network.Control c) {
        if(!Float.isFinite(c.value())||!p.level().hasChunkAt(c.pos())||c.pos().distToCenterSqr(p.position())>64||p.isSpectator())return;
        if(ticks<controls.getOrDefault(p.getUUID(),0L))return;controls.put(p.getUUID(),ticks+2);
        if(!(p.level().getBlockEntity(c.pos()) instanceof EquipmentEntity e))return;
        // Respect vanilla spawn protection / server interaction restrictions.
        if(!p.level().mayInteract(p,c.pos()))return;
        EquipmentEntity deck=e.supports(EquipmentBlock.Kind.DECK)?e:nearby(e,EquipmentBlock.Kind.DECK,2).stream().findFirst().orElse(null);
        EquipmentEntity amp=e.supports(EquipmentBlock.Kind.AMP)?e:deck==null?null:amplifier(deck);
        switch(c.action()){
            case 1 -> {if(deck!=null){if(deck.playing){deck.pausedTicks=Math.max(0,deck.getLevel().getGameTime()-deck.started);deck.playing=false;deck.changed();}else play(deck,p);}}
            case 2 -> {if(deck!=null){deck.playing=false;deck.pausedTicks=0;deck.changed();}}
            case 3 -> {if(deck!=null)deck.eject(p);}
            case 4 -> {if(amp!=null){amp.volume=Math.clamp(c.value(),0,1);amp.changed();}}
            case 5 -> {if(amp!=null){amp.tape=Math.clamp(c.value(),0,1);amp.changed();}}
            case 6 -> {if(e.kind()==EquipmentBlock.Kind.RACK)e.ejectAt(p,(int)c.value());}
            default -> {}
        }
        var inventory=e.kind()==EquipmentBlock.Kind.RACK?e:deck;
        var titles=inventory==null?List.<String>of():inventory.tapes.stream().map(s -> TrackRules.title(s.getHoverName().getString())).toList();
        Network.send(p,new Network.Panel(c.pos(),titles,deck!=null&&deck.playing,amp==null?0.65f:amp.volume,amp==null?0.35f:amp.tape));
    }
    private static final class Pending {
        final Network.Begin begin;final ItemStack cassette;final InteractionHand hand;final ChunkAssembly data;
        long last,rateTick;int packets;boolean finishing;
        Pending(Network.Begin b,ItemStack s,InteractionHand h,long tick){begin=b;cassette=s;hand=h;data=new ChunkAssembly(b.size());last=tick;}
    }
    private static final class Outgoing {final TrackStore.Meta meta;final byte[] data;int offset;Outgoing(TrackStore.Meta m,byte[] b){meta=m;data=b;}}
}
