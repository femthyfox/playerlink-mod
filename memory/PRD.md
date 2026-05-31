# Player-Linked Redstone Frequencies — PRD

## Original problem statement
> Create a custom NeoForge addon for the **Create** mod (Minecraft 1.21.1,
> Create 6.0.x) that makes Redstone Link frequencies player-specific so
> that two players on the same multiplayer server using the same
> item-slot combination never cross-connect. The same isolation must
> extend to the **Linked Controller** (per-slot owners) and remain easy
> to extend to additional ownable items later (e.g. Create Aeronautics
> typewriter).

## User-confirmed choices
* **Target**: Minecraft 1.21.1 + NeoForge, Create 6.0.x
* **Whitelist source**: server's vanilla `whitelist.json`
* **Owner assignment**: any player holding the link/controller can
  assign any whitelisted player as the owner (open assignment)
* **Backward compatibility**: links / controller slots with no owner
  behave exactly like vanilla Create
* **Build location**: user builds locally via GitHub Actions — cloud
  container CANNOT run `./gradlew build`

## Architecture (current)
* `com.playerlink.api.PlayerLinkApi` — single static facade for every
  ownership read/write. **All internal code routes through this class.**
  See `EXTENDING.md` for the integration recipe.
* `com.playerlink.api.IFrequencyOwner` / `IOwnedLink` — duck-typed
  interfaces injected by mixins; treated as internal implementation
  detail (don't call from outside the api/mixin packages).
* `com.playerlink.util.ControllerOwners` — per-slot stack storage
  (`CUSTOM_DATA` → `playerlink_owners.{0..5}`).
* `com.playerlink.util.StackOwners` — single-owner stack storage
  (`CUSTOM_DATA` → `playerlink_owner`). **Reserved for future items.**
* `com.playerlink.util.ControllerOwnerContext` — thread-local stash
  for the "currently-transmitting" owner; exposed via
  `PlayerLinkApi.beginTransmit/endTransmit/currentTransmitOwner`.
* **Mixins** — all use `PlayerLinkApi` for ownership lookups:
  * `FrequencyMixin` — @Unique UUID field + equals/hashCode folding
  * `FrequencyOfMixin` — stamps owner on every produced Frequency
    using the active transmit context
  * `RedstoneLinkBlockEntityMixin` — BE-level owner storage (NBT)
  * `LinkBehaviourMixin` — block-link emission tagging
  * `LinkedControllerServerHandlerMixin` — controller-side transmit
    isolation (reflection-cached for hot-path safety)
  * `RedstoneLinkFrequencySlotMixin` / `ValueBoxTransformMixin` —
    click-on-face detection
  * `RedstoneLinkRendererMixin` → `LinkFaceRenderer` — in-world face
* **Networking** (NeoForge `PayloadRegistrar`):
  * C→S: `RequestWhitelistPacket`, `SetOwnerPacket`,
    `RequestControllerWhitelistPacket`, `SetControllerSlotOwnerPacket`,
    `ClearAllControllerOwnersPacket`
  * S→C: `WhitelistResponsePacket`, `ControllerWhitelistResponsePacket`
* **UI**:
  * `PlayerSelectScreen` (block mode + controller-slot mode)
  * `LinkedControllerScreenEvents` (face row + side-relocated buttons)
  * In-world face render via `LinkFaceRenderer`
  * Keybind `K` to open block-link picker when looking at a link

## What's been implemented
* Redstone Link: BE owner storage, NBT persistence, BlockBench face
  render, GUI face-slot click detection, PlayerSelectScreen
* Linked Controller: per-slot owners on stack, picker GUI, face row
  under freq columns, right-side buttons (no overlap)
* Refactored everything to go through `PlayerLinkApi` (2026-02)
* Performance pass (2026-02): batch reads, pre-allocated tooltips,
  cached quaternions, cached reflected method, gated init logging
* `EXTENDING.md` integration guide for future items
* `CHANGELOG.md` to track what's been built

## In progress
* **Frequency isolation on controller transmit** — blocked on user CI
  logs revealing the correct `LinkedControllerItem` static method
  name. Look for the log line:
  `[PlayerLink] slot-items API NOT FOUND. Tried: ... Static methods on LinkedControllerItem: ...`
  Once that line is shared, add the right name to `SLOT_ITEMS_METHODS`
  in `LinkedControllerServerHandlerMixin`.
* **Auto-copy link owner when binding to a controller slot** — same
  blocker; needs the bind-path method name.

## Backlog / future
* Wire the typewriter (Create Aeronautics) using the new
  `StackOwners` + `PlayerLinkApi.beginTransmit` pattern — see
  `EXTENDING.md` for the worked example. **NOT to be done now per user
  request — just made easy to add later.**
* Visual indicator on the link block when owned (currently shown only
  via the GUI face slot).
* `/playerlink list` admin command listing all owned links per dim.
* Permission node integration (LuckPerms etc.) for restricting who
  can assign ownership.
* Optional: wrench-click integration with Create's value-settings
  overlay (current UX is the face-slot click).

## Notes / Risks
* **Cannot build inside the cloud container** — user must run
  `./gradlew build` via GitHub Actions / locally.
* If a future Create release renames any of:
  * `RedstoneLinkNetworkHandler$Frequency` — affects `FrequencyMixin`,
    `FrequencyOfMixin`
  * `LinkBehaviour#getNetworkKey` — affects `LinkBehaviourMixin`
  * `RedstoneLinkBlockEntity#write/read` — affects
    `RedstoneLinkBlockEntityMixin`
  * `LinkedControllerServerHandler#receivePressed` — affects
    `LinkedControllerServerHandlerMixin`
  * `LinkedControllerItem.getFrequencyItems` (or whatever its real
    name is) — affects `SLOT_ITEMS_METHODS` in
    `LinkedControllerServerHandlerMixin`
  …only that single file needs updating thanks to the API layer.

## Next action items
1. **User**: run a CI build, share lines beginning with `[PlayerLink]`
   from the resulting `latest.log`. The two pending bugs (transmit
   isolation, auto-copy on bind) unblock as soon as we see the actual
   `Static methods on LinkedControllerItem` list.
2. **Then**: wire the resolved method name into `SLOT_ITEMS_METHODS`.
3. Future: integrate Create Aeronautics typewriter using
   `EXTENDING.md`'s worked example. ~15 lines in a new mixin file.
