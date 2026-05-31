# Changelog

All notable changes to PlayerLink. Newest first.

---

## 2026-02 — Architecture pass + perf cleanup

### Added
- **`com.playerlink.api.PlayerLinkApi`** — single static facade for every
  ownership read/write (frequency stamping, BE owners, single-owner stacks,
  multi-slot stacks, transmit thread-local). All internal code now routes
  through this class.
- **`com.playerlink.util.StackOwners`** — single-owner ItemStack helper
  (mirrors `ControllerOwners`'s API). Reserved for future items like the
  Create Aeronautics typewriter; not yet wired into any mixin.
- **`ControllerOwners.getAll(stack)`** — bulk read of all 6 slot owners in
  a single tag copy.
- **`EXTENDING.md`** — copy-paste-ready guide for adding new ownable items
  / blocks / cross-mod hooks.

### Changed
- Refactored every call site to use `PlayerLinkApi`. The duck-typed
  interfaces (`IOwnedLink`, `IFrequencyOwner`) are now an internal
  implementation detail of two mixins.
- `LinkedControllerScreenEvents` now relocates Create's trash + confirm
  buttons to a vertical stack on the right side of the GUI on every
  `Init.Post`; faces sit directly under the freq columns (4 px gap).
- `ServerPacketHandlers.handleSetControllerSlotOwner` no longer tries to
  call `item.use()` server-side — that was just toggling the controller's
  active state, not reopening the GUI. The client's
  `setScreen(returnScreen)` is sufficient since the menu was never closed.

### Performance
- `ControllerOwners.get/set` — pre-compute slot key strings ("0".."5")
  once; previously allocated a fresh `Integer.toString` on every call.
- `ControllerOwners.get/set` — fast-path skip when CUSTOM_DATA doesn't
  contain the root key (no `copyTag()` deep copy).
- `LinkedControllerScreenEvents.onRender` — batch-read all 6 owners with
  one tag copy per frame instead of six.
- `LinkedControllerScreenEvents` — pre-allocated hover tooltip components
  (`TIP_OWNED` / `TIP_EMPTY`) instead of allocating per hover frame.
- `LinkedControllerScreenEvents.onInit` — verbose layout log now fires
  ONCE per screen instance (was firing on every resize/return).
- `LinkFaceRenderer` — pre-baked per-facing rotation `Quaternionf`
  instances (was allocating one per visible link per frame); identity
  rotation case skips `mulPose` entirely.
- `LinkFaceRenderer` — removed the 5-second diagnostic log.
- `LinkedControllerServerHandlerMixin` — caches the resolved
  `LinkedControllerItem` method via reflection on first successful
  lookup; no reflection on subsequent presses.
- `ValueBoxTransformMixin` — removed the 2-second diagnostic log.

### Removed
- `playerlink$reopenController` helper in `ServerPacketHandlers` (broken
  no-op that toggled the controller's active state).

---

## 2026-01 — Linked Controller integration (in progress)

- Added 6 player-face slots to Create's LinkedController GUI.
- `ControllerOwners` storage on the controller's ItemStack via
  `CUSTOM_DATA`.
- Packets: `RequestControllerWhitelist`, `ControllerWhitelistResponse`,
  `SetControllerSlotOwner`, `ClearAllControllerOwners`.
- `PlayerSelectScreen.forControllerSlot(...)` factory + return-screen
  handling so the picker closes back to the controller GUI.
- Initial transmit-isolation mixin (`LinkedControllerServerHandlerMixin`)
  — uses reflection to find Create's per-slot frequency items method.
  **Pending CI logs** to confirm the right method name on Create 6.0.x.

---

## 2026-01 — Initial release

- NeoForge 1.21.1 + Create 6.0.x mod scaffolding.
- `RedstoneLinkBlockEntityMixin` + `FrequencyMixin` for per-link owner
  storage and frequency equality folding.
- `PlayerSelectScreen` GUI with whitelist search.
- `/playerlink owner` and `/playerlink owner clear` commands.
- 3D player face rendering on each Redstone Link block via
  `LinkFaceRenderer` and `RedstoneLinkRendererMixin`.
- `ValueBoxTransformMixin` for accurate face-slot click detection.
