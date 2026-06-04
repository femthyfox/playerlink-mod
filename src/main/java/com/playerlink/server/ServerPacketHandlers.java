package com.playerlink.server;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.compat.TypewriterCompat;
import com.playerlink.network.*;
import com.playerlink.util.ControllerOwners;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public final class ServerPacketHandlers {

    private static final double MAX_REACH_SQR = 1296.0;

    private ServerPacketHandlers() {}

    // ── Redstone Link ─────────────────────────────────────────────────────────

    public static void handleRequestWhitelist(final RequestWhitelistPacket pkt,
                                               final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            UUID current = ((IOwnedLink) be).playerlink$getOwner();
            PacketDistributor.sendToPlayer(sp, new WhitelistResponsePacket(
                    pkt.blockPos(), Optional.ofNullable(current), collectWhitelist(server)));
        });
    }

    public static void handleSetOwner(final SetOwnerPacket pkt,
                                       final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity link)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            pkt.newOwner().ifPresentOrElse(
                    uuid -> { if (validateOwner(server, uuid)) ((IOwnedLink) link).playerlink$setOwner(uuid); },
                    ()   -> ((IOwnedLink) link).playerlink$setOwner(null)
            );
        });
    }

    // ── Link Controller ───────────────────────────────────────────────────────

    public static void handleRequestControllerWhitelist(final RequestControllerWhitelistPacket pkt,
                                                         final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            int slot = pkt.slotIndex();
            if (slot < 0 || slot >= ControllerOwners.SLOT_COUNT) return;
            ItemStack ctrl = findController(sp);
            if (ctrl.isEmpty()) return;

            UUID current = ControllerOwners.get(ctrl, slot);
            PacketDistributor.sendToPlayer(sp, new ControllerWhitelistResponsePacket(
                    slot, Optional.ofNullable(current), collectWhitelist(server)));
        });
    }

    public static void handleSetControllerSlotOwner(final SetControllerSlotOwnerPacket pkt,
                                                     final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            int slot = pkt.slotIndex();
            if (slot < 0 || slot >= ControllerOwners.SLOT_COUNT) return;
            ItemStack ctrl = findController(sp);
            if (ctrl.isEmpty()) return;

            pkt.newOwner().ifPresentOrElse(
                    uuid -> { if (validateOwner(server, uuid)) ControllerOwners.set(ctrl, slot, uuid); },
                    ()   -> ControllerOwners.set(ctrl, slot, null)
            );
        });
    }

    public static void handleClearAllControllerOwners(final ClearAllControllerOwnersPacket pkt,
                                                       final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack ctrl = findController(sp);
            if (ctrl.isEmpty()) return;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) ControllerOwners.set(ctrl, i, null);
        });
    }

    // ── Linked Typewriter ─────────────────────────────────────────────────────

    public static void handleRequestTypewriterWhitelist(final RequestTypewriterWhitelistPacket pkt,
                                                         final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!TypewriterCompat.isAvailable()) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!TypewriterCompat.isTypewriter(be)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            UUID current = TypewriterCompat.getOwner(be);
            PacketDistributor.sendToPlayer(sp, new TypewriterWhitelistResponsePacket(
                    pkt.blockPos(), Optional.ofNullable(current), collectWhitelist(server)));
        });
    }

    public static void handleSetTypewriterOwner(final SetTypewriterOwnerPacket pkt,
                                                 final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!TypewriterCompat.isAvailable()) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!TypewriterCompat.isTypewriter(be)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            pkt.newOwner().ifPresentOrElse(
                    uuid -> { if (validateOwner(server, uuid)) TypewriterCompat.setOwner(be, uuid); },
                    ()   -> TypewriterCompat.setOwner(be, null)
            );
        });
    }

    // ── Copy frequency from Redstone Link ────────────────────────────────────

    /**
     * Copies the player-frequency owner from a Redstone Link block onto either
     * a Link Controller slot or a Linked Typewriter, depending on what the
     * player is holding / what typewriter position was sent.
     *
     * controllerSlot >= 0  → copy to that controller slot
     * typewriterPos != ZERO → copy to that typewriter block
     */
    public static void handleCopyLinkFrequency(final CopyLinkFrequencyPacket pkt,
                                                final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (sp.distanceToSqr(pkt.linkPos().getCenter()) > MAX_REACH_SQR) return;

            BlockEntity linkBe = sp.level().getBlockEntity(pkt.linkPos());
            if (!(linkBe instanceof RedstoneLinkBlockEntity)) return;

            UUID owner = ((IOwnedLink) linkBe).playerlink$getOwner();

            // Copy to controller slot
            if (pkt.controllerSlot() >= 0) {
                ItemStack ctrl = findController(sp);
                if (!ctrl.isEmpty()) {
                    ControllerOwners.set(ctrl, pkt.controllerSlot(), owner);
                    PlayerLinkMod.LOGGER.debug("[PlayerLink] Copied link owner {} to controller slot {}",
                            owner, pkt.controllerSlot());
                }
            }

            // Copy to typewriter
            if (!pkt.typewriterPos().equals(BlockPos.ZERO)) {
                if (TypewriterCompat.isAvailable()) {
                    BlockEntity twBe = sp.level().getBlockEntity(pkt.typewriterPos());
                    if (TypewriterCompat.isTypewriter(twBe)) {
                        if (sp.distanceToSqr(pkt.typewriterPos().getCenter()) <= MAX_REACH_SQR) {
                            TypewriterCompat.setOwner(twBe, owner);
                            PlayerLinkMod.LOGGER.debug("[PlayerLink] Copied link owner {} to typewriter at {}",
                                    owner, pkt.typewriterPos());
                        }
                    }
                }
            }
        });
    }

    public static void handleRequestTypewriterKeyWhitelist(final RequestTypewriterKeyWhitelistPacket pkt,
                                                             final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!TypewriterCompat.isAvailable()) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!TypewriterCompat.isTypewriter(be)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            UUID current = TypewriterCompat.getKeyOwner(be, pkt.glfwKey());
            PacketDistributor.sendToPlayer(sp, new TypewriterKeyWhitelistResponsePacket(
                    pkt.blockPos(), pkt.glfwKey(),
                    Optional.ofNullable(current), collectWhitelist(server)));
        });
    }

    public static void handleSetTypewriterKeyOwner(final SetTypewriterKeyOwnerPacket pkt,
                                                    final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!TypewriterCompat.isAvailable()) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!TypewriterCompat.isTypewriter(be)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            pkt.newOwner().ifPresentOrElse(
                    uuid -> { if (validateOwner(server, uuid))
                                  TypewriterCompat.setKeyOwner(be, pkt.glfwKey(), uuid); },
                    ()   -> TypewriterCompat.setKeyOwner(be, pkt.glfwKey(), null)
            );
        });
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static ItemStack findController(ServerPlayer sp) {
        ItemStack main = sp.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof LinkedControllerItem) return main;
        ItemStack off = sp.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof LinkedControllerItem) return off;
        return ItemStack.EMPTY;
    }

    private static List<WhitelistResponsePacket.Entry> collectWhitelist(MinecraftServer server) {
        List<WhitelistResponsePacket.Entry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        for (String name : wl.getUserList()) {
            Optional<GameProfile> p = server.getProfileCache() == null
                    ? Optional.empty() : server.getProfileCache().get(name);
            if (p.isPresent() && seen.add(p.get().getId()))
                entries.add(new WhitelistResponsePacket.Entry(p.get().getId(), p.get().getName()));
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers())
            if (seen.add(online.getUUID()))
                entries.add(new WhitelistResponsePacket.Entry(
                        online.getUUID(), online.getGameProfile().getName()));
        return entries;
    }

    private static boolean validateOwner(MinecraftServer server, UUID candidate) {
        UserWhiteList wl = server.getPlayerList().getWhiteList();
        for (String name : wl.getUserList()) {
            Optional<GameProfile> p = server.getProfileCache() == null
                    ? Optional.empty() : server.getProfileCache().get(name);
            if (p.isPresent() && p.get().getId().equals(candidate)) return true;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers())
            if (online.getUUID().equals(candidate)) return true;
        return false;
    }
}
