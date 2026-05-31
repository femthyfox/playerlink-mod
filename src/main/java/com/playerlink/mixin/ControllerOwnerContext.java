package com.playerlink.mixin;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thread-local stash for the "currently-transmitting controller slot owner".
 * Set by LinkedControllerServerHandlerMixin at HEAD of receivePressed,
 * read by FrequencyOfMixin during Frequency.of, cleared at RETURN.
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