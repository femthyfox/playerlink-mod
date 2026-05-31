package com.playerlink.util;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thread-local stash for the "currently-transmitting controller slot owner".
 * Lives in com.playerlink.util (NOT com.playerlink.mixin) because the mixin
 * package may only contain mixin classes — putting helpers there triggers
 * IllegalClassLoadError at runtime.
 */
public final class ControllerOwnerContext {

    private static final ThreadLocal<UUID> CURRENT_OWNER = new ThreadLocal<>();

    private ControllerOwnerContext() {}

    public static void set(@Nullable UUID owner) {
        if (owner == null) CURRENT_OWNER.remove();
        else CURRENT_OWNER.set(owner);
    }

    @Nullable
    public static UUID get() {
        return CURRENT_OWNER.get();
    }

    public static void clear() {
        CURRENT_OWNER.remove();
    }
}