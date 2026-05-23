package com.playerlink.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SkinCache {

    private static final ConcurrentHashMap<UUID, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicBoolean> PENDING = new ConcurrentHashMap<>();

    private SkinCache() {}

    public static ResourceLocation get(UUID uuid, String name) {
        if (uuid == null) return DefaultPlayerSkin.get(new UUID(0L, 0L)).texture();

        ResourceLocation cached = CACHE.get(uuid);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();

        LocalPlayer self = mc.player;
        if (self != null && uuid.equals(self.getUUID())) {
            ResourceLocation tex = self.getSkin().texture();
            CACHE.put(uuid, tex);
            return tex;
        }

        ClientLevel lvl = mc.level;
        if (lvl != null) {
            Player p = lvl.getPlayerByUUID(uuid);
            if (p instanceof AbstractClientPlayer acp) {
                ResourceLocation tex = acp.getSkin().texture();
                CACHE.put(uuid, tex);
                return tex;
            }
        }

        AtomicBoolean inFlight = PENDING.computeIfAbsent(uuid, u -> new AtomicBoolean(false));
        if (inFlight.compareAndSet(false, true)) {
            GameProfile profile = new GameProfile(uuid, name == null ? "" : name);
            mc.getSkinManager()
              .getOrLoad(profile)
              .thenAccept(skin -> {
                  CACHE.put(uuid, skin.texture());
                  PENDING.remove(uuid);
              })
              .exceptionally(t -> {
                  PENDING.remove(uuid);
                  return null;
              });
        }

        return DefaultPlayerSkin.get(uuid).texture();
    }
}
