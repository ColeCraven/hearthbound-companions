# Command and action guide

This is the reference for everything you can say to a companion and everything they can do about
it. I've split it into what works today ("Implemented") and what I haven't built yet ("Planned"),
so nothing here is a promise I can't keep.

Everything works in normal chat. The `/companion` commands are the same actions for people who
prefer a keyboard, plus a few settings.

## Who hears you

| You type | Who it reaches |
|---|---|
| `@Hazel follow me` | Hazel only. Any loaded companion in your dimension, however far away. |
| `Hazel, follow me` | The same, with a comma instead of an @. |
| `hey @Hazel, how are you?` | The same, with the name in the middle of the sentence. |
| `@party follow me` (or `@all`, `@team`, `@companions`) | Everyone travelling with you: the ones following you, plus any within 64 blocks. |
| `@everyone come to me` | Every companion you have, wherever they are. |
| `/companion tell Hazel hello` | The same as typing `@Hazel hello`. Also takes `party` and `everyone`. |
| `follow me` (no name) | The companion you're already talking to. See below. |

A typo in a name is forgiven when only one name fits (`@hazle`) and no player has that name.

**Plain chat.** Once you've talked to someone, you can carry on without the @ for three minutes,
as long as they're within 64 blocks. They also pick up your plain chat if you're alone and:

1. you're looking at one of them (up to 24 blocks away), or
2. you have none in view, so it goes to your nearest companion within 64 blocks, or
3. you're standing right beside a stranger (within 8 blocks).

With other players nearby I don't want to hijack your chat with friends, so plain chat only
reaches a companion you're facing. Saying bye, walking off or going quiet ends the conversation.

**Reports.** When you use `@party` or `@everyone` you get one short grey line saying who heard it,
and another for anyone who couldn't:

- *another dimension*: they can't hear you from here. Tell them when you're back together.
- *not loaded, too far away*: their part of the world isn't loaded, so they're frozen until you
  go near. Messages and orders can't reach a companion in an unloaded chunk, so nothing is queued.
  Go back to them and ask again.

**Other people's companions.** Only the player a companion travels with can give them orders.
Everyone else gets a polite no. Strangers (companions who haven't joined anyone) will talk to you,
and will offer to join you if you ask them to do a job (see "Strangers" below).

## Implemented: orders

These all work in chat, with or without the @name, and mostly work as `/companion` commands too.

| Order | Say something like | Needs | Notes |
|---|---|---|---|
| Join | `@Hazel join me` | A free party slot (3 by default) | `/companion recruit Hazel` |
| Leave | `@Hazel you're dismissed` | | `/companion dismiss Hazel` |
| Follow | `follow me`, `come with me`, `let's go` | | `/companion follow Hazel` |
| Wait | `wait here`, `stay`, `hold on`, `guard this place` | | They stay at that spot. |
| Come | `come here`, `get over here` | | They walk over, or catch up if the way is blocked. |
| Stop | `stop`, `never mind`, `cancel that` | | Drops the current job. If they were only following, they wait. |
| Gather | `get me 16 oak logs`, `collect some stone`, `I need wood`, `dig for coal` | The right tool in their pack for hard blocks | Natural blocks only, up to 256. They tell you if there's none nearby or they lack a tool. |
| Collect what you point at | `collect that`, `break that block`, `get more of this` | | "That" is the block under your crosshair, or what they were last collecting. |
| Fight | `fight that zombie`, `kill the nearest creeper`, `go and kill the spiders` | | See "Fighting" below. |
| Hunt | `hunt some pigs`, `hunt animals` | | Animals only count if they're not tamed. |
| Dig | `go mining`, `dig a tunnel`, `dig down 20` | A pickaxe | Stops before lava, water or a big drop. Puts torches up if they carry some. |
| Build a shelter | `build a house`, `build a shelter` | About 70 building blocks | A 5x5 hut with a doorway, roof, and a door and torch if they have them. |
| Build a wall | `build a wall` | About 20 building blocks | 7 wide and 3 high, in front of you. |
| Build a bridge | `build a bridge`, `bridge this gap` | Building blocks | Across water or a gap ahead of you, up to 32 blocks. |
| Make camp | `set up camp`, `make a campfire` | A campfire and 3 building blocks | A campfire with three seats around it. |
| Hand things over | `give me everything`, `give me the wood`, `drop the logs` | | `/companion give Hazel` |
| Open their pack | `show me your inventory` | | Or sneak and right-click them. |
| Status | `what are you doing?`, `progress` | | `/companion status Hazel` |

**Anyone can do any job.** Nobody refuses something because of their personality. They sound
different doing it, and their role (below) decides who's quickest. The only refusals are ethical
ones: they won't attack you, other companions, players, villagers, iron golems or tamed pets.

**"This" and "that".** If you say `collect that`, they look at the block you're looking at. If
you say `fight that`, they go for the creature under your crosshair, or the nearest hostile mob.
If you say `build this` or `build something`, they can't know what you mean, so they ask one short
question ("A shelter, a wall, a bridge or a camp?") and use your next message as the answer. The same
goes for `gather` with no item ("Gather what?") and `attack` with nothing in sight ("Attack what?").
You can ignore the question and say something else, and they'll treat it as a new message.

**Strangers.** If you ask a companion who hasn't joined anyone to build, gather, dig or fight,
they offer to join you first. Say yes and they join (if you have room) and start the job. Say no
and they drop it.

**Far away.** If you ask for a gather, dig or build and they're more than 24 blocks from you, they
say they're on their way, walk over (or catch up by teleport if they're stuck or very far), and
start the job when they arrive.

