# Technical foundation and trade-offs

Research date: 2026-10-20. Sources are linked inline; see also [RESEARCH.md](RESEARCH.md).

## Decision summary

| Choice | Decision | Why |
|---|---|---|
| Minecraft version | **Java Edition 26.3** ("Wilderness Bound", released 2026-09-15) | Latest stable release; what the author plays. 26.4 is still in weekly snapshots. |
| Mod loader | **Fabric** (Loader 0.19.5, Fabric API 0.164.0+26.3) | Already installed in the target game; ships Fabric-only automated *client* game tests (simulated input + screenshots); fast updates (26.4 snapshot builds already exist). |
| Build tool | **Gradle 9.7.1** via the wrapper, **Fabric Loom 1.18.4** (`net.fabricmc.fabric-loom`) | Matches the official `fabric-example-mod` 26.3 branch; pinned stable versions instead of `-SNAPSHOT` for reproducible builds. |
| Java | **Java 25** (`options.release = 25`) | Required by Minecraft 26.x. |
| Mappings | **None** - Mojang's own names | Minecraft has shipped unobfuscated since 26.1, so there is no Yarn/Intermediary/remapping step. |
| Entity base | Custom `PathfinderMob` rendered with the vanilla player model | Gives us vanilla pathfinding, goals and saving; player model gives correct wide/slim proportions. |
| AI dialogue | **Optional**, provider-neutral: Ollama (local), any OpenAI-compatible API, or Anthropic | Mod must be fully playable offline; see [AI_DESIGN.md](AI_DESIGN.md). |

## Minecraft version

* **26.3** is the latest release; 26.4 has snapshots 1-5 (one a week since 2026-09-22) and is
  planned for Q4 2026 ([version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json),
  [minecraft.wiki](https://minecraft.wiki/w/Java_Edition_26.4)).
* Since 26.1 the game is **no longer obfuscated** ([Fabric blog](https://fabricmc.net/2025/10/31/obfuscation.html)).
  For modders that means readable class names in stack traces and no mapping layer. It also means
  older tutorials written with Yarn names (`ServerWorld`, `PlayerEntity`, ...) or with older
  Mojang names are often wrong for this version - we always check names against the decompiled
  26.3 source.
* Trade-off: targeting the newest version gives the best player experience and tooling but a
  smaller pool of up-to-date tutorials. Porting to 26.4 later should be small because there is no
  mapping churn.

## Loader: Fabric vs NeoForge

| | Fabric | NeoForge |
|---|---|---|
| 26.3 status | Stable: Loader 0.19.5 + API 0.164.0+26.3 | Stable since 26.3.0.76 (2026-10-11), latest 26.3.0.95 |
| Already in my game | **Yes** | No |
| Automated testing | Server GameTests **and** client game tests (simulated keyboard/mouse, screenshots, screenshot comparison) | Server GameTests, JUnit; no official client test API found |
| Tooling | Loom 1.18, unobfuscated `net.fabricmc.fabric-loom` plugin | ModDevGradle 2.0.148 |
| Philosophy | Small loader + modular API; mixins for anything else | Larger API with more built-in hooks (capabilities, events) |

**Chosen: Fabric.** The decisive factors are that I already play on Fabric 26.3 and that
Fabric's client game-test API lets me script real in-game playtests and capture README
screenshots. NeoForge would have given more built-in events, but nothing this mod needs is missing
from Fabric API (entity attributes, networking, commands, key bindings, HUD, data storage).

## Build setup

```properties
minecraft_version=26.3
loader_version=0.19.5
loom_version=1.18.4
fabric_api_version=0.164.0+26.3
```

* Plugin id `net.fabricmc.fabric-loom` (the `-remap` variant is only for 1.21.11 and older).
* Dependencies use plain `implementation` (not `modImplementation`), and the output is the normal
  `jar` task (no `remapJar`).
* `splitEnvironmentSourceSets()` keeps client-only code (rendering, screens) in `src/client`, so
  the server never loads client classes by accident.
* `fabricApi.configureTests { ... }` creates a `gametest` source set with `runGameTest`
  (also run by `./gradlew build`) and `runClientGameTest`.
* JUnit 5 through `fabric-loader-junit` for plain-Java logic (parser, task state machines, dialogue
  selection, AI response parsing).

Sources: [fabric-example-mod 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3),
[Fabric 26.3 announcement](https://fabricmc.net/2026/09/15/263.html),
[Loom docs](https://docs.fabricmc.net/develop/loom/),
[automated testing docs](https://docs.fabricmc.net/develop/automatic-testing),
[Loom releases](https://github.com/FabricMC/fabric-loom/releases).

## Companion entity design

**Options considered**

1. *Fake player (`ServerPlayer` subclass)*, as used by bot mods: perfect player behaviour, but it
   shows up in the player list, counts for sleeping, breaks easily with other mods, and has no
   pathfinding.
2. *Extend the new vanilla `Mannequin` / `Avatar` classes* (player-shaped entities added in
   1.21.9): get the player renderer for free, but they're not `Mob`s, so no navigation or goals.
3. **A `PathfinderMob` subclass rendered with the vanilla `PlayerModel`** - chosen. Vanilla
   navigation (doors, water, climbing), goal selectors, and normal entity saving; the client
   renderer uses the player model with the correct slim/wide arms and the built-in skin textures.

**Skins.** The 26.3 jar contains all nine default skins in both variants:
`textures/entity/player/{wide,slim}/{steve,alex,ari,efe,kai,makena,noor,sunny,zuri}.png` (18
textures). The mod stores only a `(skin, model)` pair per companion and points the renderer at the
game's own texture - no Mojang images are committed to this repository.

## Saving

* Companion bodies save with their chunk like any mob (order, inventory, equipment, skin).
* Identity and memory also live in a world-level `SavedData` registry so a companion can be
  restored next to its owner if its body is stranded in an unloaded chunk.

## Networking and UI

* Custom packets via Fabric's payload API (client -> server: message, quick command; server ->
  client: companion line, party status, open conversation).
* Screens use vanilla widgets and textures so they look like Minecraft. A small party HUD shows
  selected companion and current task; it can be hidden.
* 26.3 replaced GLFW with **SDL** for input: key bindings use `InputConstants`, and custom text
  inputs must report focus changes (`onTextInputFocusChange`). We use vanilla `EditBox`, which
  already does this.

## Risks

| Risk | Mitigation |
|---|---|
| Fabric docs lag behind (docs.fabricmc.net tops out at 26.2) | Read the decompiled 26.3 sources (`genSources`) and Fabric API's own 26.3 test mods. |
| Vanilla pathfinding can't build bridges or dig tunnels | Keep tasks to what navigation can reliably do; detect "stuck" and explain; teleport catch-up while following. |
| AI replies contradict game state | AI can't act; it proposes intents that the game validates, and replies are produced after the outcome is known. |
| Client game tests open a real window on Windows | Run them deliberately (not on every build); server GameTests and JUnit run on every build. |
