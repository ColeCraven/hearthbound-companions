# Changelog

## 0.6.0 - lives of their own (2026-10-22)

The companions you aren't travelling with used to stand around waiting for you. Now they get on with
their lives, in any world, with nothing to set up.

**A day of their own**
- Anyone who lives somewhere (hub residents, companions you dismissed, wanderers) follows the clock: up at
  dawn (one at a time, not all at once), work by role in the morning, a meal at noon, an afternoon of work or
  time off, the evening together at a bell or campfire (lit at dusk, put out in the morning), and bed at night,
  lying down in a bed of their own like villagers do. When it rains they head indoors.
- Personalities change the shape of it. Farmhands are up with the hens, mystics stay up late, rogues take most
  afternoons off. Hunger, tiredness and loneliness steer the day too, and make them more or less chatty.
- Talking to someone in bed gets them up. Any order still comes first, and they go back to their day after.
- `/companion autonomy on` lets their work change the world: farmers harvest and replant, collectors gather,
  builders light dark corners. With it off they still keep their day, they just don't touch anything.

**Works in a brand-new world**
- Places are found on their own near each home: beds (one each), a bell or campfire to meet at, a furnace or
  smoker beside a chest for a kitchen, farmland. They look again every couple of minutes, so a bed you place
  or a farm you till gets noticed.
- With autonomy on, a builder with no roof over home puts up a small shelter (chopping wood for it if they
  have to), and a farmer with seeds starts a plot by the nearest water (making a hoe first if they can).
- When something's missing they say what they need, once a day, and get on with something else.
- `/companion places` shows what a home has and what everyone's up to. `/companion setplace
  <bed|commons|kitchen|farm|storage|patrol> [name|role]` marks the spot you're standing on.
- Servers can list a hub's places in `server.hub.places` (see `docs/examples/skyhaven-places.json`).

**Catching up**
- Each home remembers when someone there was last loaded. When you come back after a while, the time
  that passed is worked out honestly: crops in the farm grow, farmers harvest and replant, and the harvest goes
  into the storage chest and nowhere else. Capped at a week and a few hundred crops.
- You get one short "While you were away" line, and a page goes into a diary on a lectern at the meeting spot,
  if there's an empty one.

**Together**
- Shared meals, stories by the fire after dark, and a festival at every full moon.
- Friendships grow from time spent together, and a few personalities clash. It shows in their chats about each
  other and about the players they know.
- A small project of their own over several days: a flower bed, a flower a day (with autonomy on).
- Now and then someone asks a passing player for a favour ("bring me 16 logs") and pays from their own pack.

**Plain requests for whoever's around**
- "Build a house", "let's build a house", "we need a farm", "can someone get wood", "let's set up camp here",
  with nobody named, reach the companions within about 32 blocks who take your orders. One of them says the plan,
  builders share one blueprint, collectors bring the wood, guardians keep watch, and they report progress and
  what's stopping them. "I built a house yesterday" starts nothing.

**Control**
- Obey mode is for the single-player host and server operators only, and now reaches every companion, including
  ones travelling with other players (they don't change sides). Others are refused.
- `/companion partylimit [player] <number|unlimited>` for the host or operators. 0 or "unlimited" lifts the limit.
- Storehouse chests are remembered after a restart.

## 0.5.0 - talking to groups, obey mode, and big projects that really happen (2026-10-21)

Playtesting showed that "all my builders, ..." was ignored when it was typed without an @, and that
settings only reached your own party. This release fixes both and adds the jobs behind them.

**Groups, with no @**
- "Everyone", "everybody", "guys", "team", "you all" and "all of you" reach everyone within 48 blocks
  plus your party. "All my builders", "farmers", "collectors", "guardians" (also with @) reach that role.
  A role mentioned later in a message ("work with the collectors") is a mention, not the addressee.
- One of them answers for the group, and a grey *Heard by* line says who got it.

**Obey mode**
- `/companion obey on|off` (alias `lead`): every companion in the world takes your orders even if
  you haven't recruited them. They only join if you ask, and go back to their own business afterwards.
  On by default for the host in single-player; on a server it is off, op only, and per operator.
- `/companion chattiness` and `/companion autonomy` now apply to every companion in the world. With
  autonomy on, wanderers and hub residents pick jobs around their own home, with the same limits
  (protected areas, player builds, no chests, a cap per minute).

**Big projects**
- New blueprints: a cottage, and a storehouse with a chest and a furnace. A farm is a plot beside
  water (or around water poured from a bucket).
- Builders pick spaced-out sites, craft planks, sticks, a chest, doors, a furnace, torches and a wooden hoe from what they
  carry, chop wood when short, and say so when they can't. Collectors gather and hand logs over.
  Farmers till, plant, harvest, replant and fill storehouse chests (never a player's chest).
- Both example requests ("all my builders, ... their own house ...", "all my farmers, build a farm
  ... store and cook food") work as typed.

**Homes and fights**
- `/companion sethome <who> [here|x y z]`, `/companion home <who> [teleport]`, `/companion tphome`;
  "go home" walks, "teleport home" or "go home now" teleports. "You can go home now" is still a dismissal.
- Companions fight a named player or companion only when you direct it, never you or their owner, not
  players while PvP is off, and companions stop when one is knocked down.
- `/companion hub list` and `hub home` work from the server console, and `hub home` can respawn a cast
  member who is on record but has no body loaded.

## 0.4.0 - orders that land, roles, and a renamed cast (2026-10-21)

Playtesting showed companions ignoring plain requests and answering things nobody asked, so most of
this release is about making chat reliable and relevant.

**Orders in normal chat**
- Plain chat now goes somewhere sensible. Alone, it reaches the companion you're looking at (up to
  24 blocks), or your nearest party member (up to 64 blocks), or a stranger standing right beside
  you. Before, it was ignored unless someone was within five or six blocks.
- "This" and "that" work. `collect that` collects the block under your crosshair (or what they were
  last collecting), `fight that enemy` goes for what you're looking at, and `get that` works out
  which you meant.
- If a request is too vague (`build this`, `gather`, `attack` with nothing in sight) they ask one
  short question ("A shelter, a wall, a bridge or a camp?") and use your next message as the answer.
- A stranger asked to do a job offers to join you first, and starts the job when you say yes.
- Anyone can do any job now. The only refusals left are not hurting villagers, golems, pets,
  players or other companions.
- More everyday phrasing is understood: `come on over here`, `dig for coal`, `dig that out`,
  `go and kill the spiders`, `hunt some pigs`, `I need a shelter`, `let's build something`.
