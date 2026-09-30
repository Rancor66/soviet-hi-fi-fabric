package ru.daniel.soviethifi.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.daniel.soviethifi.network.Network;

/** Client-only Fabric calls stay out of the dedicated server class loader. */
final class ClientNetwork {
    static void init() {
        Network.serverSender = ClientPlayNetworking::send;
        Network.CLIENT_TYPES.forEach(ClientNetwork::register);
    }
    private static <T extends Network.ClientMessage> void register(CustomPacketPayload.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> Network.client.accept(payload));
    }
}
