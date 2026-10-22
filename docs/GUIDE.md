# Command and action guide

This is the reference for everything you can say to a companion and everything they can do about
it. I've split it into what works today ("Implemented") and what I haven't built yet ("Planned"),
so nothing here is a promise I can't keep.

**The wording is up to you.** Everything works in normal chat, and the phrases below are only
examples of the kind of thing that works. "Get me some wood", "could you grab 16 logs?" and "we're
out of wood" all land in the same place. Exact syntax only matters for the `/companion` commands,
which are written in `code` with their arguments.

## Who hears you

| You type | Who it reaches |
|---|---|
| `@Hazel follow me` | Hazel only. Any loaded companion in your dimension, however far away. |
| `Hazel, follow me` | The same, with a comma instead of an @. |
| `hey @Hazel, how are you?` | The same, with the name in the middle of the sentence. |
| `@party follow me` (or `@all`, `@team`, `@companions`) | Everyone travelling with you: the ones following you, plus any within 64 blocks. |
| `@everyone come to me` | Every companion you have, wherever they are. |
| everyone, follow me · hey guys · can you all come here | No @ needed. Everyone within 48 blocks, plus your party. |
| all my builders, ... · farmers, ... · @collectors ... | Every companion with that role (builders, farmers, collectors, guardians) in your dimension, nearest first. |
| build a house · we need a farm · can someone get wood? | Nobody named: whoever is within about 32 blocks and takes your orders. See "Plain requests" below. |
| `/companion tell Hazel hello` | The same as typing `@Hazel hello`. Also takes `party` and `everyone`. |
| follow me (no name) | The companion you're already talking to. See below. |

A typo in a name is forgiven when only one name fits (`@hazle`) and no player has that name.

**Plain chat.** Once you've talked to someone, you can carry on without the @ for three minutes,
as long as they're within 64 blocks. They also pick up your plain chat if you're alone and:

1. you're looking at one of them (up to 24 blocks away), or
2. you have none in view, so it goes to your nearest companion within 64 blocks, or
3. you're standing right beside a stranger (within 8 blocks).

With other players nearby I don't want to hijack your chat with friends, so plain chat only
reaches a companion you're facing. Saying bye, walking off or going quiet ends the conversation.

