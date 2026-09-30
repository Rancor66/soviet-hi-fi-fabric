package ru.daniel.soviethifi.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.function.BiConsumer;
import java.util.function.Function;
import ru.daniel.soviethifi.server.HiFiServer;
import ru.daniel.soviethifi.storage.TrackRules;
import java.util.*;
import java.util.function.Consumer;

public final class Network {
    private static final Map<Class<?>, CustomPacketPayload.Type<?>> TYPES = new HashMap<>();
    public static final List<CustomPacketPayload.Type<? extends ClientMessage>> CLIENT_TYPES = new ArrayList<>();
    public interface Message extends CustomPacketPayload {
        @Override default Type<? extends CustomPacketPayload> type() { return TYPES.get(getClass()); }
    }
    public interface ClientMessage extends Message {}
    public interface ServerMessage extends Message {}
    public static Consumer<ServerMessage> serverSender = _ -> { throw new IllegalStateException("Client networking not initialized"); };
    public static Consumer<ClientMessage> client = _ -> {};
    public record Begin(String id, String title, String format, int size, int seconds, boolean offHand) implements ServerMessage {}
    public record Upload(String id, int offset, byte[] bytes) implements ServerMessage {}
    public record Fetch(String id) implements ServerMessage {}
    public record Control(BlockPos pos, int action, float value) implements ServerMessage {}
    public record OpenUi(int kind, BlockPos pos, boolean offHand) implements ClientMessage {}
    public record Notice(String message, int code) implements ClientMessage {}
    public record Download(String id, String format, int size, int offset, byte[] bytes) implements ClientMessage {}
    public record DeckState(BlockPos pos, String id, String title, long started, float volume, float tape, List<BlockPos> speakers) {}
    public record Snapshot(long tick, List<DeckState> decks) implements ClientMessage {}
    public record Panel(BlockPos pos, List<String> titles, boolean playing, float volume, float tape) implements ClientMessage {}
    public static void init() {
        register(Begin.class, true)
            .encoder((m,b) -> { b.writeUtf(m.id,64); b.writeUtf(m.title,80); b.writeUtf(m.format,3); b.writeVarInt(m.size); b.writeVarInt(m.seconds); b.writeBoolean(m.offHand); })
            .decoder(b -> new Begin(b.readUtf(64),b.readUtf(80),b.readUtf(3),b.readVarInt(),b.readVarInt(),b.readBoolean()))
            .receiver((m,c) -> { if (c.getSender() != null) HiFiServer.get(c.getSender().level().getServer()).begin(c.getSender(),m); }).add();
        register(Upload.class, true)
            .encoder((m,b) -> { b.writeUtf(m.id,64); b.writeVarInt(m.offset); b.writeByteArray(m.bytes); })
            .decoder(b -> new Upload(b.readUtf(64),b.readVarInt(),b.readByteArray(TrackRules.CHUNK)))
            .receiver((m,c) -> { if (c.getSender() != null) HiFiServer.get(c.getSender().level().getServer()).upload(c.getSender(),m); }).add();
        register(Fetch.class, true)
            .encoder((m,b) -> b.writeUtf(m.id,64)).decoder(b -> new Fetch(b.readUtf(64)))
            .receiver((m,c) -> { if (c.getSender() != null) HiFiServer.get(c.getSender().level().getServer()).fetch(c.getSender(),m.id); }).add();
        register(Control.class, true)
            .encoder((m,b) -> { b.writeBlockPos(m.pos); b.writeVarInt(m.action); b.writeFloat(m.value); })
            .decoder(b -> new Control(b.readBlockPos(),b.readVarInt(),b.readFloat()))
            .receiver((m,c) -> { if (c.getSender() != null) HiFiServer.get(c.getSender().level().getServer()).control(c.getSender(),m); }).add();
        register(OpenUi.class, false)
            .encoder((m,b) -> { b.writeVarInt(m.kind); b.writeBlockPos(m.pos); b.writeBoolean(m.offHand); })
            .decoder(b -> new OpenUi(b.readVarInt(),b.readBlockPos(),b.readBoolean())).receiver((m,c) -> client.accept(m)).add();
        register(Notice.class, false)
            .encoder((m,b) -> { b.writeUtf(m.message,256); b.writeVarInt(m.code); })
            .decoder(b -> new Notice(b.readUtf(256),b.readVarInt())).receiver((m,c) -> client.accept(m)).add();
        register(Download.class, false)
            .encoder((m,b) -> { b.writeUtf(m.id,64); b.writeUtf(m.format,3); b.writeVarInt(m.size); b.writeVarInt(m.offset); b.writeByteArray(m.bytes); })
            .decoder(b -> new Download(b.readUtf(64),b.readUtf(3),b.readVarInt(),b.readVarInt(),b.readByteArray(TrackRules.CHUNK)))
            .receiver((m,c) -> client.accept(m)).add();
        register(Snapshot.class, false)
            .encoder((m,b) -> {
                b.writeLong(m.tick); b.writeVarInt(m.decks.size());
                for (DeckState d : m.decks) { b.writeBlockPos(d.pos); b.writeUtf(d.id,64); b.writeUtf(d.title,80); b.writeLong(d.started); b.writeFloat(d.volume); b.writeFloat(d.tape); b.writeVarInt(d.speakers.size()); for (BlockPos p : d.speakers) b.writeBlockPos(p); }
            }).decoder(b -> {
                long tick = b.readLong(); int count = bounded(b.readVarInt(),16); var decks = new ArrayList<DeckState>();
                for (int i=0;i<count;i++) { BlockPos pos=b.readBlockPos(); String id=b.readUtf(64),title=b.readUtf(80); long start=b.readLong(); float volume=b.readFloat(),tape=b.readFloat(); int n=bounded(b.readVarInt(),2); var speakers=new ArrayList<BlockPos>(); for(int j=0;j<n;j++)speakers.add(b.readBlockPos()); decks.add(new DeckState(pos,id,title,start,volume,tape,speakers)); }
                return new Snapshot(tick,decks);
            }).receiver((m,c) -> client.accept(m)).add();
        register(Panel.class, false)
            .encoder((m,b) -> { b.writeBlockPos(m.pos); b.writeVarInt(m.titles.size()); for(String title:m.titles)b.writeUtf(title,80); b.writeBoolean(m.playing); b.writeFloat(m.volume); b.writeFloat(m.tape); })
            .decoder(b -> { BlockPos p=b.readBlockPos(); int n=bounded(b.readVarInt(),12); var titles=new ArrayList<String>(); for(int i=0;i<n;i++)titles.add(b.readUtf(80)); return new Panel(p,titles,b.readBoolean(),b.readFloat(),b.readFloat()); })
            .receiver((m,c) -> client.accept(m)).add();

    }
    private static int bounded(int count,int max) { if(count<0||count>max)throw new IllegalArgumentException("Invalid packet count"); return count; }
    public static void send(ServerPlayer player, ClientMessage message) { if (ServerPlayNetworking.canSend(player, message.type())) ServerPlayNetworking.send(player, message); }
    public static void sendServer(ServerMessage message) { serverSender.accept(message); }

