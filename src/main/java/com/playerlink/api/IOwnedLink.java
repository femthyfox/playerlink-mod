package com.playerlink.api;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Duck-typed interface implemented (via Mixin) on
 * {@code RedstoneLinkBlockEntity}. Allows reading/writing the per-link
 * owner UUID.
 */
public interface IOwnedLink {
    @Nullable
    UUID playerlink$getOwner();
    void playerlink$setOwner(@Nullable UUID owner);
}