**One job at a time.** A new job replaces the old one, which is properly cancelled. `stop` ends
the current job and says what it was.

**Honest results.** They only say a job is done when it is. If they can't do it, they tell you why
in their own voice: no tool, not enough blocks, the ground is uneven, there's lava, the area is
protected, nothing of that kind nearby.

## Implemented: roles

Every companion has a role next to their personality. The personality is how they talk. The role is
what they're good at. On a server, unmodded players see the role as the grey line under a
stranger's name. Once someone has joined a player, that line says who they're with instead.

| Role | Good at | Effect | Slower at |
|---|---|---|---|
| Guardian | Fighting, defending, hunting | 40% more damage, 4 extra health | Farming |
| Builder | Building, digging, tunnels | 40% faster placing and digging | Fighting |
| Collector | Gathering, mining ores, scouting | 40% faster at gathering | Building |
| Farmer | Crops, animals, food | 40% faster harvesting crops (when autonomy is on) | Digging |

Nobody is ever blocked from a job. The "slower" ones work about 15% slower, and now and then a
companion will say whether a job suits them. The cast roles are in [CAST.md](CAST.md). Wanderers
without a cast identity get a role from their personality.

## Implemented: settings you can change in game

| Command | What it does |
|---|---|
| `/companion chattiness` | Shows the current level. |
| `/companion chattiness off` | No unprompted chatter. They still answer you, confirm orders, and warn you about danger. |
| `/companion chattiness low` | Only now and then. Replies, confirmations and warnings are unchanged. |
| `/companion chattiness normal` | The default. |
| `/companion chattiness high` | Plenty of comments, and more chats between them. |
| `/companion chattiness 0.7` | Any number from 0 to 2 (saved as `ambientChatter`). |
| `/companion autonomy on` | Idle companions pick sensible jobs of their own (below). Off by default. |
| `/companion autonomy off` | Back to only doing what you ask. |

In single-player you can always use these. On a server they need operator rights. Both are saved
to `config/hearthbound.json`.

**Autonomy.** With it on, while a companion is following you and you stand still for a bit, they may
pick a job that suits their role (it is one of the things they might do with their free time, so it
doesn't happen constantly):

- **Collectors** chop a tree log or pick up exposed surface stone.
- **Farmers** harvest a ripe crop and replant it if they have a seed.
- **Guardians** go after a hostile mob close to you.
- **Builders** put a torch in a dark spot nearby, if they carry torches.

The boundaries: your orders always come first (moving off or giving any order stops it
immediately), no more than ten blocks or torches a minute each, nothing within a protected area
or beside things players built (planks, doors, glass, chests and so on, except ripe crops), nothing
taken from chests or storage, and nothing if their pack is nearly full.

## Implemented: conversation

- **Follow-ups work.** "Why?", "really?", "and you?", "tell me more" and "what do you mean?" all
  lean on what you were just talking about.
- **They pick up what you mention.** Say you saw a creeper, or that you found some iron, and the
  reply is about that.
- **They remember you.** `call me Captain`, `I love the ocean`, `I hate spiders`, then
  `what do you know about me?` and `are we friends?`.
- **They talk to each other.** When two or more travel with you and you're not mid-chat, they
  sometimes have a short exchange (two to four lines) about the weather, the road, food or each
  other, and the same happens when hub residents meet. These are only spoken lines. Nothing a
  companion says is ever read as an order. Chattiness controls how often.
- **Ambient lines wait for you.** Comments, suggestions and questions hold off while you're
  chatting. Warnings about danger still come straight through.

## Implemented: other commands

| Command | What it does |
|---|---|
| `/companion help` | Lists what you can say and do. |
| `/companion list` | Your party, what each is doing, and their health. |
| `/companion say Hazel hello` | Sends a chat message to one companion. |
| `/companion skin Hazel noor slim` | Changes outfit (a default skin, or a cast outfit like `daisy`). |
| `/companion summon hazel` | Spawns a cast member or personality (needs cheats). Old names still work. |
| `/companion hub list`, `spawn`, `home Name` | Hub tools for servers (operators). |

Keys: <kbd>G</kbd> opens chat with the name of the companion you're looking at, <kbd>H</kbd>
toggles the party list.

## Limits you should know about

- A companion in an unloaded chunk can't hear you or act. That's how Minecraft works, and I
  don't fake it. Go back near them.
- If you teleport very far away, they catch up once their chunk loads.
- Building uses blocks from their pack and only places into empty space. It won't replace
  anything that's already there.
- They fight in melee only.
- English only.

## Planned

These are things I want but haven't built, so please don't expect them yet:

- Asking for torches or plants on demand ("light up this cave", "plant these seeds"). Builders
  place torches by themselves with autonomy on, but there's no order for it yet.
- A proper farming order ("start a farm"). Farmers harvest and replant what's already there.
- Counting what they carry in a chat reply ("how many logs do you have?").
- Bows and ranged fighting.
- Showing the role under the name on modded clients. For now it's the grey line on servers.
- A conversation screen with your history.
- Acting while their chunk is unloaded.