    private static <T extends Message> Registration<T> register(Class<T> type, boolean serverbound) {
        return new Registration<>(type, serverbound);
    }
    private record Context(ServerPlayer getSender) {}
    private static final class Registration<T extends Message> {
        private final Class<T> messageClass;
        private final boolean serverbound;
        private BiConsumer<T, FriendlyByteBuf> encoder;
        private Function<FriendlyByteBuf,T> decoder;
        private BiConsumer<T,Context> receiver;
        Registration(Class<T> messageClass, boolean serverbound) { this.messageClass=messageClass; this.serverbound=serverbound; }
        Registration<T> encoder(BiConsumer<T,FriendlyByteBuf> encoder) { this.encoder=encoder; return this; }
        Registration<T> decoder(Function<FriendlyByteBuf,T> decoder) { this.decoder=decoder; return this; }
        Registration<T> receiver(BiConsumer<T,Context> receiver) { this.receiver=receiver; return this; }
        @SuppressWarnings("unchecked")
        void add() {
            var type = new CustomPacketPayload.Type<T>(Identifier.fromNamespaceAndPath("soviet_hifi", messageClass.getSimpleName().toLowerCase(Locale.ROOT) + "_v1"));
            TYPES.put(messageClass, type);
            StreamCodec<RegistryFriendlyByteBuf,T> codec = new StreamCodec<>() {
                public T decode(RegistryFriendlyByteBuf buf) { return decoder.apply(buf); }
                public void encode(RegistryFriendlyByteBuf buf,T value) { encoder.accept(value,buf); }
            };
            if (serverbound) {
                PayloadTypeRegistry.serverboundPlay().register(type,codec);
                ServerPlayNetworking.registerGlobalReceiver(type,(payload,context) -> receiver.accept(payload,new Context(context.player())));
            } else {
                PayloadTypeRegistry.clientboundPlay().register(type,codec);
                CLIENT_TYPES.add((CustomPacketPayload.Type<? extends ClientMessage>)(CustomPacketPayload.Type<?>)type);
            }
        }
    }
}
