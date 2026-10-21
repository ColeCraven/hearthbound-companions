# Personalities

Each companion is built from one of six archetypes, a name and a look: either one of the twelve
named [cast](CAST.md) characters with their own outfit, or a wanderer in one of the 18 default
skins. Two companions of the same archetype share a voice and a way of helping, but not a name,
look or memories.

I didn't want the archetype to be a coat of paint over the same follower. It changes:

1. How they talk: vocabulary, sentence length, humour, what they bring up unprompted. The scripted
   line banks and the AI character sheet both use this.
2. What they care about: interests and dislikes that decide which world events they comment on
   (ores, mobs, biomes, loot, weather, your health...).
3. How they help: plain numbers the behaviour code reads, like follow distance, combat stance,
   engage radius, retreat threshold and chattiness, plus a few hard refusals.

| | Sellsword | Naturalist | Rogue | Guardian | Farmhand | Mystic |
|---|---|---|---|---|---|---|
| Cast ([see them](CAST.md)) | Charlie, Dasha | Hazel, Birintan | Maddie, AJ | Daisy, Cole | Samuel, Honey | Luna, Luke |
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
| Hard refusals | - | Won't strip-mine rare plants | - | Won't attack villagers, golems or pets | Won't attack farm animals | - |

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
banter with each other, and react to gifts according to their tastes. Suggestions are only ever
words. Apart from these small, harmless pastimes, companions don't do anything you didn't ask for.

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
