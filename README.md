# Player-Linked Redstone Frequencies

A NeoForge 1.21.1 addon for the **Create** mod (6.0.x) that makes Redstone
Link frequencies **player-specific** so that two unrelated players on the
same multiplayer server using the same item combination won't
cross-connect. 

## Features

1. **Owner-tagged Frequencies** — each `RedstoneLinkBlockEntity` stores an
optional owner `UUID`. The owner UUID is folded into the
`Frequency.equals()` / `hashCode()` contract via Mixin, so the network
handler routes signals scoped to `(ownerUUID, slot1, slot2)`.
2. **In-game Owner Picker GUI** — press **`K`** (rebindable in Controls →
*Player-Linked Redstone*) while looking at a Redstone Link to open a
GUI listing all whitelisted players on the server. Pick one to assign,
or click *Clear Owner* to revert the link to vanilla shared behaviour.
3. **Whitelist-only candidates** — the candidate list is built from the
server's vanilla `whitelist.json` (the same one accepted by `/whitelist add`).
4. **Backward compatible** — a Redstone Link with **no owner assigned**
behaves exactly as vanilla Create. Existing builds keep working.
5. **`/playerlink owner` command** — convenience command:

   * `/playerlink owner` — print the looked-at link's current owner.
   * `/playerlink owner clear` — clear the looked-at link's owner.

## Project Layout

```
src/main/
├── java/com/playerlink/
│   ├── PlayerLinkMod.java                    # Main mod entry
│   ├── api/
│   │   ├── IFrequencyOwner.java              # Duck interface (Mixin → Frequency)
│   │   └── IOwnedLink.java                   # Duck interface (Mixin → BE)
│   ├── client/
│   │   ├── ClientEvents.java                 # Keybind + tick listener
│   │   ├── ClientPacketHandlers.java         # S2C packet handling
│   │   └── PlayerSelectScreen.java           # The owner-picker GUI
│   ├── mixin/
│   │   ├── FrequencyMixin.java               # Owner UUID on Frequency + equals/hashCode
│   │   └── RedstoneLinkBlockEntityMixin.java # Owner UUID on BE + NBT + getNetworkKey()
│   ├── network/
│   │   ├── PlayerLinkNetwork.java            # Payload registrar
│   │   ├── RequestWhitelistPacket.java       # C→S
│   │   ├── WhitelistResponsePacket.java      # S→C
│   │   └── SetOwnerPacket.java               # C→S
│   └── server/
│       ├── ServerEvents.java                 # /playerlink command
│       └── ServerPacketHandlers.java         # C→S packet handling
└── resources/
    ├── META-INF/neoforge.mods.toml           # (templated)
    ├── pack.mcmeta
    ├── playerlink.mixins.json
    └── assets/playerlink/lang/en\_us.json
```

## Building

Requires JDK 21.

```bash
./gradlew build
# Output: build/libs/playerlink-<version>.jar
```

To run a dev client/server with Create already mapped in:

```bash
./gradlew runClient
./gradlew runServer
```

> Important: the `compileOnly` / `runtimeOnly` Maven coordinates for
> Create, Flywheel and Registrate in `gradle.properties` must match an
> actually-published Create 6.0.x build. Visit
> <https://maven.createmod.net/> and update `create\_version`,
> `flywheel\_version`, `registrate\_version` if needed.

## How the Mixins Work

### `FrequencyMixin` → `RedstoneLinkNetworkHandler$Frequency`

* Adds `@Unique UUID playerlink$owner`.
* `@Inject(method = "equals", at = "HEAD", cancellable = true)` — if the
other object is also a `Frequency` and the owner UUIDs differ, returns
`false` immediately (short-circuit). Otherwise falls through to the
vanilla equals, which compares item-stack content.
* `@Inject(method = "hashCode", at = "RETURN", cancellable = true)` —
folds the owner UUID's hash into the original return value.

### `RedstoneLinkBlockEntityMixin` → `RedstoneLinkBlockEntity`

* Adds `@Unique UUID playerlink$ownerUuid`.
* `@Inject` into Create's `write(CompoundTag, HolderLookup.Provider, boolean)`
and `read(...)` to persist/load the owner UUID under the NBT key
`PlayerLinkOwner`.
* `@Inject(method = "getNetworkKey", at = "RETURN")` — on every network-
key build, stamps both `Frequency` instances of the returned
`Couple<Frequency>` with this BE's owner UUID via the
`IFrequencyOwner` interface. The handler then keys the link map by an
owner-aware identity.
* `playerlink$setOwner(UUID)` forces `setChanged()` + a block update so
the link is re-registered in the network with the new key.

## Network Protocol

|Direction|Payload|Purpose|
|-|-|-|
|C → S|`RequestWhitelistPacket`|"I want to edit the link at this BlockPos"|
|S → C|`WhitelistResponsePacket`|Whitelisted players + current owner|
|C → S|`SetOwnerPacket`|Final selection (UUID or empty = clear)|

All packets validate distance, that the target block is actually a
`RedstoneLinkBlockEntity`, and (for `SetOwnerPacket`) that the candidate
UUID is on the server whitelist.

## Compatibility Notes / Things to Verify Locally

Because Create's internal class layout sometimes shifts between
revisions, please verify the following before publishing:

* `RedstoneLinkNetworkHandler.Frequency` is still an inner class of
`RedstoneLinkNetworkHandler` and its package is unchanged.
* `RedstoneLinkBlockEntity#getNetworkKey()` exists and returns
`Couple<Frequency>`. If renamed (e.g. `getNetworkID`), rename the
`method = "..."` reference in `RedstoneLinkBlockEntityMixin`.
* `RedstoneLinkBlockEntity#write(CompoundTag, HolderLookup.Provider, boolean)`
and matching `read(...)` are inherited / overridden. If you target a
different revision of Create, prefer mixing into the vanilla
`saveAdditional` / `loadAdditional` instead.

If any of the above changed, only the **method names inside the two
mixin files** need adjusting — the rest of the design is decoupled via
the `IFrequencyOwner` / `IOwnedLink` duck interfaces.

## License

MIT — do whatever you like, please credit if useful.

