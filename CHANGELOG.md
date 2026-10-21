# Changelog

## 0.3.0 - real work, and companions who get to know you (2026-10-21)

- Talk without @ once you're chatting: name a companion once (or right-click them) and keep typing.
  The conversation ends when you say bye, walk off or go quiet for a minute. With other players
  around, plain chat only goes to a companion you're facing, so chat between friends is left alone.
  On your own, plain chat goes to the companion you're looking at, or the nearest one.
- `@name` forgives short forms and typos (`@ros`, `@rsoie` for Rosie) when only one name fits and
  no player has that name.
- They remember what you tell them: "call me Captain", "I love the ocean", "I hate spiders". The
  nickname is used from then on, likes and dislikes come up when they turn up in the world, and
  "what do you know about me?" gets it all said back. Saved per player with the companion.
- In quiet moments a companion asks you something (what to call you, your favourite thing, what
  you can't stand, how you're doing) and reads your reply as the answer. Orders still go through.
- "Are we friends?" gets an honest answer based on how you've treated them.
- Reactions to your advancements, level milestones, finding diamonds, going to bed, waking up,
  dying and coming back. Big moments go into their memory.
- Crouch twice at a companion and they crouch back. They wave hello and goodbye, hop when
  you make them laugh, and look at you while you're talking.
- A hungry player gets handed food if the companion carries some, and friends sometimes give you
  a flower they picked.
- New orders: dig (a staircase down, a tunnel ahead, or a full mining trip that collects ores and
  places torches, stopping before lava, water and drops) and build (a shelter, a wall, or a bridge
  over water or a gap, from blocks in their pack, with a door and torch for the shelter).
- `/companion help` (or just `/companion`) lists everything you can say and do.
- Ten more things to talk about: their past, the place you're in, what to do next, yes and no,
  laughing, kind words, bad days, food, and any other question. About a thousand new lines across the
  six personalities.
- The line under a companion's name on servers is now short and grey ("Sellsword", "with Tekless").
- Fixed: memories read as "Remember joined you on the road?"; articles in filled-in lines ("a
  apple", "a enderman"); a sentence starting with a filled-in word wasn't capitalised.

## 0.2.0 - server support (2026-10-21)

- Runs on a dedicated server with no client mod needed: with Polymer installed, companions are sent
  to unmodded players as mannequins with their skin, name and a status line.
- Hub residents: the cast can live at a configured spawn hub, spawn there once, greet visitors and
  return there when dismissed. `/companion hub list|spawn|home`.
- Multiplayer rules: only the owner gives orders ("I'm with ..." lines for everyone else),
  strangers can't hurt companions, companions never target players or others' pets, replies reach
  the owner, the speaker and anyone nearby, no gathering in protected areas or next to builds, no
  looting other players' items, `@name` works anywhere in a message.
- Per-speaker conversation memory, a per-player AI rate limit and a cap on concurrent AI requests.
- A world roster and loaded-companion tracker replace large entity scans.
- Redrawn skins for Cass, Conoby, Samuel and Casper; travel outfits for Rosie and Luna.
- Signed cast skins for unmodded players: `tools/skins/mineskin_upload.py` makes
  `config/hearthbound-skins.json` (notes starting with `_` are ignored).
- Fixed: `/companion summon <name>` gave the gear of a random personality; hub residents could be
  placed on the hub's centre block (e.g. a campfire) when their side of the ring was blocked.

## 0.1.0 - preview (2026-10-20)

First public preview for Minecraft Java 26.3 (Fabric).

- Companions with the 18 default player looks (9 skins x wide/slim), rendered with the vanilla
  player model and the game's own textures.
- Six archetypes (Sellsword, Naturalist, Rogue, Guardian, Farmhand, Mystic) that differ in voice,
  interests and combat behaviour, each with ~90 situations of scripted dialogue.
- Orders: recruit, dismiss, follow, wait, come, gather, give, attack, cancel, status, open pack.
- Talk in normal chat (`@Name ...`, `Name, ...`, `@all ...`), the Talk key (G), or `/companion`.
- Follow with personal distance, hurry, and safe catch-up teleport (also through dimensions).
- Gathering of natural blocks with tool rules and honest progress/failure reports.
- Essential companions (knocked down instead of killed), warnings, ambient comments, party HUD.
- Saves identity, orders, inventory, conversation memory and affinity with the world.
- Optional AI dialogue via Ollama, OpenAI-compatible APIs or Anthropic; the model can only propose
  intents that the game validates.
