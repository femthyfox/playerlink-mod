package com.playerlink.network;

import com.playerlink.client.ClientPacketHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ClientNetwork {

    private ClientNetwork() {}

    public static void registerClient(PayloadRegistrar registrar) {
        registrar.playToClient(
                WhitelistResponsePacket.TYPE,
                WhitelistResponsePacket.STREAM_CODEC,
                ClientPacketHandlers::handleWhitelistResponse
        );
    }
}