# Research notes

Before I started on this in October 2026, I spent a while looking around: which version and loader
to build on, what companion mods already exist and where they fall over, what makes a follower
fun to travel with, and how people hook language models up to games. These are my notes from that.
What I actually decided is in [TECH_DECISIONS.md](TECH_DECISIONS.md) and
[AI_DESIGN.md](AI_DESIGN.md).

## Versions and loaders

Minecraft Java 26.3 (released 2026-09-15) is the latest release, and 26.4 is in weekly snapshots.
It needs Java 25. Since 26.1 the game ships unobfuscated, so Fabric dropped Yarn/Intermediary
mappings and remapping for 26.x
([Fabric: obfuscation removal](https://fabricmc.net/2025/10/31/obfuscation.html),
[Fabric for 26.1](https://fabricmc.net/2026/03/14/261.html)).

Fabric for 26.3 is Loader 0.19.5 (stable) and Fabric API 0.164.0+26.3, with the
`net.fabricmc.fabric-loom` plugin. The official example mod uses Loom 1.18 and Gradle 9.7.1
([Fabric 26.3 post](https://fabricmc.net/2026/09/15/263.html),
[fabric-example-mod](https://github.com/FabricMC/fabric-example-mod/tree/26.3)). Two 26.3 changes
to keep in mind: SDL replaced GLFW for input, and several Fabric registries (fuel, compost,
strippable...) became vanilla data components or data-driven files.

NeoForge has been stable on 26.3 since 26.3.0.76 (2026-10-11). The latest is 26.3.0.95, with
ModDevGradle 2.0.148. I couldn't find an official client-side test API like Fabric's.

For testing, Fabric has `fabric-gametest-api-v1` (server GameTests, run by `./gradlew build`) and
`fabric-client-gametest-api-v1`, which drives the real client with simulated input and
screenshots. On Windows the client tests open a real window; headless only works on Linux with Xvfb
([automated testing docs](https://docs.fabricmc.net/develop/automatic-testing)).

## Companion mods that already exist

| Mod | 26.3 Fabric? | What I liked | Where it falls over |
|---|---|---|---|
| [MCA Reborn](https://modrinth.com/mod/minecraft-comes-alive-reborn) | No (26.2 latest) | Rich villager relationships; optional GPT chat | Pathing failures indoors/at fences ([#1088](https://github.com/Luke100000/minecraft-comes-alive/issues/1088)) |
| [CreatureChat](https://github.com/CreatureChat/creature-chat) | No (1.21.7) | Chat with any mob; behaviour tags in replies | Friendship set directly by the model (one reply can jump from hostile to max, unlocking rewards); entity changes from a network callback thread |
| Human Companions, Villager Recruits, Touhou Little Maid | No | Recruit/command UX, stances, inventory screens | Companions vanishing in unloaded chunks/boats; permanent aggro after an accidental hit; item duplication on death |
| [PlayerEngine / Player2 NPC](https://github.com/shakey2/PlayerEngine) | No (1.21.1) | Text commands mapped to tasks (`@get diamond 5`) | Heavy dependencies |
| [Mindcraft](https://github.com/mindcraft-bots/mindcraft) | n/a (bot, not a mod) | 47 typed commands, argument validation, error messages fed back | Model claims success it didn't achieve ([#808](https://github.com/mindcraft-bots/mindcraft/issues/808): claimed 9/9, actually 1); high cloud costs |
| [Voyager](https://arxiv.org/abs/2305.16291) | n/a (research) | Skill libraries, environment feedback | ~$50 per 160 GPT-4 iterations; code generation is unsafe for a mod |
| Baritone | Client-only | Excellent pathfinding | Drives the *player*, can't drive NPC entities |

That Mindcraft issue is exactly the kind of follower I didn't want to make.

None of the established companion mods had a 26.3 Fabric build yet. There are a few small LLM chat
mods (Smart Villagers AI, CraftyAI, AI Friend), but none of them do proper RPG-style followers
with tasks.

## What makes a good follower

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

## Hooking up language models

What works is mapping the model's output onto a fixed, typed set of actions that hand-written code
runs (CreatureChat tags, Mindcraft commands, Touhou Little Maid function calls).

What doesn't: letting the model decide when something is done (it'll happily claim success),
unbounded retries (loops), slow replies (~7 s already hurts believability), and small local models
breaking free-form action syntax. Structured outputs with a JSON schema fix that last one.

To keep a persona consistent: stable character sheet first, a short rolling memory summary plus
salient events ([Generative Agents](https://arxiv.org/abs/2304.03442)), compact world state, and
the last few lines of chat. Without reminders, persona drift shows up within ~8 turns
([Li et al.](https://arxiv.org/abs/2402.10962)).

Signs, books, item/entity names and other players' chat are all untrusted input
([OWASP LLM01](https://genai.owasp.org/llmrisk/llm01-prompt-injection/)). Delimit them
("spotlighting", [Hines et al.](https://arxiv.org/abs/2403.14720)), strip formatting codes, cap the
length, and give the model no privileges.

For providers: Ollama (local, JSON-schema `format`), OpenAI-compatible (`response_format` JSON
schema), Anthropic (`output_config.format`). Details and costs are in [AI_DESIGN.md](AI_DESIGN.md).

## What I took from all this

The model only talks and *proposes* an intent. Java validates it and runs it, and there's no code
generation. The core commands (follow, wait, come, cancel, dismiss) never need the model at all:
buttons, keybinds, commands and a rule-based parser handle them instantly. Whether something is
done gets decided by checking the world in code, never by what the model claims.

Every task has a timeout, a retry cap and a no-progress check, and gives up quickly with a concrete
reason when something's impossible. Catch-up happens in two steps: speed up when far behind,
teleport (to a safe spot) when much farther, in another dimension, or about to be left in an
unloaded chunk. They also keep a personal follow distance, step aside, and don't block the player.

In combat they're downed instead of dead by default, they retreat at a threshold, they don't hold
permanent grudges, they never target the owner, and they don't steal the player's loot.

Saving was in from day one, with a world-level registry so companions can't get lost, and a data
version in the saved data. Nothing touches the network or does heavy work on the game thread, and
scans are throttled.

Comments are triggered by what's going on, with cooldowns and a frequency setting, and scripted
lines are the fallback. Personality is data that drives barks, prompts and behaviour numbers
alike. Affinity only changes from actions the game observes, capped per event, and chat can never
grant game rewards.
