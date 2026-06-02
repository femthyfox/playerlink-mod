package com.playerlink.server;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.compat.TypewriterCompat;
import com.playerlink.network.*;
import com.playerlink.util.ControllerOwners;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
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

    public static void handleRequestWhitelist(final RequestWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            UUID currentOwner = ((IOwnedLink) be).playerlink$getOwner();
            List<WhitelistResponsePacket.Entry> entries = collectWhitelist(server);
            PacketDistributor.sendToPlayer(sp, new WhitelistResponsePacket(
                    pkt.blockPos(), Optional.ofNullable(currentOwner), entries));
        });
    }

    public static void handleSetOwner(final SetOwnerPacket pkt, final IPayloadContext ctx) {
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

    public static void handleRequestControllerWhitelist(final RequestControllerWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            int slot = pkt.slotIndex();
            if (slot < 0 || slot >= ControllerOwners.SLOT_COUNT) return;

            ItemStack controller = findController(sp);
            if (controller.isEmpty()) return;

            UUID currentOwner = ControllerOwners.get(controller, slot);
            List<WhitelistResponsePacket.Entry> entries = collectWhitelist(server);
            PacketDistributor.sendToPlayer(sp, new ControllerWhitelistResponsePacket(
                    slot, Optional.ofNullable(currentOwner), entries));
        });
    }

    public static void handleSetControllerSlotOwner(final SetControllerSlotOwnerPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            int slot = pkt.slotIndex();
            if (slot < 0 || slot >= ControllerOwners.SLOT_COUNT) return;

            ItemStack controller = findController(sp);
            if (controller.isEmpty()) return;

            pkt.newOwner().ifPresentOrElse(
                    uuid -> { if (validateOwner(server, uuid)) ControllerOwners.set(controller, slot, uuid); },
                    ()   -> ControllerOwners.set(controller, slot, null)
            );
        });
    }

    public static void handleClearAllControllerOwners(final ClearAllControllerOwnersPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack controller = findController(sp);
            if (controller.isEmpty()) return;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) ControllerOwners.set(controller, i, null);
        });
    }

    // ── Linked Typewriter ─────────────────────────────────────────────────────

    public static void handleRequestTypewriterWhitelist(final RequestTypewriterWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!TypewriterCompat.isAvailable()) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!TypewriterCompat.isTypewriter(be)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            UUID currentOwner = TypewriterCompat.getOwner(be);
            List<WhitelistResponsePacket.Entry> entries = collectWhitelist(server);
            PacketDistributor.sendToPlayer(sp, new TypewriterWhitelistResponsePacket(
                    pkt.blockPos(), Optional.ofNullable(currentOwner), entries));
        });
    }

    public static void handleSetTypewriterOwner(final SetTypewriterOwnerPacket pkt, final IPayloadContext ctx) {
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
        UserWhiteList whitelist = server.getPlayerList().getWhiteList();
        for (String name : whitelist.getUserList()) {
            Optional<GameProfile> p = server.getProfileCache() == null
                    ? Optional.empty() : server.getProfileCache().get(name);
            if (p.isPresent() && seen.add(p.get().getId()))
                entries.add(new WhitelistResponsePacket.Entry(p.get().getId(), p.get().getName()));
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (seen.add(online.getUUID()))
                entries.add(new WhitelistResponsePacket.Entry(online.getUUID(), online.getGameProfile().getName()));
        }
        return entries;
    }

    private static boolean validateOwner(MinecraftServer server, UUID candidate) {
        UserWhiteList whitelist = server.getPlayerList().getWhiteList();
        for (String name : whitelist.getUserList()) {
            Optional<GameProfile> p = server.getProfileCache() == null
                    ? Optional.empty() : server.getProfileCache().get(name);
            if (p.isPresent() && p.get().getId().equals(candidate)) return true;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers())
            if (online.getUUID().equals(candidate)) return true;
        return false;
    }
}
