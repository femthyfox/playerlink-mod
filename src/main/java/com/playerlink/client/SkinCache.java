package com.playerlink.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SkinCache {

    private static final ConcurrentHashMap<UUID, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicBoolean> PENDING = new ConcurrentHashMap<>();

    private SkinCache() {}

    public static ResourceLocation get(UUID uuid, String name) {
        ResourceLocation cached = CACHE.get(uuid);
        if (cached != null) return cached;

        AtomicBoolean inFlight = PENDING.computeIfAbsent(uuid, u -> new AtomicBoolean(false));
        if (inFlight.compareAndSet(false, true)) {
            GameProfile profile = new GameProfile(uuid, name == null ? "" : name);
            Minecraft.getInstance().getSkinManager()
                    .getOrLoad(profile)
                    .thenAccept(skin -> CACHE.put(uuid, skin.texture()))
                    .exceptionally(t -> { PENDING.remove(uuid); return null; });
        }
        return DefaultPlayerSkin.get(uuid).texture();
    }
}
