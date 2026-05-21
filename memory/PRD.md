# Player-Linked Redstone Frequencies — PRD

## Original problem statement
> Create a custom NeoForge addon for the **Create** mod (Minecraft 1.21.1,
> Create 6.0.x) that makes Redstone Link frequencies player-specific so
> that two players on the same multiplayer server using the same
> item-slot combination never cross-connect.

## User-confirmed choices (Jan 2026)
* **Target**: Minecraft 1.21.1 + NeoForge, Create 6.0.x
* **Whitelist source**: server's vanilla `whitelist.json`
* **Owner assignment**: any player holding the link can assign any
  whitelisted player as owner (open assignment)
* **Backward compatibility**: links with no owner behave exactly like
  vanilla Create

## Architecture
* **Mixins**
  * `FrequencyMixin` (on `RedstoneLinkNetworkHandler$Frequency`) — adds
    an owner UUID field via `@Unique`; folds it into `equals` (HEAD
    short-circuit) and `hashCode` (RETURN combine).
  * `RedstoneLinkBlockEntityMixin` (on `RedstoneLinkBlockEntity`) —
    stores owner UUID on the BE, persists to NBT in `write`/`read`,
    stamps emitted Frequencies inside `getNetworkKey()`.
* **Duck-type API** (`IFrequencyOwner`, `IOwnedLink`) keeps mod code
  decoupled from Create internals.
* **Networking** (NeoForge `PayloadRegistrar`): three payloads —
  RequestWhitelist (C→S), WhitelistResponse (S→C), SetOwner (C→S).
* **UI**: client-side `PlayerSelectScreen` (Screen subclass with
  EditBox search, ObjectSelectionList, Assign / Clear / Close buttons),
  opened via a keybind (`K` by default) when the player is looking at a
  Redstone Link. Owner display also surfaced through `/playerlink
  owner` command.
* **Whitelist source**: server-side `PlayerList.getWhiteList()`,
  enriched with names via `ProfileCache`.

## What's been implemented (Jan 2026)
* Full NeoForge 1.21.1 mod scaffolding (build.gradle / settings.gradle
  / gradle.properties using ModDevGradle 1.0.21)
* Templated `neoforge.mods.toml`
* Two Mixins covering Frequency equality + BE owner persistence +
  network-key tagging
* Three payloads (Request / Response / SetOwner) with stream codecs
* Server packet handlers with distance + whitelist validation
* `/playerlink owner` and `/playerlink owner clear` commands
* Client keybind, ray-trace, and `PlayerSelectScreen` GUI with search,
  current-owner highlight, and empty-whitelist messaging
* English language file with all GUI / chat / keybind strings
* `README.md` documenting build steps & class layout

## Backlog / P2
* Visual feedback in-world (small indicator on the link block when
  owned) — currently only via GUI / command
* Wrench-click integration into Create's own value-settings overlay
  (optional polish; current UX is keybind-based)
* `/playerlink list` admin command listing all owned links in a
  dimension
* Permission node integration (e.g. LuckPerms) for restricting who can
  assign ownership beyond "any whitelisted player"

## Notes / Risks
* **Cannot be built or run inside the Emergent cloud container** — the
  environment is Python/React/MongoDB. The user must execute
  `./gradlew build` locally with JDK 21.
* The exact published Create 6.0.x version in
  `gradle.properties::create_version` may need to be bumped to match
  what's on `maven.createmod.net` at the user's build time.
* If a future Create release renames `getNetworkKey()` or moves the
  `Frequency` inner class, the two mixin files are the only places
  that need an update (method/target strings).

## Next action items
* User: open the project in IntelliJ/Eclipse, run `./gradlew build`,
  verify it compiles against the Create artifact on maven.createmod.net.
* User: drop the resulting `playerlink-1.0.0.jar` into the server's
  `mods/` folder alongside Create + NeoForge, restart, test by giving
  two players the same item combo and confirming isolation.
