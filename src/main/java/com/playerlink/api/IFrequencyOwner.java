package com.playerlink.api;

import java.util.UUID;

/**
 * Duck-typed interface implemented (via Mixin) on
 * {@code RedstoneLinkNetworkHandler.Frequency}. Allows attaching an
 * owner UUID so that two Frequencies with identical item-stack contents
 * but different owners do NOT compare equal.
 */
public interface IFrequencyOwner {
    UUID playerlink$getOwner();
    void playerlink$setOwner(UUID owner);
}
