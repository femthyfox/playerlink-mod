# Adding a new ownable item to PlayerLink

This guide shows how to make any new item / block / mod hook into the
player-frequency ownership system without rewriting the core. The same
pattern works for handheld emitters, wearables, custom blocks, and
cross-mod hooks (e.g. a future Create Aeronautics typewriter).

Everything goes through one class — `com.playerlink.api.PlayerLinkApi`.
That's it. You should never have to touch `ControllerOwners`,
`StackOwners`, the mixins, or the BE interfaces directly.

---

## 1 · Pick a storage shape

| Storage | When to use it | API |
|---|---|---|
| **Single owner per ItemStack** | Handheld emitter, wearable, single-purpose tool. *(e.g. typewriter — one operator at a time)* | `PlayerLinkApi.readStackOwner / writeStackOwner` |
| **Per-slot owners on an ItemStack** | Multi-binding item like the Linked Controller (6 buttons → 6 owners) | `PlayerLinkApi.readSlotOwner / writeSlotOwner` |
| **Owner per block in the world** | A new placed-in-world emitter/receiver | Have the block entity implement `IOwnedLink`, then `PlayerLinkApi.readBlockOwner / writeBlockOwner` |

That decision drives everything else.

---

## 2 · Wire up the UI

`PlayerSelectScreen` already supports two modes — reuse it:

```java
// Block mode (BE-stored owner)
mc.setScreen(PlayerSelectScreen.forBlock(blockPos, currentOwner, whitelistEntries));

// Single-owner stack mode — reuse the controller-slot factory and treat
// "slot 0" as the single owner location.
mc.setScreen(PlayerSelectScreen.forControllerSlot(0, currentOwner, whitelistEntries, returnScreen));
```

For your own packet, copy `SetControllerSlotOwnerPacket` and replace
the `writeSlotOwner` call inside the server handler with the storage
method you picked in step 1.

> **Tip:** Always pass `mc.screen` as the `returnScreen` so the picker
> closes back to the previous menu, not the world.

---

## 3 · Stamp the owner onto emitted frequencies

This is the only mixin code most integrations need. The pattern:

```java
@Mixin(value = MyEmitterItem.class, remap = false)
public abstract class MyEmitterItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void playerlink$pushOwner(Level level, Player player, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide) return;
        ItemStack stack = player.getItemInHand(hand);
        PlayerLinkApi.beginTransmit(PlayerLinkApi.readStackOwner(stack));
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void playerlink$popOwner(Level level, Player player, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        PlayerLinkApi.endTransmit();
    }
}
```

What this does:

1. **HEAD** of the emit method: push the player UUID onto the thread.
2. Inside the method, Create calls `RedstoneLinkNetworkHandler.Frequency.of(stack)` to make the frequency.
3. `FrequencyOfMixin` intercepts at RETURN of `.of(...)`, reads the
   thread-local, and stamps the new Frequency with your owner UUID.
4. **RETURN** of your emit method: clear the thread-local so the next
   call isn't accidentally tagged.

You never touch the `Frequency` object directly — the central mixin
does it for you. **That's the whole integration.**

> **Why a thread-local?** Create's `Frequency.of(...)` doesn't know who
> the caller is. Threading the owner through a ThreadLocal lets us
> stamp frequencies without modifying every call site.
>
> **Always pair `beginTransmit` with `endTransmit` in a finally block**
> if your method can throw, otherwise stale owners leak into subsequent
> emits on the same thread.

---

## Worked example — Create Aeronautics typewriter (not implemented yet)

If/when you wire the typewriter into PlayerLink:

```java
// 1. Storage — typewriter has one operator at a time → StackOwner.
@Mixin(value = TypewriterItem.class, remap = false)
public abstract class TypewriterMixin {

    @Inject(method = "useOn", at = @At("HEAD"))
    private void playerlink$pushOwner(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getLevel().isClientSide) return;
        ItemStack stack = ctx.getItemInHand();
        UUID owner = PlayerLinkApi.readStackOwner(stack);
        // If the typewriter has no owner yet, default to the player using it.
        if (owner == null && ctx.getPlayer() != null) {
            owner = ctx.getPlayer().getUUID();
            PlayerLinkApi.writeStackOwner(stack, owner);
        }
        PlayerLinkApi.beginTransmit(owner);
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void playerlink$popOwner(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        PlayerLinkApi.endTransmit();
    }
}
```

That's it. The typewriter now emits player-isolated frequencies.
No new screens, no new packets, no new fields.

If you want a UI to let players REASSIGN the operator (not just default
to whoever first uses it), follow step 2 above with a copy of
`SetControllerSlotOwnerPacket` that calls `writeStackOwner` instead.

---

## What you should NEVER do

* ❌ Touch `IFrequencyOwner` directly — go through `PlayerLinkApi.stampOwner`.
* ❌ Call `((IOwnedLink) be).playerlink$setOwner(...)` — use
  `PlayerLinkApi.writeBlockOwner(be, ...)`.
* ❌ Read CustomData yourself to discover an owner — use the API.
* ❌ Skip the `endTransmit()` call. Always put it in a finally.

If you find yourself wanting to do one of these, that's a sign the API
should grow — open an `// TODO PlayerLinkApi:` note instead of monkey-
patching.