**Groups without an @.** Start a message with "everyone", "everybody", "guys", "team", "you all" or
"all of you" ("hey everyone, ..."), or say "you all" anywhere ("can you all come here"), and it goes to
everyone within 48 blocks plus the companions with you. A role at the start of a message ("all my
builders, ...", "farmers, ...", `@builders`) reaches that role: loaded, same dimension, nearest first,
twelve at the most. A role mentioned later ("work with the collectors") is just a mention. Ordinary
chat that happens to say "everyone" is left alone when other players are close by and it isn't an order.

For a group order one of them answers (not all of them), and a grey *Heard by* line says who got it
and who couldn't.

**Reports.** When you use `@party` or `@everyone` you get one short grey line saying who heard it,
and another for anyone who couldn't:

- *another dimension*: they can't hear you from here. Tell them when you're back together.
- *not loaded, too far away*: their part of the world isn't loaded, so they're frozen until you
  go near. Messages and orders can't reach a companion in an unloaded chunk, so nothing is queued.

**Other people's companions.** Only the player a companion travels with can give them orders,
unless the host or an operator has obey mode on (below). Everyone else gets a polite no. Strangers
(companions who haven't joined anyone) will talk to you, and offer to join you if you ask them to do a
job.

## Implemented: plain requests for whoever is around

Say what you need without naming anyone, and the companions within about 32 blocks who take your
orders (your party, plus everyone if you have obey mode on) sort it out between them:

- **A building.** "build a house", "let's build a house", "we need a house", "can someone build us a
  house?", "let's build a shelter", "can someone build a wall".
- **A farm.** "we need a farm", "let's make a farm", "could someone start a farm?".
- **A storehouse.** "we need a storehouse", "time to build a storehouse".
- **Materials.** "can someone get wood", "we need more stone", "someone chop some wood", "I need wood".
- **A camp.** "let's set up camp here", "time to make camp".

If only one companion is around, that one does it. If several are:

- One of them answers with the plan, in their own voice: "Maddie and I'll do the walls and the roof,
  Hazel, get us some logs, and Dasha keeps watch."
- Builders share **one** blueprint. Each claims the next free spot, so several pairs of hands go into the
  same house. A builder who runs dry gets blocks from another one.
- Collectors (and anyone else free) chop wood and hand it to the builders when the builders are short.
- Farmers take the farm. Guardians walk round the site and deal with anything hostile.
- They say when it's half done and when it's finished. When the blocks run out and nothing more is coming,
  someone says how many are missing and the job stops. They never say it's built unless it is.

Only real requests count. "I built a house yesterday", "I love building houses", "we don't need a farm",
"did you build a house?" and "I'm going to build a house" start nothing. Something too vague ("build
something") goes to the companion you're talking to, who asks what you mean. With other players nearby,
a plain request only counts if you're facing one of your companions.

## Implemented: orders

These all work in chat, with or without a name, and most also work as `/companion` commands.

- **Join you.** "join me", "want to join me?", "travel with me", "let's team up". You can have 3
  companions at a time unless the host or an operator changes it. `/companion recruit Hazel`
- **Let them go.** "you're dismissed", "you can go home", "you're free to go". `/companion dismiss Hazel`
- **Follow.** "follow me", "come with me", "let's go", "stay close", "keep up". "Let's go home" means
  come along, not goodbye. `/companion follow Hazel`
- **Wait.** "wait here", "stay", "hold on", "don't move", "guard this place", "keep watch". They stay at that
  spot.
- **Come.** "come here", "get over here", "come back", "over here". They walk over, or catch up if the way
  is blocked.
- **Stop.** "stop", "never mind", "cancel that", "forget it", "that's enough". Drops the current job; if they
  were only following, they wait.
- **Gather.** "get me 16 oak logs", "collect some stone", "I need wood", "dig for coal", "chop some trees",
  "bring me a stack of cobblestone", "could you get me some sand?". Natural blocks only, up to 256. Hard
  blocks need the right tool in their pack, and they tell you if there's none nearby or they lack a tool.
  `/companion gather Hazel 16 oak logs`
- **What you're pointing at.** "collect that", "break that block", "get more of this". "That" is the block
  under your crosshair, or what they were last collecting.
- **Fight.** "fight that zombie", "kill the nearest creeper", "go and kill the spiders", "take out that
  skeleton", "help me fight". See "Fighting" below.
- **Hunt.** "hunt some pigs", "hunt animals". Animals only count if they're not tamed.
- **Dig.** "go mining", "let's go mining", "dig a tunnel", "tunnel ahead", "dig down 20", "dig a staircase
  down". Needs a pickaxe. Stops before lava, water or a big drop, and puts torches up if they carry some.
- **Build.** "build a shelter", "put up a hut", "I need a shelter" (a 5x5 hut, about 70 building blocks),
  "build a wall" (7 wide, 3 high, about 20 blocks), "build a bridge" or "bridge this gap" (across water or a
  gap ahead of you, up to 32 blocks), "set up camp" or "make a campfire" (a campfire and three seats; needs a
  campfire and 3 blocks).
- **Hand things over.** "give me everything", "give me the wood", "give me 5 bread", "drop the logs", "hand it
  over". `/companion give Hazel`
- **Their pack.** "show me your inventory", "what are you carrying?", "open your pack", or sneak and
  right-click them.
- **Status.** "what are you doing?", "how's the build going?", "are you done yet?". A companion at home
  says what part of their day it is, how they feel, and any favour they're after. `/companion status Hazel`

**Anyone can do any job.** Nobody refuses something because of their personality. They sound
different doing it, and their role (below) decides who's quickest. The only refusals are ethical
ones: they won't attack you, other companions, players, villagers, iron golems or tamed pets.

**"This" and "that".** "collect that" uses the block you're looking at. "fight that" goes for the
creature under your crosshair, or the nearest hostile mob. For "build this" or "build something" they
can't know what you mean, so they ask one short question ("A shelter, a wall, a bridge or a camp?")
and use your next message as the answer. The same goes for "gather" with no item ("Gather what?") and
"attack" with nothing in sight ("Attack what?"). Ignore the question and they treat your next message
as a new one.

**Strangers.** If you ask a companion who hasn't joined anyone to build, gather, dig or fight,
they offer to join you first. Say yes and they join (if you have room) and start the job.

**Far away.** If you ask for a gather, dig or build and they're more than 24 blocks from you, they
say they're on their way, walk over (or catch up by teleport if they're stuck or very far), and
start the job when they arrive.

**One job at a time.** A new job replaces the old one, which is properly cancelled.

**Honest results.** They only say a job is done when it is. If they can't do it, they tell you why
in their own voice: no tool, not enough blocks, the ground is uneven, there's lava, the area is
protected, nothing of that kind nearby.

## Implemented: big projects

Ask a group for something big and they work it out between them. Both of these work as typed:

> All my builders, please help expand this village. Everyone should have their own house, unless
> you all want to share, or just some of you do. Work with the collectors, gather materials and
> start building.

Every builder lays out a small cottage (5 by 5, a door, a window gap on each side, a flat roof,
room for a bed) on its own site near you, spaced apart, facing the middle of the village, and clear
of existing buildings, other people's builds and protected areas. If there is only room for some,
the rest share a house. Collectors nearby gather logs and hand them to the builders.

> All my farmers, build a farm and start harvesting food for us to eat. Connect with the builders
> and maybe build a building for us to store and cook food.

The farmers till a plot of up to twenty tiles beside open water (or around a water source they place
from a bucket), plant whatever seeds, carrots or potatoes they carry, tend it for a few minutes,
harvest what is ripe and replant. A builder makes a small storehouse with a chest and a furnace.
Farmers put their surplus food into storage chests (one companions built, or one marked as storage),
and never into any other chest.

How it stays honest:

- They only use what is in their pack. Logs become planks, planks become sticks, a chest, doors or a
  wooden hoe, cobblestone becomes a furnace, coal and a stick become torches. Nothing appears from nowhere.
- A builder who is short of blocks says so, waits a little for collectors if you asked for them, then
  chops their own wood. If they still can't, they say what's needed and stop.
- No water, no seeds, no hoe, no room or no materials are reported by name instead of going quiet.
- One project at a time per companion, buildings of about seventy blocks, and any other order
  (including "stop") replaces the project.

## Implemented: going home

"go home" or "head back home" walks them home. "teleport home", "go home now" or "tp home" takes them
there at once. Someone who travels with you stays yours and waits at home; someone who doesn't just goes
back to their own day there. Homes are saved with the companion.

| Command | What it does |
|---|---|
| `/companion sethome <name\|party\|everyone\|role> [here\|x y z]` | Sets their home to where you stand, or to the coordinates. |
| `/companion home <who> [teleport]` | Sends them home. With `teleport` (or `/companion tphome <who>`) they go straight there. |

Both report who went and who couldn't (another dimension, not loaded, not yours to command).

## Implemented: fighting each other on your say-so

"Attack Steve" or "Charlie, attack that player" (looking at them) makes a companion fight that
player or companion, and only that one. Never on their own, never you or their own owner, and never
a player while the world's `pvp` game rule is off (they tell you why). Two companions told to fight
spar until one is knocked down; nobody is killed. "Stop" ends it. Villagers, golems and pets are
still off limits.

## Implemented: roles

Every companion has a role next to their personality. The personality is how they talk. The role is
what they're good at, and what they do with their working day at home.

| Role | Good at | Effect | At home | Slower at |
|---|---|---|---|---|
| Guardian | Fighting, defending, hunting | 40% more damage, 4 extra health | Walks a patrol round home and sees off hostile mobs | Farming |
| Builder | Building, digging, tunnels | 40% faster placing and digging | Puts up a shelter if there's no roof, lights dark corners | Fighting |
| Collector | Gathering, mining ores, scouting | 40% faster at gathering | Chops wood and gathers surface stone | Building |
| Farmer | Crops, animals, food | 40% faster harvesting crops | Works the farm, starts one by water if there isn't one | Digging |

Nobody is ever blocked from a job. The "slower" ones work about 15% slower. The cast roles are in
[CAST.md](CAST.md). Wanderers without a cast identity get a role from their personality.

## Implemented: a day of their own

Every companion who isn't travelling with someone lives a day at home: hub residents, companions you
dismissed, and wanderers. Followers stay with their player, and any order comes first.

| Time | What they do |
|---|---|
| Dawn | Get up, one at a time over the first hour or so. The first one up puts out the evening fire. |
| Morning | Work by role (see the table above). |
| Midday | A meal in the kitchen. If they carry food, they eat some of it. |
| Afternoon | More work, or time off: a stroll, the meeting spot, a favourite spot, their own little project. |
| Evening | Everyone gathers at the meeting spot. A campfire there gets lit at dusk. |
| Night | Bed. They lie down in a bed of their own, the way villagers do. With no bed, they doze somewhere dry. |
| Rain | Indoors: their bed, the kitchen, storage, or any covered spot nearby. |

- **Personalities shift it.** Farmhands are up with the hens and in bed early, mystics are night owls,
  rogues take most afternoons off, guardians rarely do. Everyone gets their own few minutes either way,
  so a hub never wakes up as one.
- **Needs.** Hunger, tiredness and loneliness build up and are put right by meals, sleep and company. A
  hungry companion eats early, a worn-out one goes to bed early, a lonely one spends free time with the
  others. Lonely companions talk more; tired or hungry ones less. Nobody is hurt by any of it.
- **Talking to them.** Say something and they stop to listen. Talking to someone in bed gets them up for a
  bit, and "what are you doing?" gets their part of the day, their mood, and any favour they're after.
- **Orders first.** Any order replaces their day, and they go back to it when the job is done.

Daily life is on by default (`dailyLife` in the config). It only moves companions about and lights the
meeting fire. Work that changes the world (harvesting, chopping, building, planting) needs
`/companion autonomy on`, except in a farm someone marked for them (below), which the farmers always look after.

## Implemented: where things are

Each home finds its own places, looking within about 20 blocks every couple of minutes, so anything you
build is noticed:

- **Beds.** Each companion claims a free bed nearby, one each, and keeps it. A bed someone is lying in, a
  bed a player has slept in, and another companion's bed are never taken. If one of them is asleep in the
  bed you want, right-click it: they get up and it's yours from then on.
- **Meeting spot.** A bell, else a campfire, else home itself.
- **Kitchen.** A furnace, smoker or campfire with a chest within a few blocks, else the meeting spot.
- **Farm.** The patch of farmland nearest home (24 blocks across at most).
- **Storage.** A storehouse chest companions built, or a chest you mark. Any other chest is never touched.
- **Patrol points.** Four points about twelve blocks out, for the guardians.

You can see it all, and change it:

| Command | What it does |
|---|---|
| `/companion places [name]` | What the home nearby has (meeting spot, kitchen, farm, storage, patrol points) and what each resident is up to, their mood and their bed. |
| `/companion setplace bed [name]` | Stand on (or by) a bed: it's theirs now. With no name, it goes to the nearest resident without a marked bed. |
| `/companion setplace commons` | The meeting spot is where you stand. A campfire here gets lit at dusk. |
| `/companion setplace kitchen` | Meals happen here. |
| `/companion setplace farm` | The farmland around you is the farm (or, with none yet, a 9 by 9 patch). Farmers always work a farm marked for them, and only the crops inside it. |
| `/companion setplace storage` | Stand next to a chest or barrel: the harvest goes in there, and in no other chest. |
| `/companion setplace patrol` | Adds a patrol point for the guardians (eight at most). |

`setplace` takes an optional name or role to say whose home you mean (`/companion setplace farm
farmers`, `/companion setplace bed Samuel`). Without one, it's the home nearest you. Marking places is
for the single-player host or operators on a server.

On a server the hub's places can go in the config instead, under `server.hub.places`: a meeting spot,
kitchen, farm and storage (boxes from one corner to another), beds by name, patrol points, and other spots
residents like to wander to. [skyhaven-places.json](examples/skyhaven-places.json) is a full example for an
island hub. Anything left out is found on its own.

## Implemented: catching up

When nobody is near a home, its chunks unload and nothing there moves. Each home remembers when someone
there was last going about their day. When you come back after a while, the time that really passed is
worked out:

- Crops in the farm grow as they would have (about a stage every quarter of a day on wet farmland, half
  that on dry).
- If a farmer lives there and there's a storage chest with room, ripe crops are harvested and replanted,
  and the harvest goes into that chest. Only that chest, only the crops inside the farm.
- Capped at a week of time and a few hundred crops. When the storage is full, the rest stay ripe and waiting.
- You get one grey line: "While you were away (the last 3 days): Samuel and Honey harvested 24 wheat and
  9 carrots, replanted, and put it all in storage."
- A page goes into the diary on a lectern near the meeting spot, if there's an empty lectern there (or one
  with their diary on it). Other books are left alone.

A server that pauses when empty, or a single-player world that's closed, doesn't move the game clock, so
no time passes there and there's nothing to catch up. That's honest, not a bug. Catching up needs the farm
to be one marked for them, or autonomy on.

## Implemented: life together

- **Shared meals, stories and a festival.** Two or more eating together chat about the food. After dark at
  the fire, someone tells a story. On a full moon (every eighth night) there's a festival: everyone stays up
  later and dances (well, hops). These only start when a player is close enough to hear.
- **Friends and rivals.** Time together at meals, in the evening and at work brings companions closer.
  A few personalities rub each other up the wrong way (a rogue and a guardian, a sellsword and a naturalist,
  a mystic and a farmhand), and for them it can go the other way. Friends talk warmly about each other,
  rivals get snippy, and they gossip about the players they know.
- **A project of their own.** With autonomy on, a companion makes a little flower bed near home over
  several days: a flower a day, ones they picked themselves, nine at most. Never in a protected area or
  beside anything a player built, and only on plain grass or dirt.
- **Favours.** Now and then a companion at home asks a passing player for something their work needs
  ("bring me 16 logs", "32 cobblestone", "8 bone meal", "16 arrows") and offers something fair from their
  own pack. Right-click them with the items to hand them over; they pay when it's all there. They only ask
  when they really have the reward, each player gets asked once a day at most, and a favour lasts a day.
- **What they start with.** Anyone who lives somewhere starts once with a few things of their own:
  food for their meals, and something their work uses (seeds for farmers, torches for builders, an emerald
  for collectors, iron for guardians). It's what they eat and what they pay favours with.

## Implemented: a brand-new world

None of this needs a special build, fixed coordinates or a hub. In a fresh world:

1. Get a companion: `/companion summon` (cheats on), the spawn egg in Creative, or "Open to LAN" with
   cheats allowed in a survival world.
2. Where they stand when they appear is their home. Recruit them and dismiss them somewhere else, or use
   `/companion sethome`, to move it.
3. They live their day there straight away, dozing in the open and eating at home until there's more.
4. Put down what you like and they'll find it: a bed each, a bell or campfire, a furnace by a chest, some
   farmland. Or mark things with `/companion setplace`.
5. With `/companion autonomy on` they help get things going: collectors gather wood and stone, a builder
   with no roof puts up a small shelter (chopping the wood if they must), and a farmer with seeds starts a
   plot by the nearest water (making a hoe from a couple of logs first if they need one).
6. When something's missing they say what they need ("We could do with a bed round here"), once a day, and
   get on with something else. A farmer with a full pack and no storage chest also gets you a grey tip on
   how to give them one.

## Implemented: settings you can change in game

The single-player host can always use these. On a server they need operator rights. They're saved to
`config/hearthbound.json` and apply to every companion in the world.

| Command | What it does |
|---|---|
| `/companion chattiness` | Shows the current level. |
| `/companion chattiness off\|low\|normal\|high` | How much they say unprompted. Replies, confirmations and danger warnings always get through. |
| `/companion chattiness 0.7` | Any number from 0 to 2 (saved as `ambientChatter`). |
| `/companion autonomy on\|off` | Lets companions do work of their own that changes the world: chopping, gathering, harvesting, lighting dark spots, putting up a shelter, starting a farm, planting a flower bed. Off by default. |
| `/companion obey on\|off` (or `lead`) | Every companion in the world takes your orders: ones with nobody, yours, and ones travelling with other players. On by default for the single-player host. |
| `/companion partylimit` | Shows how many companions each player can travel with. |
| `/companion partylimit <number\|unlimited>` | Changes it for everyone. 0 or `unlimited` lifts it. Saved as `partyLimit`. |
| `/companion partylimit <player> <number\|unlimited\|default>` | One player's own limit (they must be online). Saved in `partyLimits`. |

**Obey mode.** Only the host of a single-player world and server operators (permission level 2 or more)
can use it; anyone else is refused, and an operator who loses operator rights loses it too. Companions
with nobody only join you if you ask ("follow me" does that, and the party limit applies). Companions
travelling with someone else obey you but never change sides: "join me" is still refused, and their own
player can call them back at any time. Everyone goes back to what they were doing when the job ends. On a
server each operator turns it on for themselves (saved as `obeyPlayers`).

**Autonomy while following.** While a companion is following you and you stand still for a bit, they may
pick a job that suits their role (collectors chop a tree log or pick up surface stone, farmers harvest a
ripe crop and replant, guardians go after a hostile mob close to you, builders put a torch in a dark spot).
Your orders always come first. The limits everywhere: no more than ten blocks or torches a minute each,
nothing within a protected area or beside things players built (planks, doors, glass, chests and so on,
except ripe crops), nothing taken from chests, and nothing if their pack is nearly full.

## Implemented: conversation

- **Follow-ups work.** "Why?", "really?", "and you?", "tell me more" and "what do you mean?" all
  lean on what you were just talking about. After a refusal, "why?" repeats the real reason.
- **They answer about themselves.** "What are you good at?" gets their role, and remarks about the
  weather or the time of day get a fitting reply.
- **They pick up what you mention.** Say you saw a creeper, or that you found some iron, and the
  reply is about that.
- **They remember you.** "call me Captain", "I love the ocean", "I hate spiders", then
  "what do you know about me?" and "are we friends?".
- **They talk to each other.** Short exchanges (two to four lines) when two or more travel with you, and
  between neighbours at home. These are only spoken lines; nothing a companion says is read as an order.
- **Ambient lines wait for you.** Comments hold off while you're chatting. Warnings come straight through.

## Implemented: other commands

| Command | What it does |
|---|---|
| `/companion help` | Lists what you can say and do. |
| `/companion list` | Your party, what each is doing, and their health. |
| `/companion say Hazel hello` | Sends a chat message to one companion. |
| `/companion skin Hazel noor slim` | Changes outfit (a default skin, or a cast outfit like `daisy`). |
| `/companion summon hazel` | Spawns a cast member or personality (needs cheats). Old names still work. |
| `/companion hub list`, `spawn`, `home Name` | Hub tools for servers (operators). `list` and `home` also work from the server console. |

Keys: <kbd>G</kbd> opens chat with the name of the companion you're looking at, <kbd>H</kbd>
toggles the party list.

## Limits you should know about

- A companion in an unloaded chunk can't hear you or act. Homes catch up when you come back (above), but
  only the farm is worked out; nobody moves about or builds while unloaded.
- They can't craft beds (no wool), so a home needs you to put the beds down.
- Houses are one size, farms are a flat plot, and the flower bed is the only personal project so far.
- Building uses blocks from their pack and only places into empty space. It won't replace anything.
- They fight in melee only.
- English only.

## Planned

These are things I want but haven't built, so please don't expect them yet:

- Companions turning up on their own in a world (now you summon them, or use the spawn egg).
- Copying a building you made and having them build it again.
- Smarter movement: ladders, pillaring out of holes, bridging small gaps, swimming after you.
- Bows, shields, eating when hurt, swapping worn-out gear.
- Putting things in chests, smelting and crafting on request.
- A conversation screen with your history.
- Asking for torches or plants on demand ("light up this cave", "plant these seeds").
- Counting what they carry in a chat reply ("how many logs do you have?").
- More personal projects than the flower bed.
