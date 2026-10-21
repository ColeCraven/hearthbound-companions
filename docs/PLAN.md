# Development plan

Hearthbound Companions adds RPG-style followers to single-player Minecraft Java 26.3
(Fabric). This plan is ordered **core first, polish after**: each phase ends with something that
works and is tested before the next one starts.

Legend: each phase lists its *exit criteria*; "verified" means covered by an automated test or an
in-game run recorded in [TESTING.md](TESTING.md).

---

## Phase 0 - Toolchain and skeleton

* Gradle 9.7.1 wrapper (SHA-256 pinned), Loom 1.18.4, Fabric Loader 0.19.5, Fabric API
  0.164.0+26.3, Java 25, split client/common source sets, `gametest` source set, JUnit.
* Empty mod that loads in the dev client.

**Exit:** `./gradlew build` succeeds (compiles, runs JUnit + server GameTests), `runClient` reaches
the title screen with the mod listed.

## Phase 1 - A companion exists (body, skin, saving)

* `CompanionEntity` (`PathfinderMob`): health, armour, inventory (18 slots), equipment, owner,
  archetype, name, skin, order. Saves/loads with the chunk.
* Client renderer using the vanilla player model; the 18 built-in default skin textures with the
  matching wide/slim arms; held items and armour layers; name tag.
* `/companion summon [archetype] [name] [skin] [wide|slim]` for testing, plus a spawn egg.
* `CompanionRegistry` (`SavedData`): identity + memory, body UUID.

**Exit:** each of the 18 skin/model combinations renders correctly (client game test screenshot);
companion survives save/quit/reload with identical name, skin, archetype, inventory (GameTest +
manual).

## Phase 2 - Deterministic orders (the core loop)

* Intent model + `CommandParser` (rule-based, synonyms, numbers in words, item names resolved
  against the registry and translated names) + `IntentValidator` with typed failure reasons.
* `TaskManager` with one order at a time and clean cancel:
  * **Follow** - distance band per personality, sprint to catch up, open doors, swim, teleport to
    the owner when > 32 blocks away or in another dimension (only to a safe spot, never into lava or
    walls).
  * **Wait** - hold position; return to it after fighting.
  * **Come** - walk to the owner once, then wait.
  * **Gather** `<item> <count>` - logs/stone/dirt/sand/gravel/ores/plants etc.; search within a
    radius, path to a standing spot, break with correct timing and tool rules, collect drops, return
    and hand over; fail with a reason (none found, unreachable, needs tool, inventory full).
  * **Give** - hand over carried items.
  * **Cancel** - stops any order and returns to idle (or follow, if they were following before).
* Recruit / dismiss (party limit 3), select.
* Stuck detection and recovery: re-path, small hop/sidestep, then either teleport (when following)
  or fail the task with "I'm stuck".

**Exit:** JUnit for parser/validator/state machine; GameTests for follow distance, wait, gather
logs, cancel mid-task, unreachable target, needs-tool failure, inventory full; manual run of each
order.

## Phase 3 - Talking (separate from doing)

* Network payloads, conversation screen (history, text box, quick buttons: Follow, Wait, Gather...,
  Cancel, Inventory, Recruit/Dismiss), `@Name message` in chat, party HUD, key bindings
  (talk to looked-at/selected companion, cycle selection).
* `DialogueDirector`: replies produced **after** the order outcome is known; scripted line banks per
  archetype with anti-repetition; small talk topics; task progress/completion events.
* Conversation memory per companion (recent lines + salient events), saved with the world.

**Exit:** each archetype answers each situation in its own voice (JUnit over line banks: every key
present, no placeholder errors); interrupted conversations handled (screen closed, new message while
pending, companion busy/downed); manual in-game run.

## Phase 4 - Believable adventuring

* Combat behaviour per stance: defend the owner, assist on the owner's target, retreat at the
  personality threshold, avoid creepers, equip best weapon/armour from inventory.
* "Essential" companions: at zero health they are *downed* (kneel, invulnerable, recover) instead
  of dying - configurable.
* Danger reactions: avoid lava/fire/cactus/drops, flee explosions, eat carried food, call out hostiles
  behind the player, low player health, lava, chests, ores (per archetype interests).
* Ambient comments with cooldowns, suppressed in combat and conversations.
* Light the way: place carried torches in dark areas while following.

**Exit:** GameTests for defend/assist, retreat threshold, downed/recover, catch-up teleport safety;
manual cave + night + Nether run.

## Phase 5 - Optional AI dialogue

* Config file + secrets handling, provider clients (Ollama, OpenAI-compatible, Anthropic), async
  with timeouts, circuit breaker.
* Prompt builder (character sheet, memory, world state, recent conversation), JSON contract, intent
  validation, commitment guard, anti-repetition check, prompt-injection hygiene.
* Scripted fallback on any failure.

**Exit:** JUnit with a local fake HTTP server for every provider (success, malformed JSON, timeout,
refusal, impossible intent); manual run with a real local model if available.

## Phase 6 - Polish and portfolio

* UI polish (Minecraft-style screens, sounds, translations file), config screen if time permits.
* README with concept, features, install, configuration, architecture diagram, limitations,
  credits, licence; screenshots from client game tests; short demo GIF if possible.
* Release jar, changelog, clean git history.

---

## Scope boundaries (deliberately *not* in v1)

* Multiplayer support beyond "it shouldn't crash" (single-player is the target).
* Building, crafting, farming automation, tunnelling, bridging, or parkour.
* Ranged combat (bows) - melee only in v1.
* Voice input/output.
* Companion-to-companion conversations (they react to the same events, but don't chat with each
  other).

## Testing strategy (summary)

| Layer | Tool | Runs |
|---|---|---|
| Parser, validator, task state machines, dialogue selection, prompt/JSON handling | JUnit 5 (`./gradlew test`) | Every build |
| Entity behaviour in a real server world | Fabric server GameTests (`./gradlew runGameTest`) | Every build |
| Rendering, UI, screenshots | Fabric client game tests (`./gradlew runClientGameTest`) | On demand (opens a window) |
| Feel: navigation, combat, conversation | Manual dev-client sessions | Each phase |

Detailed results, including what is verified and what still needs testing, are tracked in
[TESTING.md](TESTING.md).
