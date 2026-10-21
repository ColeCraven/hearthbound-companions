# Personalities

Each companion is built from one of six **archetypes**, a name, and one of the 18 default skins.
Two companions of the same archetype share a voice and a way of helping, but not a name, skin or
memories.

An archetype is **not just flavour text**. It changes three separate things:

1. **How they talk** - vocabulary, sentence length, humour, what they bring up unprompted.
   Used by both the scripted line banks and the AI character sheet.
2. **What they care about** - interests and dislikes that decide which world events they comment
   on (ores, mobs, biomes, loot, weather, the player's health...).
3. **How they help** - deterministic numbers the behaviour code reads: follow distance, combat
   stance, engage radius, retreat threshold, chattiness, and a few hard refusals.

| | Sellsword | Naturalist | Rogue | Guardian | Farmhand | Mystic |
|---|---|---|---|---|---|---|
| **Example names** | Bram, Hilde, Torvald | Wren, Elowen, Tobias | Marlo, Cass, Jory | Isolde, Aldric, Seren | Pip, Tilly, Odo | Sable, Morwen, Ashe |
| **Voice** | Terse, blunt, soldier's slang | Long, excited sentences, asks questions | Quick quips, teasing, "partner" | Formal, sincere, old-fashioned courtesy | Rambling, nervous, homely sayings | Dry, cryptic, short poetic lines |
| **Humour** | Deadpan understatement | Puns and trivia | Sarcasm and teasing | Earnest, gentle | Self-deprecating, accidental | Dark, gallows humour |
| **Interests** | Weapons, armour, a good fight | Biomes, plants, animals, rocks | Emeralds, loot, chests, trades | Villagers, oaths, keeping you alive | Crops, animals, cooking, home | The Nether, the End, enchanting, night |
| **Dislikes** | Cowardice, creepers | Needless destruction | Being broke, rules | Cruelty, undead | The dark, caves, creepers | Crowds, daylight chatter |
| **Combat stance** | Aggressive | Defensive | Opportunist | Protective | Cautious | Aggressive at night, defensive by day |
| **Engage radius** | 12 blocks | only when attacked | 8 blocks (targets threatening you) | 10 blocks (targets threatening you) | only when you or they are hit | 12 at night / 6 by day |
| **Retreats at** | 20% health | 50% | 40% | 25% | 60% | 35% |
| **Follow distance** | 3-4 (escort) | 4-6 (wanders to look) | 3-5 | 2-3 (closest) | 2-4, tighter at night | 5-7 (aloof) |
| **Calls out** | Hostiles, gear | Ores, rare biomes, structures | Chests, valuables | Your low health, threats to villagers | Hostiles early (jumpy) | Lava, sculk, endermen |
| **Chattiness** | Low | High | Medium-high | Medium | High when scared | Low |
| **Hard refusals** | - | Won't strip-mine rare plants | - | Won't attack villagers, golems or pets | Won't attack farm animals | - |

## Avoiding repetition

* Every situation (acknowledging an order, a refusal reason, spotting an ore, nightfall...) has a
  pool of lines **per archetype**, many of them assembled from opener + body + closer fragments.
* Each companion remembers the last lines it used per situation and won't reuse them until the
  pool is exhausted.
* Ambient comments have cooldowns per topic and a global "don't talk over yourself" timer, and
  they are suppressed during conversations and combat.
* When an AI model is used, the character sheet includes style examples, a list of phrases the
  archetype would never say, and the last few things the companion said, with an instruction not
  to repeat them.

## Relationship

Each companion tracks a simple **affinity** score with its owner (-100..100), moved by events the
game can verify: being healed or given gifts, fighting side by side, being sent into lava, being
ignored for long periods, being hit by the player. Affinity shifts tone (warmer or cooler lines)
and is included in the AI prompt, but it never blocks basic orders - companions are reliable.
