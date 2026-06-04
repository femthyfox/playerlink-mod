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

    public static final String PROTOCOL_VERSION = "5";

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // ── Redstone Link ──────────────────────────────────────────────────────
        registrar.playToServer(RequestWhitelistPacket.TYPE,
                RequestWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestWhitelist);
        registrar.playToServer(SetOwnerPacket.TYPE,
                SetOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetOwner);

        // ── Link Controller ────────────────────────────────────────────────────
        registrar.playToServer(RequestControllerWhitelistPacket.TYPE,
                RequestControllerWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestControllerWhitelist);
        registrar.playToServer(SetControllerSlotOwnerPacket.TYPE,
                SetControllerSlotOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetControllerSlotOwner);
        registrar.playToServer(ClearAllControllerOwnersPacket.TYPE,
                ClearAllControllerOwnersPacket.STREAM_CODEC,
                ServerPacketHandlers::handleClearAllControllerOwners);
        registrar.playToServer(CopyLinkFrequencyPacket.TYPE,
                CopyLinkFrequencyPacket.STREAM_CODEC,
                ServerPacketHandlers::handleCopyLinkFrequency);

        // ── Linked Typewriter (block-level owner) ──────────────────────────────
        registrar.playToServer(RequestTypewriterWhitelistPacket.TYPE,
                RequestTypewriterWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestTypewriterWhitelist);
        registrar.playToServer(SetTypewriterOwnerPacket.TYPE,
                SetTypewriterOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetTypewriterOwner);

        // ── Linked Typewriter (per-key owner) ──────────────────────────────────
        registrar.playToServer(RequestTypewriterKeyWhitelistPacket.TYPE,
                RequestTypewriterKeyWhitelistPacket.STREAM_CODEC,
                ServerPacketHandlers::handleRequestTypewriterKeyWhitelist);
        registrar.playToServer(SetTypewriterKeyOwnerPacket.TYPE,
                SetTypewriterKeyOwnerPacket.STREAM_CODEC,
                ServerPacketHandlers::handleSetTypewriterKeyOwner);

        // ── Server → client ────────────────────────────────────────────────────
        if (FMLEnvironment.dist.isClient()) {
            try {
                Class.forName("com.playerlink.network.ClientNetwork")
                        .getMethod("registerClient", PayloadRegistrar.class)
                        .invoke(null, registrar);
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] Failed to register client handlers", t);
            }
        } else {
            registrar.playToClient(WhitelistResponsePacket.TYPE,
                    WhitelistResponsePacket.STREAM_CODEC, (p, c) -> {});
            registrar.playToClient(ControllerWhitelistResponsePacket.TYPE,
                    ControllerWhitelistResponsePacket.STREAM_CODEC, (p, c) -> {});
            registrar.playToClient(TypewriterWhitelistResponsePacket.TYPE,
                    TypewriterWhitelistResponsePacket.STREAM_CODEC, (p, c) -> {});
            registrar.playToClient(TypewriterKeyWhitelistResponsePacket.TYPE,
                    TypewriterKeyWhitelistResponsePacket.STREAM_CODEC, (p, c) -> {});
        }
    }
}
