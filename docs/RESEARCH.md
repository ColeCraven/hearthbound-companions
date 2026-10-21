# Research notes (October 2026)

Summary of the research behind Hearthbound Companions. Decisions that came out of it are in
[TECH_DECISIONS.md](TECH_DECISIONS.md) and [AI_DESIGN.md](AI_DESIGN.md).

## 1. Versions and loaders

* **Minecraft Java 26.3** (released 2026-09-15) is the latest release; 26.4 is in weekly
  snapshots. Java 25 is required. Since 26.1 the game ships **unobfuscated**, so Fabric dropped
  Yarn/Intermediary mappings and remapping for 26.x
  ([Fabric: obfuscation removal](https://fabricmc.net/2025/10/31/obfuscation.html),
  [Fabric for 26.1](https://fabricmc.net/2026/03/14/261.html)).
* **Fabric** for 26.3: Loader 0.19.5 (stable), Fabric API 0.164.0+26.3, plugin
  `net.fabricmc.fabric-loom`; the official example mod uses Loom 1.18 and Gradle 9.7.1
  ([Fabric 26.3 post](https://fabricmc.net/2026/09/15/263.html),
  [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod/tree/26.3)).
  Notable 26.3 changes for modders: SDL replaced GLFW for input; several Fabric registries
  (fuel, compost, strippable...) became vanilla data components or data-driven files.
* **NeoForge** for 26.3: stable since 26.3.0.76 (2026-10-11); latest 26.3.0.95; ModDevGradle
  2.0.148. No official client-side automated test API comparable to Fabric's.
* **Testing**: Fabric's `fabric-gametest-api-v1` (server GameTests, run by `./gradlew build`)
  and `fabric-client-gametest-api-v1` (drives the real client: simulated input, screenshots).
  On Windows client tests open a real window; headless only works on Linux with Xvfb
  ([automated testing docs](https://docs.fabricmc.net/develop/automatic-testing)).

## 2. Existing companion and follower mods

| Mod | 26.3 Fabric? | What to learn | What to avoid |
|---|---|---|---|
| [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | No (26.2 latest) | Rich villager relationships; optional GPT chat | Pathing failures indoors/at fences ([#1088](https://github.com/Luke100000/minecraft-comes-alive/issues/1088)) |
| [CreatureChat](https://github.com/CreatureChat/creature-chat) | No (1.21.7) | Chat with any mob; behaviour tags in replies | Friendship set directly by the model (one reply can jump from hostile to max, unlocking rewards); entity changes from a network callback thread |
| Human Companions, Villager Recruits, Touhou Little Maid | No | Recruit/command UX, stances, inventory screens | Companions vanishing in unloaded chunks/boats; permanent aggro after an accidental hit; item duplication on death |
| [PlayerEngine / Player2 NPC](https://github.com/shakey2/PlayerEngine) | No (1.21.1) | Text commands mapped to tasks (`@get diamond 5`) | Heavy dependencies |
| [Mindcraft](https://github.com/mindcraft-bots/mindcraft) | n/a (bot, not a mod) | 47 typed commands, argument validation, error messages fed back | Model claims success it didn't achieve ([#808](https://github.com/mindcraft-bots/mindcraft/issues/808): claimed 9/9, actually 1); high cloud costs |
| [Voyager](https://arxiv.org/abs/2305.16291) | n/a (research) | Skill libraries, environment feedback | ~$50 per 160 GPT-4 iterations; code generation is unsafe for a mod |
| Baritone | Client-only | Excellent pathfinding | Drives the *player*, can't drive NPC entities |

The 26.3 Fabric niche is open: no established companion mod has a 26.3 Fabric build yet. A few
small LLM chat mods exist (Smart Villagers AI, CraftyAI, AI Friend), but none offer Elder
Scrolls-style followers with tasks.

## 3. What makes a good follower

I went back over the RPG companions I've liked (and the ones that drove me up the wall) and wrote
down what they have in common.

The basic commands are always the same: follow, wait here, trade, go home, and "do something" while
you point at a target. The good ones can't die for real; they get knocked down instead, so you
don't lose someone you've put time into. They also need to keep up: speed up when they fall
behind, and teleport to you when they're properly lost.

The usual complaints are followers blocking doorways, walking into traps, falling behind, getting
lost and saying the same lines over and over. What fixes most of that is less chatter overall and
comments that fit the situation. The companions people remember have a consistent personality,
comment on places and events, and the relationship changes over time, ideally in response to what
you actually do, with some feedback so you can tell.

## 4. AI approaches

* **What works:** map model output onto a *fixed, typed set of actions* that hand-written code
  executes (CreatureChat tags, Mindcraft commands, Touhou Little Maid function calls).
* **What fails:** letting the model judge completion (hallucinated success), unbounded retries
  (loops), slow replies (~7 s hurts believability), small local models breaking free-form
  action syntax - structured outputs with a JSON schema fix the last one.
* **Persona consistency:** stable character sheet first, a short rolling memory summary plus
  salient events ([Generative Agents](https://arxiv.org/abs/2304.03442)), compact world state,
  last few lines of chat; persona drift appears within ~8 turns without reminders
  ([Li et al.](https://arxiv.org/abs/2402.10962)).
* **Prompt injection:** signs, books, item/entity names and other players' chat are untrusted
  input ([OWASP LLM01](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)). Delimit them
  ("spotlighting", [Hines et al.](https://arxiv.org/abs/2403.14720)), strip formatting codes, cap
  length - and give the model no privileges.
* **Providers:** Ollama (local, JSON-schema `format`), OpenAI-compatible (`response_format`
  JSON schema), Anthropic (`output_config.format`). Details and costs in
  [AI_DESIGN.md](AI_DESIGN.md).

## 5. Lessons adopted

1. The model only talks and *proposes* an intent; Java validates and executes. No code generation.
2. Core commands (follow, wait, come, cancel, dismiss) never need the model: buttons, keybinds,
   commands and a rule-based parser handle them instantly.
3. Completion is decided by checking world state in code, never by the model's claim.
4. Every task has a timeout, a retry cap and a no-progress check, and reports "impossible" quickly
   with a concrete reason.
5. Two-step catch-up: speed up when far, teleport (to a safe spot) when much farther, in another
   dimension, or about to be left in an unloaded chunk.
6. Stay out of the way: personal follow distance, step aside, don't block the player.
7. Combat: downed instead of dead by default, retreat thresholds, no permanent grudges, never
   target the owner, don't steal the player's loot.
8. Persistence from day one, with a world-level registry so companions can't be lost, and a
   data version in saved data.
9. Performance: no network or heavy work on the game thread; throttled scans.
10. Comments are context-triggered with cooldowns and a frequency setting; scripted lines are the
    fallback.
11. Personality is data that drives barks, prompts, and behaviour numbers alike.
12. Affinity changes only from observed actions, capped per event; chat can never grant game
    rewards.
