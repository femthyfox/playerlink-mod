package com.playerlink.mixin;

import com.playerlink.api.ITypewriterKeyOwner;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Injects per-key player-owner UUID storage into the Typewriter block entity.
 * Uses @Pseudo so the mixin is silently skipped when Create Aeronautics is absent.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity",
       remap = false)
public abstract class LinkedTypewriterBlockEntityMixin implements ITypewriterKeyOwner {

    @Unique private static final String KEY_OWNERS_TAG = "PlayerLinkKeyOwners";

    @Unique private final Map<Integer, UUID> playerlink$keyOwners = new HashMap<>();

    @Override
    @Nullable
    public UUID playerlink$getKeyOwner(int keyNum) {
        return playerlink$keyOwners.get(keyNum);
    }

    @Override
    public void playerlink$setKeyOwner(int keyNum, @Nullable UUID owner) {
        if (owner == null) playerlink$keyOwners.remove(keyNum);
        else playerlink$keyOwners.put(keyNum, owner);
    }

    @Inject(method = "write", at = @At("TAIL"), remap = false, require = 0)
    private void playerlink$write(CompoundTag tag, HolderLookup.Provider reg, boolean client, CallbackInfo ci) {
        if (playerlink$keyOwners.isEmpty()) return;
        CompoundTag owners = new CompoundTag();
        for (Map.Entry<Integer, UUID> e : playerlink$keyOwners.entrySet()) {
            owners.putUUID(e.getKey().toString(), e.getValue());
        }
        tag.put(KEY_OWNERS_TAG, owners);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false, require = 0)
    private void playerlink$read(CompoundTag tag, HolderLookup.Provider reg, boolean client, CallbackInfo ci) {
        playerlink$keyOwners.clear();
        if (!tag.contains(KEY_OWNERS_TAG, 10)) return;
        CompoundTag owners = tag.getCompound(KEY_OWNERS_TAG);
        for (String key : owners.getAllKeys()) {
            try {
                int keyNum = Integer.parseInt(key);
                if (owners.hasUUID(key)) playerlink$keyOwners.put(keyNum, owners.getUUID(key));
            } catch (NumberFormatException ignored) {}
        }
    }
}
