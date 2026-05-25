package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import com.playerlink.server.ServerPacketHandlers;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PlayerLinkNetwork {

    public static final String PROTOCOL_VERSION = "1";

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                RequestWhitelistPacket.TYPE,
                RequestWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestWhitelist
        );

        if (FMLEnvironment.dist.isClient()) {
            ClientNetwork.registerClient(registrar);
        } else {
            registrar.playToClient(
                    WhitelistResponsePacket.TYPE,
                    WhitelistResponsePacket.STREAM_CODEC,
                    (pkt, ctx) -> {}
            );
        }

        registrar.playToServer(
                SetOwnerPacket.TYPE,
                SetOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetOwner
        );
    }
}
