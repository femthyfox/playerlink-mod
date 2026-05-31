package com.playerlink.server;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.network.ClearAllControllerOwnersPacket;
import com.playerlink.network.ControllerWhitelistResponsePacket;
import com.playerlink.network.RequestControllerWhitelistPacket;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.network.SetControllerSlotOwnerPacket;
import com.playerlink.network.SetOwnerPacket;
import com.playerlink.network.WhitelistResponsePacket;
import com.playerlink.util.ControllerOwners;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ServerPacketHandlers {

    private static final double MAX_REACH_SQR = 1296.0;

    private ServerPacketHandlers() {}

    public static void handleRequestWhitelist(final RequestWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            PlayerLinkMod.LOGGER.info("[PlayerLink] Got request from {} for link@{}",
                    sp.getName().getString(), pkt.blockPos());

            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity)) {
                PlayerLinkMod.LOGGER.warn("[PlayerLink] {} requested non-link block at {}",
                        sp.getName().getString(), pkt.blockPos());
                return;
            }
            double distSqr = sp.distanceToSqr(pkt.blockPos().getCenter());
            if (distSqr > MAX_REACH_SQR) {
                PlayerLinkMod.LOGGER.warn("[PlayerLink] {} too far from link@{} (distSqr={})",
                        sp.getName().getString(), pkt.blockPos(), distSqr);
                return;
            }

            UUID currentOwner = ((IOwnedLink) be).playerlink$getOwner();
            List<WhitelistResponsePacket.Entry> entries = collectWhitelist(server);

            PlayerLinkMod.LOGGER.info("[PlayerLink] Sending {} entries to {}", entries.size(), sp.getName().getString());

            PacketDistributor.sendToPlayer(sp, new WhitelistResponsePacket(
                    pkt.blockPos(),
                    Optional.ofNullable(currentOwner),
                    entries
            ));
        });
    }

    public static void handleSetOwner(final SetOwnerPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            Level level = sp.level();
            BlockEntity be = level.getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity link)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > MAX_REACH_SQR) return;

            Optional<UUID> newOwner = pkt.newOwner();
            if (newOwner.isPresent()) {
                if (!validateOwner(server, newOwner.get())) return;
                ((IOwnedLink) link).playerlink$setOwner(newOwner.get());
            } else {
                ((IOwnedLink) link).playerlink$setOwner(null);
            }

            PlayerLinkMod.LOGGER.info("[PlayerLink] {} set owner of link@{} to {}",
                    sp.getName().getString(), pkt.blockPos(), newOwner.orElse(null));
        });
    }

    public static void handleRequestControllerWhitelist(final RequestControllerWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            int slot = pkt.slotIndex();
            if (slot < 0 || slot >= ControllerOwners.SLOT_COUNT) return;

            ItemStack controller = findController(sp);
            if (controller.isEmpty()) {
                PlayerLinkMod.LOGGER.warn("[PlayerLink] {} requested controller whitelist but is not holding one", sp.getName().getString());
                return;
            }

            UUID currentOwner = ControllerOwners.get(controller, slot);
            List<WhitelistResponsePacket.Entry> entries = collectWhitelist(server);

            PacketDistributor.sendToPlayer(sp, new ControllerWhitelistResponsePacket(
                    slot,
                    Optional.ofNullable(currentOwner),
                    entries
            ));
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

            Optional<UUID> newOwner = pkt.newOwner();
            if (newOwner.isPresent()) {
                if (!validateOwner(server, newOwner.get())) return;
                ControllerOwners.set(controller, slot, newOwner.get());
            } else {
                ControllerOwners.set(controller, slot, null);
            }

            // Re-open the controller GUI so the player isn't kicked out to world.
            playerlink$reopenController(sp, controller);
        });
    }

    /**
     * Re-open Create's LinkedController GUI for the player by calling the
     * item's use() method (which is what right-clicking does). Try mainhand
     * first, fallback to offhand. Wrapped in try/catch so a Create API change
     * can't crash the server here.
     */
    private static void playerlink$reopenController(ServerPlayer sp, ItemStack controller) {
        try {
            net.minecraft.world.InteractionHand hand =
                sp.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND) == controller
                    ? net.minecraft.world.InteractionHand.MAIN_HAND
                    : net.minecraft.world.InteractionHand.OFF_HAND;
            controller.getItem().use(sp.level(), sp, hand);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] reopenController failed", t);
        }
    }

    public static void handleClearAllControllerOwners(final ClearAllControllerOwnersPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack controller = findController(sp);
            if (controller.isEmpty()) return;
            for (int i = 0; i < ControllerOwners.SLOT_COUNT; i++) {
                ControllerOwners.set(controller, i, null);
            }
            PlayerLinkMod.LOGGER.info("[PlayerLink] {} cleared all controller slot owners",
                    sp.getName().getString());
        });
    }

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
            Optional<GameProfile> profileOpt = server.getProfileCache() == null
                    ? Optional.empty()
                    : server.getProfileCache().get(name);
            if (profileOpt.isPresent() && seen.add(profileOpt.get().getId())) {
                entries.add(new WhitelistResponsePacket.Entry(profileOpt.get().getId(), profileOpt.get().getName()));
            }
        }

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (seen.add(online.getUUID())) {
                entries.add(new WhitelistResponsePacket.Entry(online.getUUID(), online.getGameProfile().getName()));
            }
        }
        return entries;
    }

    private static boolean validateOwner(MinecraftServer server, UUID candidate) {
        UserWhiteList whitelist = server.getPlayerList().getWhiteList();
        for (String name : whitelist.getUserList()) {
            Optional<GameProfile> p = server.getProfileCache() == null
                    ? Optional.empty()
                    : server.getProfileCache().get(name);
            if (p.isPresent() && p.get().getId().equals(candidate)) return true;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getUUID().equals(candidate)) return true;
        }
        return false;
    }
}
