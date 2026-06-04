package com.playerlink.compat;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IFrequencyOwner;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All Simulated (Create Aeronautics) interaction goes through here so that
 * PlayerLink compiles with no dependency on the Simulated JAR.
 *
 * Per-key player-frequency owners are stored in the block entity's NBT under
 * "playerlink_key_owners" as a CompoundTag mapping GLFW-key-string → UUID.
 *
 * When a key is activated, we read the owner for that GLFW key and tag the
 * two Frequency objects on the KeyboardEntry before they reach the network.
 */
public final class TypewriterCompat {

    private static final String BE_CLASS =
            "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity";
    private static final String ENTRY_CLASS =
            "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries";
    private static final String OWNERS_NBT_KEY = "playerlink_key_owners";

    private static boolean initialised = false;
    private static boolean available   = false;

    private static Class<?> beClass;
    private static Method   getTypewriterEntries; // LinkedTypewriterBlockEntity.getTypewriterEntries()
    private static Method   getKeyMap;            // LinkedTypewriterEntries.getKeyMap()
    private static Method   getNetworkKey;        // KeyboardEntry.getNetworkKey()
    private static Field    glfwKeyCode;          // KeyboardEntry.glfwKeyCode

    private TypewriterCompat() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isAvailable() { init(); return available; }

    public static boolean isTypewriter(BlockEntity be) {
        return isAvailable() && beClass.isInstance(be);
    }

    // ── Per-block owner (legacy — kept for GUI packet compat) ─────────────────

