package com.playerlink.api;

import com.playerlink.util.ControllerOwnerContext;
import com.playerlink.util.ControllerOwners;
import com.playerlink.util.StackOwners;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Single static entry point for everything related to "who owns this
 * frequency". Internal callers (mixins, packet handlers, renderers,
 * future emitter items) should ONLY talk to PlayerLink through this
 * class — that way the underlying storage layout can evolve without
 * breaking call sites.
 *
 * <h2>How to add a new ownable item (3 steps)</h2>
 *
 * <ol>
 *   <li><b>Storage</b> — pick one based on your item's needs:
 *     <ul>
 *       <li>Single owner per stack: {@link #readStackOwner} /
 *           {@link #writeStackOwner}.</li>
 *       <li>Owner per slot (controller pattern):
 *           {@link #readSlotOwner} / {@link #writeSlotOwner}.</li>
 *       <li>Owner per block in the world: have your BE implement
 *           {@link IOwnedLink}, then use {@link #readBlockOwner} /
 *           {@link #writeBlockOwner}.</li>
 *     </ul>
 *   </li>
 *   <li><b>UI</b> — reuse {@code PlayerSelectScreen} (block mode for
 *       BEs, controller-slot mode for stacks). Send your own packet on
 *       Assign that routes to the right write method here.</li>
 *   <li><b>Emit-side mixin</b> — wrap the call into Create's emit code
 *       with {@link #beginTransmit}/{@link #endTransmit}:
 *       <pre>{@code
 *   PlayerLinkApi.beginTransmit(PlayerLinkApi.readStackOwner(stack));
 *   try { /* call Create emit */ } finally { PlayerLinkApi.endTransmit(); }
 *       }</pre>
 *       {@link com.playerlink.mixin.FrequencyOfMixin} reads the
 *       thread-local at the end of {@code Frequency.of(...)} and
 *       stamps every produced frequency with the active owner — so you
 *       never have to touch the Frequency object yourself.</li>
 * </ol>
 *
 * <p>See {@code EXTENDING.md} in the repo root for a copy-paste-ready
 * worked example targeting a hypothetical Create Aeronautics typewriter.
 */
public final class PlayerLinkApi {

    private PlayerLinkApi() {}

    // ════════════════════════════════════════════════════════════════
    //   FREQUENCY STAMPING — used by emit-mixins and receive-mixins
    // ════════════════════════════════════════════════════════════════

    /**
     * Tags a Create {@link Frequency} object with an owner UUID so it
     * compares un-equal to frequencies owned by other players.
     * No-op for null {@code freq}.
     */
    public static void stampOwner(@Nullable Frequency freq, @Nullable UUID owner) {
        if (freq instanceof IFrequencyOwner fo) fo.playerlink$setOwner(owner);
    }

    /** Returns the owner stamped on this frequency, or {@code null}. */
    @Nullable
    public static UUID getStampedOwner(@Nullable Frequency freq) {
        return (freq instanceof IFrequencyOwner fo) ? fo.playerlink$getOwner() : null;
    }

    // ════════════════════════════════════════════════════════════════
    //   BLOCK-ENTITY OWNERSHIP — links + future emitter blocks
    // ════════════════════════════════════════════════════════════════

    /** Returns the owner UUID stored on this BE, or {@code null}. */
    @Nullable
    public static UUID readBlockOwner(@Nullable BlockEntity be) {
        return (be instanceof IOwnedLink owned) ? owned.playerlink$getOwner() : null;
    }

    /** Sets or clears the owner UUID on this BE. No-op for non-{@link IOwnedLink}. */
    public static void writeBlockOwner(@Nullable BlockEntity be, @Nullable UUID owner) {
        if (be instanceof IOwnedLink owned) owned.playerlink$setOwner(owner);
    }

    // ════════════════════════════════════════════════════════════════
    //   SINGLE-OWNER ITEMSTACK — handheld emitters, wearables, etc.
    // ════════════════════════════════════════════════════════════════

    @Nullable
    public static UUID readStackOwner(ItemStack stack) {
        return StackOwners.get(stack);
    }

    public static void writeStackOwner(ItemStack stack, @Nullable UUID owner) {
        StackOwners.set(stack, owner);
    }

    // ════════════════════════════════════════════════════════════════
    //   MULTI-SLOT ITEMSTACK — the Linked Controller pattern
    // ════════════════════════════════════════════════════════════════

    @Nullable
    public static UUID readSlotOwner(ItemStack stack, int slot) {
        return ControllerOwners.get(stack, slot);
    }

    public static void writeSlotOwner(ItemStack stack, int slot, @Nullable UUID owner) {
        ControllerOwners.set(stack, slot, owner);
    }

    /** Bulk-read all slot owners with a single tag copy. Hot-path-friendly. */
    public static UUID[] readAllSlotOwners(ItemStack stack) {
        return ControllerOwners.getAll(stack);
    }

    /** Returns the number of owner slots on a controller-style stack. */
    public static int slotCount() {
        return ControllerOwners.SLOT_COUNT;
    }

    // ════════════════════════════════════════════════════════════════
    //   TRANSMIT CONTEXT — thread-local "current emitter owner"
    // ════════════════════════════════════════════════════════════════

    /**
     * Push an owner UUID onto the current thread so that any
     * {@code Frequency.of(ItemStack)} call made between this and
     * {@link #endTransmit()} gets stamped with {@code owner}.
     * Always pair with a {@link #endTransmit()} call in a finally block.
     */
    public static void beginTransmit(@Nullable UUID owner) {
        ControllerOwnerContext.set(owner);
    }

    /** Returns the owner currently being transmitted, or {@code null}. */
    @Nullable
    public static UUID currentTransmitOwner() {
        return ControllerOwnerContext.get();
    }

    /** Clears the thread-local. Always call this from a finally block. */
    public static void endTransmit() {
        ControllerOwnerContext.clear();
    }
}
