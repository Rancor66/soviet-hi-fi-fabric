package ru.daniel.soviethifi.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import ru.daniel.soviethifi.SovietHiFi;
import ru.daniel.soviethifi.audio.*;
import ru.daniel.soviethifi.network.Network;
import ru.daniel.soviethifi.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class HiFiClient implements ClientModInitializer {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static String playbackLog = "";
    private static final Minecraft MC=Minecraft.getInstance();
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Soviet Hi-Fi decoder");t.setDaemon(true);return t;});
    private static final Map<String,PcmTrack> PCM=new LinkedHashMap<>(8,0.75f,true);
    private static final Map<String,Long> FAILED=new HashMap<>();
    private static final TapeMixer MIXER=new TapeMixer(message->MC.execute(()->message(message)));
    private static Network.Snapshot snapshot=new Network.Snapshot(0,List.of());
    private static long receivedNanos;
    private static Object lastLevel;
    private static int epoch,ticks;
    private static String requested="";
    private static long requestTime;
    private static ChunkAssembly incoming;
    private static UploadTask upload;
    public static String status="MP3 или OGG Vorbis • до 16 МБ / 6 минут";
    public static boolean preparing;
    public static float personalVolume=1;
    public HiFiClient() {}
    @Override public void onInitializeClient() {
        Network.client=HiFiClient::receive;
        ClientNetwork.init();
        ClientTickEvents.END_CLIENT_TICK.register(_->tick());
    }
    public static void message(String text) {
        LOG.info("Hi-Fi: {}", text);
        status=text;
        if(MC.player!=null)MC.player.sendSystemMessage(Component.literal("§6[Hi-Fi] §r"+text));
    }
    public static void record(Path path,String title,boolean offHand) {
        if(preparing||upload!=null)return;
        preparing=true;status="Проверяю и декодирую файл…";int generation=epoch;
        WORKER.execute(()->{
            try {
                String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
                String format=name.endsWith(".mp3")?"mp3":name.endsWith(".ogg")?"ogg":"";
                if(format.isEmpty()||!Files.isRegularFile(path)||Files.size(path)>TrackRules.MAX_BYTES)throw new IllegalArgumentException("Выберите MP3/OGG размером до 16 МБ");
                byte[] bytes=Files.readAllBytes(path);PcmTrack pcm=TrackDecoder.decode(bytes,format);String id=TrackRules.hash(bytes);
                String label=TrackRules.title(title.isBlank()?path.getFileName().toString().replaceFirst("\\.[^.]+$",""):title);
                cache(id,format,bytes);
                MC.execute(()->{if(generation!=epoch)return;preparing=false;putPcm(id,pcm);upload=new UploadTask(id,bytes);status="Ожидаю сервер…";Network.sendServer(new Network.Begin(id,label,format,bytes.length,(int)Math.ceil(pcm.seconds()),offHand));});
            }catch(Exception e){MC.execute(()->{if(generation==epoch){preparing=false;message("Не удалось записать: "+e.getMessage());}});}
        });
    }
    private static void receive(Network.ClientMessage msg) {
        if(msg instanceof Network.OpenUi ui){MC.gui.setScreen(new HiFiScreen(ui));return;}
        if(msg instanceof Network.Panel p){if(MC.gui.screen() instanceof HiFiScreen screen)screen.panel(p);return;}
        if(msg instanceof Network.Notice n){
            status=n.message();if(n.code()==1&&upload!=null)upload.accepted=true;
            if(n.code()==2||n.code()==-1){upload=null;message(n.message());}return;
        }
        if(msg instanceof Network.Snapshot s){
            snapshot=s;receivedNanos=System.nanoTime();
            String signature=s.decks().stream().map(d->d.id()+":"+d.started()).reduce("",String::concat);
            if(!signature.equals(playbackLog)){
                playbackLog=signature;
                for(var d:s.decks())LOG.info("Hi-Fi sync: title={}, start={}, serverTick={}, offsetSeconds={}, speakers={}",d.title(),d.started(),s.tick(),(s.tick()-d.started())/20.0,d.speakers().size());
            }
            return;
        }
        if(msg instanceof Network.Download d){
            if(!d.id().equals(requested))return;
            try {
                if(d.offset()==0)incoming=new ChunkAssembly(d.size());
                if(incoming==null)throw new IllegalArgumentException("Нет начала загрузки");
                incoming.append(d.offset(),d.bytes());requestTime=System.nanoTime();
                if(incoming.complete()) {byte[] data=incoming.finish(d.id());incoming=null;decode(d.id(),d.format(),data,epoch);}
            }catch(Exception e){FAILED.put(d.id(),System.nanoTime());requested="";incoming=null;message("Ошибка загрузки музыки: "+e.getMessage());}
        }
    }
    private static void putPcm(String id,PcmTrack pcm){LOG.info("Hi-Fi decoded: {} frames, {} Hz, {} seconds",pcm.frames(),pcm.sampleRate(),pcm.seconds());PCM.put(id,pcm);while(PCM.size()>4)PCM.remove(PCM.keySet().iterator().next());}
    private static Path cacheDir(){return MC.gameDirectory.toPath().resolve("soviet-hifi/cache");}
    private static void cache(String id,String format,byte[] bytes)throws Exception {
        if(!TrackRules.validId(id)||!(format.equals("mp3")||format.equals("ogg")))throw new IllegalArgumentException("Некорректный кэш");
        Path dir=cacheDir();Files.createDirectories(dir);
        try(var listing=Files.list(dir)){
            var files=listing.filter(Files::isRegularFile).sorted(Comparator.comparingLong(p->p.toFile().lastModified())).toList();
            long total=bytes.length;for(Path p:files)total+=Files.size(p);
            for(Path p:files){if(total<=256L*1024*1024)break;long length=Files.size(p);Files.delete(p);total-=length;}
        }
        Files.write(dir.resolve(id+"."+format),bytes);
    }
    private static void decode(String id,String format,byte[] bytes,int generation){
        WORKER.execute(()->{
            try{PcmTrack pcm=TrackDecoder.decode(bytes,format);cache(id,format,bytes);MC.execute(()->{if(epoch==generation){putPcm(id,pcm);requested="";}});}
            catch(Exception e){MC.execute(()->{if(epoch==generation){FAILED.put(id,System.nanoTime());requested="";message("Трек не воспроизводится: "+e.getMessage());}});}
        });
    }
    private static void request(String id){
        if(!requested.isEmpty()||!TrackRules.validId(id)||System.nanoTime()-FAILED.getOrDefault(id,0L)<60_000_000_000L)return;
        requested=id;requestTime=System.nanoTime();int generation=epoch;
        WORKER.execute(()->{
            for(String format:List.of("mp3","ogg")){
                Path path=cacheDir().resolve(id+"."+format);
                try{if(!Files.exists(path)||Files.size(path)>TrackRules.MAX_BYTES)continue;byte[] data=Files.readAllBytes(path);if(!TrackRules.hash(data).equals(id))continue;PcmTrack pcm=TrackDecoder.decode(data,format);MC.execute(()->{if(generation==epoch){putPcm(id,pcm);requested="";}});return;}catch(Exception ignored){}
            }
            MC.execute(()->{if(generation==epoch)Network.sendServer(new Network.Fetch(id));});
        });
    }
    private static void tick(){
        if(MC.level!=lastLevel){epoch++;lastLevel=MC.level;snapshot=new Network.Snapshot(0,List.of());requested="";incoming=null;upload=null;preparing=false;MIXER.update(List.of());if(MC.level==null){PCM.clear();FAILED.clear();MIXER.close();}}
        if(MC.level==null||MC.player==null)return;
        ticks++;
        if(upload!=null&&upload.accepted){
            for(int i=0;i<4&&upload.offset<upload.bytes.length;i++){int end=Math.min(upload.bytes.length,upload.offset+TrackRules.CHUNK);Network.sendServer(new Network.Upload(upload.id,upload.offset,Arrays.copyOfRange(upload.bytes,upload.offset,end)));upload.offset=end;}
            status=upload.offset==upload.bytes.length?"Сервер сохраняет кассету…":"Запись: "+(100L*upload.offset/upload.bytes.length)+"%";
        }
        if(!requested.isEmpty()&&System.nanoTime()-requestTime>60_000_000_000L){FAILED.put(requested,System.nanoTime());requested="";incoming=null;}
        if(ticks%2!=0)return;
        var voices=new ArrayList<TapeMixer.Voice>();
        if(System.nanoTime()-receivedNanos>4_000_000_000L){MIXER.update(voices);return;}
        Vec3 ear=MC.player.getEyePosition();
        double yaw=Math.toRadians(MC.player.getYRot());
        float master=MC.options.getSoundSourceVolume(SoundSource.MASTER)*MC.options.getSoundSourceVolume(SoundSource.RECORDS)*personalVolume;
        long now=System.nanoTime();
        for(var deck:snapshot.decks()){
            PcmTrack track=PCM.get(deck.id());if(track==null){request(deck.id());continue;}
            float volume=Float.isFinite(deck.volume())?Math.clamp(deck.volume(),0,1):0;
            float tape=Float.isFinite(deck.tape())?Math.clamp(deck.tape(),0,1):0;
            double seconds=Math.max(0,(snapshot.tick()-deck.started())/20.0+(now-receivedNanos)/1e9);
            int channel=0;
            for(BlockPos speaker:deck.speakers()){
                Vec3 sound=Vec3.atCenterOf(speaker);Vec3 direction=sound.subtract(ear);double distance=direction.length();
                double wall=obstruction(sound,ear);
                float gain=AudioMath.distanceGain(distance,16+volume*48)*AudioMath.wallGain(wall)*volume*master*0.8f;
                double pan=distance<0.01?0:Math.clamp((-Math.cos(yaw)*direction.x-Math.sin(yaw)*direction.z)/distance,-1,1);
                voices.add(new TapeMixer.Voice(deck.pos().asLong()+":"+deck.id()+":"+deck.started()+":"+channel,track,seconds*track.sampleRate(),now,
                    gain*(float)Math.sqrt((1-pan)/2),gain*(float)Math.sqrt((1+pan)/2),AudioMath.cutoff(wall,tape),tape,channel++));
            }
        }
        MIXER.update(voices);
    }
    private static double obstruction(Vec3 from,Vec3 to){
        double distance=from.distanceTo(to);int steps=Math.min(256,(int)(distance*3));Set<BlockPos> visited=new HashSet<>();double total=0;
        for(int i=1;i<steps;i++){
            BlockPos pos=BlockPos.containing(from.lerp(to,i/(double)steps));
            if(pos.equals(BlockPos.containing(from))||!visited.add(pos)||!MC.level.hasChunkAt(pos))continue;
            var state=MC.level.getBlockState(pos);
            if(state.isAir()||state.getCollisionShape(MC.level,pos).isEmpty())continue;
            // Open doors/trapdoors must not act like closed walls.
            if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))continue;
            if(state.is(BlockTags.WOOL))total+=1.8;
            else if(state.is(Blocks.GLASS)||state.is(Blocks.GLASS_PANE))total+=0.35;
            else if(state.is(BlockTags.PLANKS)||state.is(BlockTags.WOODEN_DOORS))total+=0.65;
            else total+=1;
            if(total>=8)break;
        }
        return total;
    }
    private static final class UploadTask{final String id;final byte[] bytes;int offset;boolean accepted;UploadTask(String id,byte[] bytes){this.id=id;this.bytes=bytes;}}
}
