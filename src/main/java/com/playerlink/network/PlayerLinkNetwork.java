package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import com.playerlink.server.ServerPacketHandlers;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class PlayerLinkNetwork {

    public static final String PROTOCOL_VERSION = "1";

    public static void register(final RegisterPayloadHandlersEvent event) {
        PlayerLinkMod.LOGGER.info("[PlayerLink] Registering network payloads on side={}",
                FMLEnvironment.dist);

        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(
                RequestWhitelistPacket.TYPE,
                RequestWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestWhitelist
        );
        PlayerLinkMod.LOGGER.info("[PlayerLink] Registered playToServer: request_whitelist");

        if (FMLEnvironment.dist.isClient()) {
            ClientNetwork.registerClient(registrar);
        } else {
            registrar.playToClient(
                    WhitelistResponsePacket.TYPE,
                    WhitelistResponsePacket.STREAM_CODEC,
                    (pkt, ctx) -> {}
            );
        }
        PlayerLinkMod.LOGGER.info("[PlayerLink] Registered playToClient: whitelist_response");

        registrar.playToServer(
                SetOwnerPacket.TYPE,
                SetOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetOwner
        );
        PlayerLinkMod.LOGGER.info("[PlayerLink] Registered playToServer: set_owner");
    }
}
