package com.playerlink.server;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.network.SetOwnerPacket;
import com.playerlink.network.WhitelistResponsePacket;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ServerPacketHandlers {

    private ServerPacketHandlers() {}

    public static void handleRequestWhitelist(final RequestWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MinecraftServer server = sp.getServer();
            if (server == null) return;

            BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
            if (!(be instanceof RedstoneLinkBlockEntity)) return;
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > 64.0) return;

            UUID currentOwner = ((IOwnedLink) be).playerlink$getOwner();

            UserWhiteList whitelist = server.getPlayerList().getWhiteList();
            List<WhitelistResponsePacket.Entry> entries = new ArrayList<>();

            for (String name : whitelist.getUserList()) {
                Optional<GameProfile> profileOpt = server.getProfileCache() == null
                        ? Optional.empty()
                        : server.getProfileCache().get(name);
                profileOpt.ifPresent(p -> entries.add(
                        new WhitelistResponsePacket.Entry(p.getId(), p.getName())
                ));
            }

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
            if (sp.distanceToSqr(pkt.blockPos().getCenter()) > 64.0) return;

            Optional<UUID> newOwner = pkt.newOwner();
            if (newOwner.isPresent()) {
                UUID candidate = newOwner.get();
                UserWhiteList whitelist = server.getPlayerList().getWhiteList();
                boolean ok = false;
                for (String name : whitelist.getUserList()) {
                    Optional<GameProfile> p = server.getProfileCache() == null
                            ? Optional.empty()
                            : server.getProfileCache().get(name);
                    if (p.isPresent() && p.get().getId().equals(candidate)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) return;
                ((IOwnedLink) link).playerlink$setOwner(candidate);
            } else {
                ((IOwnedLink) link).playerlink$setOwner(null);
            }

            PlayerLinkMod.LOGGER.debug("[PlayerLink] {} set owner of link@{} to {}",
                    sp.getName().getString(), pkt.blockPos(), newOwner.orElse(null));
        });
    }
}