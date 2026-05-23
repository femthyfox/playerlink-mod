Always returns at minimum yourself
Sends server-side chat messages telling you exactly what's happening (no log file digging needed!)
Catches every possible error so we can see it instead of silent failure
📄 Single file replacement — src/main/java/com/playerlink/server/ServerPacketHandlers.java
On GitHub, open that file, ✏️ → Ctrl+A → Delete → paste only the code below (no headings, no emoji, nothing else):

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
            ServerPlayer sp = (ctx.player() instanceof ServerPlayer s) ? s : null;
            if (sp == null) {
                PlayerLinkMod.LOGGER.warn("[PlayerLink-Server] no ServerPlayer in context");
                return;
            }
            sp.sendSystemMessage(Component.literal("[PlayerLink-Server] Got request"));

            try {
                MinecraftServer server = sp.getServer();
                if (server == null) {
                    sp.sendSystemMessage(Component.literal("[PlayerLink-Server] server is null"));
                    return;
                }

                Map<UUID, String> candidates = new LinkedHashMap<>();
                candidates.put(sp.getUUID(), sp.getGameProfile().getName());

                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    candidates.putIfAbsent(p.getUUID(), p.getGameProfile().getName());
                }

                try {
                    UserWhiteList wl = server.getPlayerList().getWhiteList();
                    for (String name : wl.getUserList()) {
                        Optional<GameProfile> gp = server.getProfileCache() == null
                                ? Optional.empty()
                                : server.getProfileCache().get(name);
                        gp.ifPresent(g -> candidates.putIfAbsent(g.getId(), g.getName()));
                    }
                } catch (Throwable t) {
                    PlayerLinkMod.LOGGER.warn("[PlayerLink-Server] whitelist read failed: {}", t.getMessage());
                }

                UUID currentOwner = null;
                BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
                if (be != null) {
                    try {
                        if (be instanceof IOwnedLink iol) {
                            currentOwner = iol.playerlink$getOwner();
                        } else {
                            sp.sendSystemMessage(Component.literal("[PlayerLink-Server] WARN: BE doesn't implement IOwnedLink (mixin not applied?). Class: " + be.getClass().getName()));
                        }
                    } catch (Throwable t) {
                        sp.sendSystemMessage(Component.literal("[PlayerLink-Server] WARN owner read failed: " + t.getMessage()));
                    }
                } else {
                    sp.sendSystemMessage(Component.literal("[PlayerLink-Server] WARN: no BlockEntity at " + pkt.blockPos()));
                }

                List<WhitelistResponsePacket.Entry> entries = new ArrayList<>();
                candidates.forEach((id, name) -> entries.add(new WhitelistResponsePacket.Entry(id, name)));

                sp.sendSystemMessage(Component.literal("[PlayerLink-Server] Sending " + entries.size() + " candidate(s)"));
                PlayerLinkMod.LOGGER.info("[PlayerLink-Server] Sending {} candidates to {}", entries.size(), sp.getName().getString());

                PacketDistributor.sendToPlayer(sp, new WhitelistResponsePacket(
                        pkt.blockPos(),
                        Optional.ofNullable(currentOwner),
                        entries
                ));
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink-Server] handleRequestWhitelist crashed", t);
                sp.sendSystemMessage(Component.literal("[PlayerLink-Server] ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        });
    }

    public static void handleSetOwner(final SetOwnerPacket pkt, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sp = (ctx.player() instanceof ServerPlayer s) ? s : null;
            if (sp == null) return;
            try {
                BlockEntity be = sp.level().getBlockEntity(pkt.blockPos());
                if (be == null) {
                    sp.sendSystemMessage(Component.literal("[PlayerLink] No block entity at that position"));
                    return;
                }

                if (!(be instanceof IOwnedLink iol)) {
                    sp.sendSystemMessage(Component.literal("[PlayerLink] ERROR: BE doesn't implement IOwnedLink (mixin failed). Class: " + be.getClass().getName()));
                    return;
                }

                if (pkt.newOwner().isPresent()) {
                    UUID newId = pkt.newOwner().get();
                    String newName = newId.equals(sp.getUUID())
                            ? sp.getGameProfile().getName()
                            : (sp.getServer() != null && sp.getServer().getProfileCache() != null
                                ? sp.getServer().getProfileCache().get(newId).map(GameProfile::getName).orElse(newId.toString().substring(0, 8))
                                : newId.toString().substring(0, 8));

                    iol.playerlink$setOwner(newId);
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Owner set to: " + newName));
                    PlayerLinkMod.LOGGER.info("[PlayerLink-Server] {} set owner of link@{} to {} ({})",
                            sp.getName().getString(), pkt.blockPos(), newName, newId);
                } else {
                    iol.playerlink$setOwner(null);
                    sp.sendSystemMessage(Component.literal("[PlayerLink] Owner cleared"));
                    PlayerLinkMod.LOGGER.info("[PlayerLink-Server] {} cleared owner of link@{}",
                            sp.getName().getString(), pkt.blockPos());
                }
            } catch (Throwable t) {
                PlayerLinkMod.LOGGER.error("[PlayerLink-Server] handleSetOwner crashed", t);
                sp.sendSystemMessage(Component.literal("[PlayerLink-Server] ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        });
    }
}