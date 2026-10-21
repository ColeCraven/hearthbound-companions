# Tech decisions

Why the mod is built the way it is. I sorted all of this out in October 2026, so the versions
below are as of 2026-10-20. My background reading is in [RESEARCH.md](RESEARCH.md).

## Minecraft 26.3 and Java 25

I went with Java Edition 26.3 ("Wilderness Bound", released 2026-09-15) because it's the latest
stable release and it's what I play. 26.4 has snapshots 1-5 so far (one a week since 2026-09-22)
and is planned for Q4 2026 ([version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json),
[minecraft.wiki](https://minecraft.wiki/w/Java_Edition_26.4)). Minecraft 26.x needs Java 25, so
the build uses `options.release = 25`.

The big change for modders is that since 26.1 the game isn't obfuscated anymore
([Fabric blog](https://fabricmc.net/2025/10/31/obfuscation.html)). No Yarn, no Intermediary, no
remapping step, just Mojang's own names, and stack traces are actually readable. The downside is
that lots of tutorials still use Yarn names (`ServerWorld`, `PlayerEntity`, ...) or older Mojang
names that are wrong for this version, so I always check names against the decompiled 26.3 source.

Being on the newest version means better tooling and fewer up-to-date tutorials. Porting to 26.4
later should be small, since there's no mapping churn anymore.

## Fabric over NeoForge

| | Fabric | NeoForge |
|---|---|---|
| 26.3 status | Stable: Loader 0.19.5 + API 0.164.0+26.3 | Stable since 26.3.0.76 (2026-10-11), latest 26.3.0.95 |
| Already in my game | Yes | No |
| Automated testing | Server GameTests and client game tests (simulated keyboard/mouse, screenshots, screenshot comparison) | Server GameTests, JUnit; no official client test API found |
| Tooling | Loom 1.18, unobfuscated `net.fabricmc.fabric-loom` plugin | ModDevGradle 2.0.148 |
| Philosophy | Small loader + modular API; mixins for anything else | Larger API with more built-in hooks (capabilities, events) |

It came down to two things. I already play on Fabric 26.3, and Fabric's client game-test API lets
me script real in-game playtests and grab screenshots for the README. NeoForge has more built-in
events, but nothing this mod needs is missing from Fabric API (entity attributes, networking,
commands, key bindings, HUD, data storage). Fabric also updates quickly; there are already builds
for the 26.4 snapshots.

## Build setup

The versions match the official `fabric-example-mod` 26.3 branch. I pinned stable releases instead
of `-SNAPSHOT` so builds are reproducible, and Gradle 9.7.1 comes through the wrapper.

```properties
minecraft_version=26.3
loader_version=0.19.5
loom_version=1.18.4
fabric_api_version=0.164.0+26.3
```

A few things are different from older Fabric setups:

* The plugin id is `net.fabricmc.fabric-loom` (the `-remap` variant is only for 1.21.11 and older).
* Dependencies use plain `implementation` (not `modImplementation`), and the output is the normal
  `jar` task (no `remapJar`).
* `splitEnvironmentSourceSets()` keeps client-only code (rendering, screens) in `src/client`, so
  the server never loads client classes by accident.
* `fabricApi.configureTests { ... }` creates a `gametest` source set with `runGameTest`
  (also run by `./gradlew build`) and `runClientGameTest`.
* JUnit 5 through `fabric-loader-junit` covers the plain-Java logic (parser, task state machines,
  dialogue selection, AI response parsing).

Sources: [fabric-example-mod 26.3](https://github.com/FabricMC/fabric-example-mod/tree/26.3),
[Fabric 26.3 announcement](https://fabricmc.net/2026/09/15/263.html),
[Loom docs](https://docs.fabricmc.net/develop/loom/),
[automated testing docs](https://docs.fabricmc.net/develop/automatic-testing),
[Loom releases](https://github.com/FabricMC/fabric-loom/releases).

## The companion entity

I looked at a few ways to make a player-shaped companion.

A fake player (a `ServerPlayer` subclass) is what bot mods use. It behaves exactly like a player,
but it shows up in the player list, counts for sleeping, breaks easily with other mods, and has no
pathfinding.

The new vanilla `Mannequin` / `Avatar` classes (player-shaped entities added in 1.21.9) would give
me the player renderer for free, but they're not `Mob`s, so no navigation or goals.

So I went with a `PathfinderMob` subclass rendered with the vanilla `PlayerModel`. That gets me
vanilla navigation (doors, water, climbing), goal selectors and normal entity saving, and the
client renderer uses the player model with the correct slim or wide arms and the built-in skin
textures.

The 26.3 jar has all nine default skins in both variants:
`textures/entity/player/{wide,slim}/{steve,alex,ari,efe,kai,makena,noor,sunny,zuri}.png` (18
textures). The mod only stores a `(skin, model)` pair per companion and points the renderer at the
game's own texture, so no Mojang images are committed to this repo.

## Saving

Companion bodies save with their chunk like any mob: order, inventory, equipment, skin, memory and
what each player has told them. A small world-level roster (`hearthbound_roster.json`) also keeps
track of every companion while their chunk is unloaded, which is what the party limit and the hub
use. I went with a plain JSON file over `SavedData` because it's easy to read and fix by hand on a
server.

## Networking and UI

Talking happens in the normal chat box, so it works the same with or without the mod installed on
the client. The only custom packet is the party status for the HUD (Fabric's payload API). The HUD
shows each companion's health and what they're doing, and you can hide it.

One 26.3 gotcha: it replaced GLFW with SDL for input. Key bindings use `InputConstants`. The talk key
just opens the vanilla chat box with the companion's name typed in, so I didn't need a text input of
my own.

## Things that could go wrong

Fabric's docs lag behind (docs.fabricmc.net tops out at 26.2). When they don't cover something, I
read the decompiled 26.3 sources (`genSources`) and Fabric API's own 26.3 test mods.

Vanilla pathfinding can't build bridges or dig tunnels on its own. Digging and building are orders
you give, with their own careful code (checking for lava, water and drops, and checking a whole
build site before starting). Everything else sticks to what navigation can do reliably, notices
when a companion is stuck and has them say so, and lets followers teleport to catch up.

AI replies could contradict what's actually happening in the game. That's why the AI can't act on
its own: it proposes intents, the game validates them, and the reply is only produced once the
outcome is known.

Client game tests open a real window on Windows, so I run those on purpose instead of on every
build. Server GameTests and JUnit run on every build.
