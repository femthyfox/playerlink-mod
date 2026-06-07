package com.playerlink.api;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Duck-typed interface injected (via Mixin) onto LinkedTypewriterBlockEntity.
 * Allows per-key player-owner UUIDs to be stored and retrieved.
 */
public interface ITypewriterKeyOwner {
    @Nullable UUID playerlink$getKeyOwner(int keyNum);
    void playerlink$setKeyOwner(int keyNum, @Nullable UUID owner);
}
