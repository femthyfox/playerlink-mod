package com.playerlink.network;

import com.playerlink.PlayerLinkMod;
import com.playerlink.server.ServerPacketHandlers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = PlayerLinkMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PlayerLinkNetwork {

    public static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PlayerLinkMod.LOGGER.info("[PlayerLink] >>> register() ENTERED, side={}", FMLEnvironment.dist);

        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(RequestWhitelistPacket.TYPE, RequestWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestWhitelist);
        registrar.playToServer(SetOwnerPacket.TYPE, SetOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetOwner);
        registrar.playToServer(RequestControllerWhitelistPacket.TYPE, RequestControllerWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestControllerWhitelist);
        registrar.playToServer(SetControllerSlotOwnerPacket.TYPE, SetControllerSlotOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetControllerSlotOwner);
        registrar.playToServer(ClearAllControllerOwnersPacket.TYPE, ClearAllControllerOwnersPacket.STREAM_CODEC,
                ServerPacketHandlers::handleClearAllControllerOwners);
        registrar.playToServer(RequestTypewriterWhitelistPacket.TYPE, RequestTypewriterWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestTypewriterWhitelist);
        registrar.playToServer(SetTypewriterKeyOwnerPacket.TYPE, SetTypewriterKeyOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetTypewriterKeyOwner);

        if (FMLEnvironment.dist.isClient()) {
            try {
                Class.forName("com.playerlink.network.ClientNetwork")
                        .getMethod("registerClient", PayloadRegistrar.class)
                        .invoke(null, registrar);
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] Failed to register client handler", t);
            }
        } else {
            registrar.playToClient(WhitelistResponsePacket.TYPE, WhitelistResponsePacket.STREAM_CODEC,
                    (pkt, ctx) -> {});
            registrar.playToClient(ControllerWhitelistResponsePacket.TYPE, ControllerWhitelistResponsePacket.STREAM_CODEC,
                    (pkt, ctx) -> {});
            registrar.playToClient(TypewriterWhitelistResponsePacket.TYPE, TypewriterWhitelistResponsePacket.STREAM_CODEC,
                    (pkt, ctx) -> {});
        }

        PlayerLinkMod.LOGGER.info("[PlayerLink] <<< register() FINISHED");
    }
}