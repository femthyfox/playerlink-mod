package com.playerlink.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SkinCache {

    private static final ConcurrentHashMap<UUID, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicBoolean> PENDING = new ConcurrentHashMap<>();

    private SkinCache() {}

    public static ResourceLocation get(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();

        // 1. Local player? Use their live skin directly (always loaded).
        LocalPlayer local = mc.player;
        if (local != null && local.getUUID().equals(uuid)) {
            return local.getSkin().texture();
        }

        // 2. Online player on the current connection? Use their PlayerInfo skin.
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                return info.getSkin().texture();
            }
        }

        // 3. Cached from an earlier async Mojang fetch?
        ResourceLocation cached = CACHE.get(uuid);
        if (cached != null) return cached;

        // 4. Kick off an async Mojang fetch in the background.
        AtomicBoolean inFlight = PENDING.computeIfAbsent(uuid, u -> new AtomicBoolean(false));
        if (inFlight.compareAndSet(false, true)) {
            final String displayName = name == null ? "Player" : name;
            CompletableFuture.runAsync(() -> {
                try {
                    GameProfile profile = new GameProfile(uuid, displayName);

                    // Try to fill profile properties (textures) from Mojang session service.
                    try {
                        var result = mc.getMinecraftSessionService().fetchProfile(uuid, true);
                        if (result != null && result.profile() != null) {
                            profile = result.profile();
                        }
                    } catch (Throwable ignored) {}

                    final GameProfile finalProfile = profile;
                    mc.getSkinManager().getOrLoad(finalProfile)
                            .thenAccept(skin -> CACHE.put(uuid, skin.texture()))
                            .exceptionally(t -> { PENDING.remove(uuid); return null; });
                } catch (Throwable t) {
                    PENDING.remove(uuid);
                }
            });
        }

        // 5. Show default Steve/Alex while we wait for the async fetch.
        return DefaultPlayerSkin.get(uuid).texture();
    }
}