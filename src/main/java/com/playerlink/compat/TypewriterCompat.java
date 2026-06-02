package com.playerlink.compat;

import com.playerlink.PlayerLinkMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * All interaction with the Linked Typewriter block entity goes through
 * this class so that PlayerLink compiles with no dependency on the
 * Simulated JAR. Every call uses reflection guarded by isAvailable().
 *
 * At runtime, if Simulated is installed the class is found and all
 * methods resolve. If Simulated is absent every method is a no-op.
 */
public final class TypewriterCompat {

    private static final String BE_CLASS = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity";
    private static final String OWNER_KEY = "playerlink_owner";

    private static boolean initialised = false;
    private static boolean available   = false;

    // Reflected handles resolved once on first use
    private static Class<?> beClass;
    private static Method   writeNbt;   // saveAdditional(CompoundTag, HolderLookup.Provider) or write()
    private static Method   setChanged;

    private TypewriterCompat() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static boolean isTypewriter(BlockEntity be) {
        if (!isAvailable()) return false;
        return beClass.isInstance(be);
    }

    @Nullable
    public static UUID getOwner(BlockEntity be) {
        if (!isTypewriter(be)) return null;
        try {
            Field f = getOrCacheField(be.getClass(), OWNER_KEY);
            if (f != null) return (UUID) f.get(be);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.getOwner failed", t);
        }
        return null;
    }

    public static void setOwner(BlockEntity be, @Nullable UUID owner) {
        if (!isTypewriter(be)) return;
        try {
            Field f = getOrCacheField(be.getClass(), "playerlink$typewriterOwner");
            if (f == null) f = getOrCacheField(be.getClass(), OWNER_KEY);
            if (f != null) {
                f.set(be, owner);
                be.setChanged();
                // Also call playerlink$setTypewriterOwner if accessible
                try {
                    Method m = be.getClass().getMethod("playerlink$setTypewriterOwner", UUID.class);
                    m.invoke(be, owner);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] TypewriterCompat.setOwner failed", t);
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    private static void init() {
        if (initialised) return;
        initialised = true;
        try {
            beClass   = Class.forName(BE_CLASS);
            available = true;
            PlayerLinkMod.LOGGER.info("[PlayerLink] Simulated detected — Linked Typewriter support enabled.");
        } catch (ClassNotFoundException e) {
            available = false;
            PlayerLinkMod.LOGGER.info("[PlayerLink] Simulated not found — Linked Typewriter support disabled.");
        }
    }

    // Simple field cache (single-entry, good enough for one class)
    private static Field cachedField;
    private static String cachedFieldName;

    @Nullable
    private static Field getOrCacheField(Class<?> cls, String name) {
        if (name.equals(cachedFieldName) && cachedField != null) return cachedField;
        try {
            Field f = findField(cls, name);
            if (f != null) {
                f.setAccessible(true);
                cachedField = f;
                cachedFieldName = name;
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }
}