- Companions further than 24 blocks away walk over (or catch up) before starting a gather, dig or
  build at your spot.
- `set up camp` builds a campfire with three seats, from a campfire and a few blocks in their pack.

**Talking to the right people**
- `@Name` reaches any loaded companion in your dimension, at any distance. Companions in another
  dimension or an unloaded chunk get a clear message instead of silence.
- `@party` (or `@all`) is everyone with you. `@everyone` is every companion you have, wherever they
  are. Both report who heard it and who couldn't.
- `/companion tell <name|party|everyone> <message>`.

**Conversation**
- Follow-ups work: "why?", "really?", "and you?", "tell me more", "what do you mean?".
  After a refusal, "why?" gets the real reason again.
- "What are you good at?" gets an answer about their role, and comments about the weather or the time of
  day get a reply that fits. "What's your favourite food?" is about tastes, not hunger, and "let's go
  home" means come along rather than "you're dismissed".
- They pick up on creatures, items and places you mention.
- A line is never repeated back to back, even in different situations, and comments wait for a
  pause in the conversation. Warnings still come straight through.
- About 500 new lines across the six voices and the cast.
- Short chats between companions (two to four lines about the weather, the road, food or each
  other) when several travel with you, and when hub residents meet. They're never read as orders.

**Roles and a renamed cast**
- Every companion has a role separate from their personality: Guardian, Builder, Collector or
  Farmer. Roles make jobs faster or tougher, never impossible. The role is the grey line under a
  stranger's name on servers.
- Eight cast members were renamed, and some changed role and voice. Old saves, rosters, config files
  and `/companion summon` still find them by their old names. See [CAST.md](docs/CAST.md).
- Charlie, Cole, Birintan and AJ have lines of their own, and Charlie picks herbs for his pipe.

**Settings**
- `/companion chattiness off|low|normal|high`: how much they say unprompted. Replies, confirmations
  and danger warnings always get through.
- `/companion autonomy on|off`: optional self-chosen jobs for idle companions (collectors chop and
  mine surface stone, farmers harvest and replant, guardians hunt nearby hostiles, builders light
  dark spots). Off by default, with caps and no touching protected areas or player builds.

**Docs**
- New [GUIDE.md](docs/GUIDE.md): a reference for every command and action, what needs what, and what
  isn't built yet.

## 0.3.0 - real work, and companions who get to know you (2026-10-21)

- Talk without @ once you're chatting: name a companion once (or right-click them) and keep typing.
  The conversation ends when you say bye, walk off or go quiet for a minute. With other players
  around, plain chat only goes to a companion you're facing, so chat between friends is left alone.
  On your own, plain chat goes to the companion you're looking at, or the nearest one.
- `@name` forgives short forms and typos (`@hon`, `@hoeny` for Honey) when only one name fits and
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
- Redrawn skins for Maddie, Cole, Samuel and Luke; travel outfits for Honey and Luna.
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
