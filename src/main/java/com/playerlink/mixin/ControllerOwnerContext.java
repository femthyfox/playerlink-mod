package com.playerlink.mixin;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Thread-local stash for the "currently-transmitting controller slot owner".
 *
 * Flow:
 *   LinkedControllerServerHandlerMixin.receivePressed(HEAD)  → set()
 *   ↓ original receivePressed body runs, eventually creates Frequency objects
 *   FrequencyOfMixin.of(stack)                                → reads
 *   ↓ tag and return the Frequency
 *   LinkedControllerServerHandlerMixin.receivePressed(RETURN) → clear()
 *
 * Server-only. Always cleared in a finally block so we never leak state
 * across packets even if Create throws.
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