    @Nullable
    public static UUID getBlockOwner(BlockEntity be) {
        if (!isTypewriter(be)) return null;
        try {
            CompoundTag tag = be.saveWithoutMetadata(
                    be.getLevel() == null ? null : be.getLevel().registryAccess());
            if (tag.contains(OWNERS_NBT_KEY)) {
                CompoundTag owners = tag.getCompound(OWNERS_NBT_KEY);
                if (owners.hasUUID("__block__")) return owners.getUUID("__block__");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ── Per-key owner storage ─────────────────────────────────────────────────

    /**
     * Returns all per-key owners stored on this block entity.
     * Key: GLFW key code (int), Value: owner UUID (nullable = no owner).
     */
    @SuppressWarnings("unchecked")
    public static Map<Integer, UUID> getKeyOwners(BlockEntity be) {
        Map<Integer, UUID> result = new HashMap<>();
        if (!isTypewriter(be)) return result;
        try {
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f != null) {
                Map<Integer, UUID> stored = (Map<Integer, UUID>) f.get(be);
                if (stored != null) result.putAll(stored);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    /**
     * Sets the player-frequency owner for a specific GLFW key on a typewriter.
     * Persists to NBT via setChanged().
     */
    public static void setKeyOwner(BlockEntity be, int glfwKey, @Nullable UUID owner) {
        if (!isTypewriter(be)) return;
        try {
            // We store owners in a transient field injected by our mixin.
            // Fall back to direct NBT mutation if the field isn't available.
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f != null) {
                @SuppressWarnings("unchecked")
                Map<Integer, UUID> map = (Map<Integer, UUID>) f.get(be);
                if (map == null) { map = new HashMap<>(); f.set(be, map); }
                if (owner == null) map.remove(glfwKey);
                else map.put(glfwKey, owner);
            }
            be.setChanged();
            // Re-tag existing entries for this key
            tagKeyEntry(be, glfwKey, owner);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.setKeyOwner failed", t);
        }
    }

    @Nullable
    public static UUID getKeyOwner(BlockEntity be, int glfwKey) {
        if (!isTypewriter(be)) return null;
        try {
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f != null) {
                @SuppressWarnings("unchecked")
                Map<Integer, UUID> map = (Map<Integer, UUID>) f.get(be);
                return map == null ? null : map.get(glfwKey);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Called from our mixin just before a key is activated.
     * Tags the KeyboardEntry's Frequency objects with the stored owner for that key.
     */
    public static void tagBeforeActivate(BlockEntity be, int glfwKey) {
        if (!isTypewriter(be)) return;
        UUID owner = getKeyOwner(be, glfwKey);
        tagKeyEntry(be, glfwKey, owner);
    }

    /**
     * When a typewriter key is bound to a Redstone Link (via the Typewriter's own
     * bind mechanic), copy the link's player-frequency owner to that key's owner slot.
     */
    public static void copyOwnerFromLink(BlockEntity typewriterBe,
                                          int glfwKey,
                                          @Nullable UUID linkOwner) {
        setKeyOwner(typewriterBe, glfwKey, linkOwner);
    }

    // ── Persist to / from NBT ─────────────────────────────────────────────────

    public static void writeOwners(BlockEntity be, CompoundTag tag) {
        if (!isTypewriter(be)) return;
        try {
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f == null) return;
            @SuppressWarnings("unchecked")
            Map<Integer, UUID> map = (Map<Integer, UUID>) f.get(be);
            if (map == null || map.isEmpty()) { tag.remove(OWNERS_NBT_KEY); return; }
            CompoundTag owners = new CompoundTag();
            map.forEach((key, uuid) -> {
                if (uuid != null) owners.putUUID(String.valueOf(key), uuid);
            });
            tag.put(OWNERS_NBT_KEY, owners);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.writeOwners failed", t);
        }
    }

    public static void readOwners(BlockEntity be, CompoundTag tag) {
        if (!isTypewriter(be)) return;
        try {
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f == null) return;
            Map<Integer, UUID> map = new HashMap<>();
            if (tag.contains(OWNERS_NBT_KEY, 10)) {
                CompoundTag owners = tag.getCompound(OWNERS_NBT_KEY);
                for (String key : owners.getAllKeys()) {
                    try {
                        int glfwKey = Integer.parseInt(key);
                        UUID uuid = owners.getUUID(key);
                        map.put(glfwKey, uuid);
                    } catch (Throwable ignored) {}
                }
            }
            f.set(be, map);
            // Re-tag all loaded entries
            map.forEach((key, uuid) -> tagKeyEntry(be, key, uuid));
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.readOwners failed", t);
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private static void init() {
        if (initialised) return;
        initialised = true;
        try {
            beClass = Class.forName(BE_CLASS);
            Class<?> entriesClass = Class.forName(ENTRY_CLASS);
            getTypewriterEntries = beClass.getMethod("getTypewriterEntries");
            getKeyMap = entriesClass.getMethod("getKeyMap");
            Class<?> entryClass = Class.forName(ENTRY_CLASS + "$KeyboardEntry");
            getNetworkKey = entryClass.getMethod("getNetworkKey");
            glfwKeyCode = entryClass.getDeclaredField("glfwKeyCode");
            glfwKeyCode.setAccessible(true);
            available = true;
            PlayerLinkMod.LOGGER.info("[PlayerLink] Simulated detected — Linked Typewriter support enabled.");
        } catch (Throwable e) {
            available = false;
            PlayerLinkMod.LOGGER.info("[PlayerLink] Simulated not found — Linked Typewriter support disabled.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void tagKeyEntry(BlockEntity be, int glfwKey, @Nullable UUID owner) {
        try {
            Object entries = getTypewriterEntries.invoke(be);
            @SuppressWarnings("unchecked")
            Map<Integer, Object> keyMap = (Map<Integer, Object>) getKeyMap.invoke(entries);
            Object entry = keyMap.get(glfwKey);
            if (entry == null) return;
            @SuppressWarnings("unchecked")
            Couple<Frequency> key = (Couple<Frequency>) getNetworkKey.invoke(entry);
            if (key == null) return;
            if (key.getFirst() instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
            if (key.getSecond() instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
        } catch (Throwable ignored) {}
    }

    // Simple field cache
    private static final Map<String, Field> fieldCache = new HashMap<>();

    @Nullable
    private static Field getOrFindField(Class<?> cls, String name) {
        return fieldCache.computeIfAbsent(name, n -> {
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) { c = c.getSuperclass(); }
            }
            return null;
        });
    }

    // ── Aliases for backward compat with ServerPacketHandlers ────────────────

    @Nullable
    public static UUID getOwner(BlockEntity be) {
        return getBlockOwner(be);
    }

    public static void setOwner(BlockEntity be, @Nullable UUID owner) {
        if (!isTypewriter(be)) return;
        try {
            Field f = getOrFindField(be.getClass(), OWNERS_NBT_KEY + "_map");
            if (f != null) {
                @SuppressWarnings("unchecked")
                Map<Integer, UUID> map = (Map<Integer, UUID>) f.get(be);
                if (map == null) { map = new java.util.HashMap<>(); f.set(be, map); }
                if (owner == null) map.remove(Integer.MIN_VALUE);
                else map.put(Integer.MIN_VALUE, owner);
            }
            be.setChanged();
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.setOwner failed", t);
        }
    }
}
