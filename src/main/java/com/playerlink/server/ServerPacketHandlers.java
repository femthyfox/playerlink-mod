package com.playerlink.server;

import com.mojang.authlib.GameProfile;
import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.network.RequestWhitelistPacket;
import com.playerlink.network.SetOwnerPacket;
import com.playerlink.network.WhitelistResponsePacket;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ServerPacketHandlers {

    private ServerPacketHandlers() {}

    public static void handleRequestWhitelist(final RequestWhitelistPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (!(ctx.player() instanceof ServerPlayer sp)) {
                    PlayerLinkMod.LOGGER.warn("[PlayerLink] handleRequestWhitelist: not a ServerPlayer");
                    return;
                }
                MinecraftServer server = sp.getServer();
                if (server == null) {
                    PlayerLinkMod.LOGGER.warn("[PlayerLink] handleRequestWhitelist: server is null");
                    return;
                }

                BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
                if (!(be instanceof RedstoneLinkBlockEntity)) {
                    PlayerLinkMod.LOGGER.warn("[PlayerLink] BE@{} is {}, not RedstoneLinkBlockEntity",
                            pkt.blockPos(),
                            be == null ? "null" : be.getClass().getName());
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Not a Redstone Link."));
                    return;
                }

                UUID currentOwner = ((IOwnedLink) be).playerlink$getOwner();

                // Build candidate list from multiple sources so SP / unwhitelisted servers also work.
                // Using LinkedHashMap to dedupe by UUID while preserving insertion order.
                Map<UUID, String> candidates = new LinkedHashMap<>();

                // 1. The requesting player themselves (always available)
                candidates.put(sp.getUUID(), sp.getGameProfile().getName());

                // 2. All currently online players
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    candidates.putIfAbsent(p.getUUID(), p.getGameProfile().getName());
                }

                // 3. Vanilla whitelist (may be empty in SP / non-whitelisted servers)
                UserWhiteList wl = server.getPlayerList().getWhiteList();
                for (String name : wl.getUserList()) {
                    Optional<GameProfile> p = server.getProfileCache() == null
                            ? Optional.empty()
                            : server.getProfileCache().get(name);
                    p.ifPresent(gp -> candidates.putIfAbsent(gp.getId(), gp.getName()));
                }

                List<WhitelistResponsePacket.Entry> entries = new ArrayList<>(candidates.size());
                candidates.forEach((id, name) -> entries.add(new WhitelistResponsePacket.Entry(id, name)));

                PlayerLinkMod.LOGGER.info("[PlayerLink] Sending whitelist response: {} candidates, currentOwner={}",
                        entries.size(), currentOwner);

                PacketDistributor.sendToPlayer(sp, new WhitelistResponsePacket(
                        pkt.blockPos(),
                        Optional.ofNullable(currentOwner),
                        entries
                ));
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] handleRequestWhitelist crashed", t);
            }
        });
    }

    public static void handleSetOwner(final SetOwnerPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                MinecraftServer server = sp.getServer();
                if (server == null) return;

                BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
                if (!(be instanceof RedstoneLinkBlockEntity link)) {
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Not a Redstone Link."));
                    return;
                }

                if (pkt.newOwner().isPresent()) {
                    UUID candidate = pkt.newOwner().get();

                    // Resolve a display name for the chat feedback (best effort)
                    String matchedName = null;
                    if (sp.getUUID().equals(candidate)) {
                        matchedName = sp.getGameProfile().getName();
                    } else {
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                            if (p.getUUID().equals(candidate)) {
                                matchedName = p.getGameProfile().getName();
                                break;
                            }
                        }
                        if (matchedName == null && server.getProfileCache() != null) {
                            matchedName = server.getProfileCache().get(candidate)
                                    .map(GameProfile::getName).orElse(candidate.toString().substring(0, 8));
                        }
                    }

                    ((IOwnedLink) link).playerlink$setOwner(candidate);
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Owner set to: " + matchedName));
                    PlayerLinkMod.LOGGER.info("[PlayerLink] {} set owner of link@{} to {} ({})",
                            sp.getName().getString(), pkt.blockPos(), matchedName, candidate);
                } else {
                    ((IOwnedLink) link).playerlink$setOwner(null);
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Owner cleared."));
                    PlayerLinkMod.LOGGER.info("[PlayerLink] {} cleared owner of link@{}",
                            sp.getName().getString(), pkt.blockPos());
                }
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink] handleSetOwner crashed", t);
            }
        });
    }
}