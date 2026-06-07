package com.playerlink.util;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the Redstone Link owner UUID that was pending when a player
 * initiated a controller/typewriter bind (right-clicked a link).
 * Consumed by LinkedControllerServerHandlerMixin.receivePressed so the
 * owner gets saved to the newly-bound slot automatically.
 */
public final class ControllerBindContext {

    private static final ConcurrentHashMap<UUID, UUID> PENDING = new ConcurrentHashMap<>();

    private ControllerBindContext() {}

    public static void set(UUID playerUuid, UUID linkOwner) {
        PENDING.put(playerUuid, linkOwner);
    }

    @Nullable
    public static UUID remove(UUID playerUuid) {
        return PENDING.remove(playerUuid);
    }

    public static void clear(UUID playerUuid) {
        PENDING.remove(playerUuid);
    }
}
