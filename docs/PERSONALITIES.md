# Personalities

Each companion is built from a voice (one of six archetypes), a role, a name and a look: either one
of the twelve named [cast](CAST.md) characters with their own outfit, or a wanderer in one of the
18 default skins. Two companions with the same voice share a way of talking and a way of acting in
a fight, but not a name, look or memories.

The voice is how they talk. The role is what they're good at: see "Roles" below.

I didn't want the archetype to be a coat of paint over the same follower. It changes:

1. How they talk: vocabulary, sentence length, humour, what they bring up unprompted. The scripted
   line banks and the AI character sheet both use this.
2. What they care about: interests and dislikes that decide which world events they comment on
   (ores, mobs, biomes, loot, weather, your health...).
3. How they help: plain numbers the behaviour code reads, like follow distance, combat stance,
   engage radius, retreat threshold and chattiness, plus a few hard refusals.

| | Sellsword | Naturalist | Rogue | Guardian | Farmhand | Mystic |
|---|---|---|---|---|---|---|
| Cast ([see them](CAST.md)) | Charlie | Hazel, Birintan | Maddie, AJ, Cole | Dasha, Daisy | Samuel, Honey | Luna, Luke |
| Wanderer names | Torvald, Greta, Osric | Elowen, Linnea, Ivy | Jory, Lark, Vesper | Aveline, Gareth, Tristan | Odo, Bess, Clem | Nyx, Thorne, Orrin |
| Voice | Terse, blunt, soldier's slang | Long, excited sentences, asks questions | Quick quips, teasing, "partner" | Formal, sincere, old-fashioned courtesy | Rambling, nervous, homely sayings | Dry, cryptic, short poetic lines |
| Humour | Deadpan understatement | Puns and trivia | Sarcasm and teasing | Earnest, gentle | Self-deprecating, accidental | Dark, gallows humour |
| Interests | Weapons, armour, a good fight | Biomes, plants, animals, rocks | Emeralds, loot, chests, trades | Villagers, oaths, keeping you alive | Crops, animals, cooking, home | The Nether, the End, enchanting, night |
| Dislikes | Cowardice, creepers | Needless destruction | Being broke, rules | Cruelty, undead | The dark, caves, creepers | Crowds, daylight chatter |
| Combat stance | Aggressive | Defensive | Opportunist | Protective | Cautious | Aggressive at night, defensive by day |
| Engage radius | 12 blocks | only when attacked | 8 blocks (targets threatening you) | 10 blocks (targets threatening you) | only when you or they are hit | 12 at night / 6 by day |
| Retreats at | 20% health | 50% | 40% | 25% | 60% | 35% |
| Follow distance | 3-4 (escort) | 4-6 (wanders to look) | 3-5 | 2-3 (closest) | 2-4, tighter at night | 5-7 (aloof) |
| Calls out | Hostiles, gear | Ores, rare biomes, structures | Chests, valuables | Your low health, threats to villagers | Hostiles early (jumpy) | Lava, sculk, endermen |
| Chattiness | Low | High | Medium-high | Medium | High when scared | Low |
| Hard refusals | - | - | - | - | - | - |

Nobody refuses a job because of their personality any more. The one thing every companion
refuses, whatever their voice, is hurting villagers, iron golems, tamed pets, other companions or
players.

## Roles

A role never stops anyone doing a job. It makes some jobs quicker and others a little slower.

| Role | Strong at | Weaker at | What it changes |
|---|---|---|---|
| Guardian | Fighting, defending, hunting | Farming | 40% more damage, 4 extra health |
| Builder | Building, digging, tunnels | Fighting | Places and digs 40% faster |
| Collector | Gathering, mining ores | Building | Gathers 40% faster |
| Farmer | Crops, animals, food | Digging | Harvests crops 40% faster when autonomy is on |

The weaker ones are about 15% slower. Now and then a companion mentions whether a job suits them.
The cast roles are in [CAST.md](CAST.md), and wanderers get a role that fits their voice
(sellsword and guardian: Guardian; naturalist and rogue: Collector; mystic: Builder; farmhand:
Farmer). On servers the role is the grey line under a stranger's name.

## Individual voices

Four cast members have lines of their own on top of their voice
(`data/hearthbound/dialogue/cast/<name>.json`). Their lines are mixed in with the voice's lines, so
they sound like themselves and everything still works if a key is missing.

- **Charlie** is gruff and funny, and loves the herbs he smokes in his pipe. When you stand still
  he picks wild flowers and herbs for it.
- **Cole** is cheeky and clever. He talks like a rogue who's read the whole book.
- **Birintan** is curious and clever, and wonders out loud how everything works.
- **AJ** is the funny one who keeps trying his luck and never succeeds. It's harmless and
  self-deprecating, and he takes every no with a grin.

## Free will: what they do on their own

When you stand still for a few seconds, a following companion may pick a pastime (code:
`entity/Pastimes.java`). Pastimes are real actions picked to suit their personality. A companion
stays within a few blocks of you while doing one, and drops it the moment you move on, give an
order or a fight starts.

| Archetype | Pastimes |
|---|---|
| Sellsword | Walks a guard circuit, practises sword swings, picks up long-abandoned loot |
| Naturalist | Studies flowers, animals, trees and moss up close; picks a wild flower (never from gardens) |
| Rogue | Pockets loot left on the ground (and admits it), sizes up chests and gold, cases the area |
| Guardian | Walks a guard circuit, looking outwards |
| Farmhand | Pets nearby animals (hearts!), picks wild flowers, admires crops and beehives |
| Mystic | Stargazes at night, studies obsidian, amethyst, sculk and lava, prowls the edges |

They also make suggestions (eat, find shelter before dark, hand over some of your full pack),
chat with each other, and react to gifts according to their tastes. Suggestions are only ever
words. Apart from these small, harmless pastimes, companions don't do anything you didn't ask for,
unless you switch on `/companion autonomy on`. Then a pastime can also be a real job that suits
their role (details in [GUIDE.md](GUIDE.md#implemented-settings-you-can-change-in-game)).

## Avoiding repetition

Nothing kills a character faster than hearing the same line five times, so every situation
(acknowledging an order, a refusal reason, spotting an ore, nightfall...) has its own pool of lines
for each archetype. A lot of them are put together from opener + body + closer fragments.

Each companion remembers the last lines it used per situation and won't reuse them until the pool
runs out. Ambient comments have cooldowns per topic and a global "don't talk over yourself" timer,
and they stay quiet during conversations and combat.

When an AI model is hooked up, the character sheet includes style examples, a list of phrases the
archetype would never say, and the last few things the companion said, with an instruction not to
repeat them.

## Relationship

Each companion tracks a simple affinity score with its owner (-100..100). It only moves on things
the game can verify: being healed or given gifts, fighting side by side, being sent into lava,
being ignored for long periods, being hit by the player. Affinity shifts their tone (warmer or
cooler lines) and goes into the AI prompt, but it never blocks basic orders. Even a grumpy
companion still does what you ask.
