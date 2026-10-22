<div align="center">

<img src="src/main/resources/assets/hearthbound/icon.png" width="88" alt="Hearthbound icon">

# Hearthbound Companions

**RPG-style companions for Minecraft.**<br>
Recruit them, chat with them, and go on adventures together.

<a href="https://github.com/ColeCraven/hearthbound-companions/releases/latest"><img src="https://img.shields.io/badge/Download-v0.7.0-2ea44f?style=for-the-badge" alt="Download v0.7.0"></a>
<a href="#install"><img src="https://img.shields.io/badge/Install-4_easy_steps-3A7BD5?style=for-the-badge" alt="Install in 4 easy steps"></a>

![Minecraft 26.3](https://img.shields.io/badge/Minecraft-26.3-62B47A)
![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4)
![Servers](https://img.shields.io/badge/Servers-no_client_mod_needed-8A63D2)
![License: MIT](https://img.shields.io/badge/License-MIT-3A7BD5)

<img src="docs/images/cast/banner.png" width="100%" alt="All twelve companions standing together in a meadow">

**[Install](#install)** · **[Your first companion](#your-first-companion)** · **[Fresh world](#fresh-world)** · **[What they can do](#what-they-can-do)** · **[Cheat sheet](#cheat-sheet)** · **[Servers](#playing-on-a-server)** · **[FAQ](#faq)**

</div>

<br>

I've always loved a good RPG companion: the one who carries way too much of your loot and has a
dry comment for every dungeon. Minecraft adventures feel a bit lonely in comparison, so I made
this: a mod that adds companions who look like players, have their own personalities, and actually
help you out.

Walk up to one, ask them to join you, and from then on they follow you around, fight next to you,
chop wood, dig, build, wander off to look at flowers when you stop, and talk back when you talk to
them. Each one sounds different. Charlie the sellsword answers in about four words. Honey the farm
girl is scared of the dark and won't stop talking about her gran.

- **Just talk.** It all happens in normal chat. Say hi once and keep typing, like you would with a friend.
- **They get to know you.** Tell them your nickname or what you love, and they remember it and bring it up later.
- **They pull their weight.** Gathering, mining trips, tunnels, shelters, walls, bridges and camps. Putting
  things in chests, smelting and crafting when you ask.
- **They copy your builds.** Save something you built as a blueprint, then say "@Maddie build my cottage here".
- **Everyone has a role.** Guardians fight best, builders build and dig fastest, collectors gather
  quickest and farmers farm best, but anyone can do any job.
- **They have lives of their own.** When they're not out with you they keep a day: up at dawn, work, a meal at noon,
  evenings round the fire, and bed at night in a bed of their own.
- **They work as a team.** Say "let's build a house" and whoever's around splits the job: builders on the walls,
  collectors on the wood, guardians on watch.
- **They never fake it.** If they say they'll do something, the game is really doing it. If they can't, they tell you why,
  and `/companion explain Name` shows exactly what they heard and what's in the way.
- **Works in any world**, with nothing to set up, and **on servers** with nothing for your friends to install.

<br>

## Install

> [!TIP]
> Joining a server that already runs Hearthbound? You don't need to install anything. Just join and say hi.

You need **Minecraft: Java Edition 26.3** on Windows, Mac or Linux. Setup takes about five minutes.

**1. Install Fabric.** Grab the [Fabric installer](https://fabricmc.net/use/installer/), run it, set
the Minecraft version to **26.3** and click **Install**. That adds a Fabric profile to your Minecraft
Launcher.

**2. Download the mod.** From the [latest release](https://github.com/ColeCraven/hearthbound-companions/releases/latest),
download **`Hearthbound-Companions-0.7.0-install-kit.zip`**. It has the mod, Fabric API (a library
the mod needs) and a step-by-step guide.

**3. Drop two files into your `mods` folder.** Unzip the kit and copy both `.jar` files into `mods`:

| Windows | Mac | Linux |
|---|---|---|
| Press <kbd>Win</kbd> + <kbd>R</kbd>, paste `%appdata%\.minecraft` and press Enter | In Finder press <kbd>Cmd</kbd> + <kbd>Shift</kbd> + <kbd>G</kbd> and go to `~/Library/Application Support/minecraft` | `~/.minecraft` |

Open the `mods` folder in there. If it doesn't exist yet, just make one.

**4. Play.** Open the Minecraft Launcher, pick **fabric-loader-26.3** next to the Play button, and hit Play.

> [!IMPORTANT]
> Once a world has companions in it, only open it with the Fabric profile. Plain Minecraft doesn't
> know what a companion is and quietly removes them from the world.

<details>
<summary><b>Using Prism, the Modrinth App or CurseForge instead?</b></summary>
<br>

Make a new instance for Minecraft 26.3 with Fabric, then drop `hearthbound-0.7.0.jar` and
[Fabric API](https://modrinth.com/mod/fabric-api) into that instance's `mods` folder. That's it.

</details>

<br>

## Your first companion

1. Create a world with **Allow Commands** turned on (or play in Creative).
2. Summon someone. Type `/companion summon honey` in chat. Any of the cast works: `dasha`, `charlie`,
   `hazel`, `birintan`, `maddie`, `aj`, `daisy`, `cole`, `honey`, `samuel`, `luna`, `luke`.
   In Creative there's also a **Companion Spawn Egg** in the Spawn Eggs tab.
3. Say hi. Right-click her, or type `@Honey hi!`.
4. Ask her along with `@Honey join me`.
5. Now just talk. Try "how are you?", "follow me", "get me 16 oak logs" or "build a shelter".

> [!TIP]
> Survival world with cheats off? Press Esc, choose **Open to LAN**, turn on **Allow Commands** and
> click **Start LAN World**. Commands work until you quit, and the world is back to normal next time.

Forgot something? `/companion help` lists everything you can say and do, right in game.

<br>

## Fresh world

Hearthbound works in any world, including one you made five minutes ago. There's no special build, no
fixed coordinates, and nothing to configure. Here's what happens, and what you can do to help it along:

1. **Wherever a companion appears is their home.** Recruit them and dismiss them somewhere else (or use
   `/companion sethome Name`) to move them.
2. **They live their day there straight away.** Up at dawn, work, a meal at noon, the evening together, bed
   at night. With nothing built yet, they doze somewhere dry and eat at home.
3. **Build and they'll notice.** They look around their home every couple of minutes (and straight away when
   you run `/companion places`) for:
   - a **bed** each (they claim one and lie down in it at night),
   - a **bell or campfire** to meet at in the evening (they light the campfire at dusk),
   - a **furnace or smoker beside a chest** for a kitchen,
   - **farmland** for the farmers.
4. **Let them help get started.** `/companion autonomy on` lets them change the world for themselves:
   collectors fetch the first logs and stone, a builder with no roof over home puts up a small shelter, and a
   farmer with seeds starts a plot by the nearest water.
5. **Point things out if you like.** Stand somewhere and use `/companion setplace bed|commons|kitchen|farm|storage|patrol`,
   or give coordinates from the console or a command block. Farmers only ever put the harvest into a chest you
   mark as storage (or one they built themselves). `/companion places` shows what they've found.

When something's missing they tell you ("We could do with a bed round here"): once, then after a day, after
two days, then every four days at most. In between they get on with something else instead of trying the
impossible over and over. Worlds and configs from earlier versions keep working exactly as before. The full
details are in [GUIDE.md](docs/GUIDE.md#implemented-fresh-world).

<br>

## What they can do

### Just talk to them

Say hi with `@Honey hi!` (or right-click her) and after that you can keep chatting like you would
with anyone: "how are you?", "tell me about your family", "what should we do today?". No @ needed
until you say bye or wander off, and a typo in a name (`@rsoie`) still gets through. Orders work
the same way: `@Hazel follow me`, `Charlie, get me 16 oak logs`, `@party wait here`. Press <kbd>G</kbd>
and the chat opens with the name of whoever you're looking at already filled in.

Press <kbd>J</kbd> instead for a conversation screen: what you've said to each other, their mood, what they
need and what they're doing, buttons for follow, wait, come, status and give, a few suggestions for right
now ("Put everything in that chest" when you're looking at one), and a box to type anything else. It does
exactly what chat does, it just doesn't go out to everyone. It needs the mod on the server too; on a server
without it, <kbd>J</kbd> opens the chat box.

You don't have to name anyone, either. Say "let's build a house", "we need a farm" or "can someone get
wood?" and whoever's close by and listens to you picks it up. The wording is up to you: "we need a
house", "can someone build us a house?" and "build a house" all work.

You can say "this" and "that" too: look at a log and say `collect that`, or look at a zombie and say
`fight that`. If a request is too vague (`build this`) they ask one short question and use your
answer. `@party` is everyone with you, and `@everyone` is every companion you have, wherever they are
(they have to be in the same dimension and in a loaded chunk to hear you, and you get a short report
of who did). The full list is in [GUIDE.md](docs/GUIDE.md).

Every personality has well over a hundred situations of their own dialogue, so you're not hearing
the same line over and over.

### They get to know you

Tell one "call me Captain" and that's what they call you from then on. Tell them you love the
ocean and they'll point it out when you get to one. When things are quiet they ask you stuff
("What should I call you, anyway?", "What's your favourite thing to find down a mine?") and
remember the answer. Ask "what do you know about me?" and they'll tell you.

<p align="center">
  <img src="docs/images/free-chat.png" width="80%" alt="Honey learning a nickname and a favourite thing, then saying them back">
</p>

They notice what you're up to as well. They cheer when you get an advancement or find diamonds,
say goodnight when you go to bed and ask how you slept in the morning, and panic when you die.
Crouch twice while looking at one and they'll crouch back. If you're starving and they have food
on them, they hand some over, and the ones who like you sometimes give you a flower they picked
on the way.

### They pull their weight

Follow, wait, come here, gather stuff, hand items over, attack that thing, stop, go home. And some
proper jobs:

- **Dig.** "go mining" digs a staircase down and then a tunnel along, grabbing any ores next to it.
  "dig a tunnel" and "dig down 20" do one or the other. They need a pickaxe, put torches up if they
  carry some, and stop before lava, water or a big drop.
- **Build.** "build a shelter", "build a wall" or "build a bridge" (across water or a gap in front of
  you), using blocks from their pack. A shelter gets a door and a torch if they have them. "set up
  camp" puts down a campfire with three seats, if they carry a campfire.
- **Chests, smelting and crafting.** Look at a chest and say "put everything in that chest" or "take the iron
  from that chest". "smelt this iron" uses a furnace nearby (or one they make) and real furnace time. "craft me
  a pickaxe" uses the game's recipes, a crafting table and what's in their pack, and you get told exactly what's
  short if they can't.
- **Copying your builds.** Mark two corners with `/companion blueprint corner1` and `corner2`, save it with
  `/companion blueprint save cottage`, then say "@Maddie build my cottage here" or "builders, build cottage". They
  pack the materials, craft, smelt or gather what's missing, and tell you up front what they can't get. Structure
  block files work too.
- **Getting about.** They climb ladders and vines, build their way up out of holes, bridge small gaps (never
  next to your builds) and swim after you, before falling back on catching up by teleport.
- **Fighting and looking after themselves.** Guardians shoot with a bow or crossbow from their pack and draw
  the sword up close, shields really block, they eat when they're hurt, hand you food when you're in a bad way,
  swap worn gear for a spare and mend it at an anvil when they have the materials.
- **Roles.** Guardians hit harder and take more punishment, builders place and dig faster,
  collectors gather faster and farmers harvest crops faster. Nobody is ever blocked from a job,
  and they'll tell you whether it suits them. On a server the role is the grey line under a
  stranger's name.

<p align="center">
  <img src="docs/images/building.png" width="49%" alt="Cole next to the shelter he just built from planks in his pack">
  <img src="docs/images/gathering.png" width="49%" alt="Charlie chopping logs, with the party list in the corner">
</p>

When they can't do something, they tell you why, in character: there's no iron around here, they
need a pickaxe, the ground's too uneven to build on, they're short on blocks.

### They work together

- Say "let's build a house" with a few of them around and one of them says the plan ("Maddie and I'll do
  the walls and the roof, Hazel, get us some logs, and Dasha keeps watch"). The builders share one
  blueprint, the collectors bring the wood, the guardians walk round the site, and you hear when it's
  half done and when it's finished (or exactly what's stopping them).
- Say "everyone, ..." or "all my builders, ..." and the whole group hears you, with no @ and one
  short reply from one of them.
- Ask the builders for a house each and they pick sites near you, spaced apart, craft what they can
  from their pack, chop wood if they run short, and build. Collectors bring them logs.
- Ask the farmers for a farm and they till a plot by the water, plant it, harvest and replant, and a builder
  puts up a storehouse with a chest and a furnace for the food.
- `/companion obey on` means every companion in the world takes your orders, even ones travelling with
  other players. It's for the single-player host (on by default) and server operators only.

### They have a life of their own

The ones who aren't out with you get on with their day. Hub residents, companions you dismissed and
wanderers all follow the clock: up at dawn (not all at once), work by role in the morning, a meal in the
kitchen at noon, an afternoon of work or time off, the evening together at the meeting spot with the fire
lit, and bed at night, lying down in a bed of their own like villagers do. When it rains they go indoors.

- **Everyone's a bit different.** Farmhands are up with the hens, Luna and Luke stay up late, the rogues
  take most afternoons off. Hunger, tiredness and loneliness nudge their day along, and their mood.
- **They live together.** Meals turn into a chat, someone tells a story by the fire after dark, and every
  full moon there's a festival. Friendships grow from time spent together, a few personalities clash,
  and you'll hear about it.
- **Small projects and favours.** With autonomy on, a companion makes a little flower bed over a few days.
  Now and then one asks you for a favour ("bring me 16 logs") and pays you from their own pack.
- **Time away is caught up.** Come back after a few days and the farm has been worked: crops grown,
  harvested, replanted and put into storage, with one line telling you who did what. If there's an empty
  lectern at the meeting spot, it gets a diary too.
- **Your orders always come first.** Talk to them and they stop to listen (even in bed). Any order replaces
  their day, and they go back to it after.

Out with you, they're the same as ever. Stand still for a bit and they get bored: Hazel goes to study a
flower, Maddie grabs loot you left lying around (and admits it), Honey pets the nearest cow, the fighters
walk a little guard circuit, and Luna stares at the stars at night. They drop it the second you move or
give an order. They also nag you to eat, suggest finding shelter before dark, and have short chats with
each other.

`/companion autonomy on` lets them do work that changes the world: chopping a tree, harvesting and
replanting, lighting a dark spot, dealing with a nearby zombie, putting up a shelter. It's off by default,
and your orders always win.

Too chatty? `/companion chattiness low` (or `off`) quietens the comments. They still answer you,
confirm orders and warn you about danger.

On the move they keep a comfortable distance, hurry when you sprint off, and if you get separated
(or jump through a portal) they catch up next to you on safe ground. They warn you about creepers
and mobs sneaking up behind you. By default they're knocked down instead of killed, so you never
lose someone you've got attached to.

Right-click one with an item to give it to them. Everyone has favourites (emeralds for Maddie, bread
for Honey, amethyst for Luna), and it warms them up towards you. Give a fighter a better sword and
they'll use it. Their name, personality, gear, current task and memories all save with your world.

### They don't lie to you

This is the part I cared about most. A lot of AI companion mods let a language model decide what
the character does, and you end up with a follower who says "Sure, I'll mine you some diamonds!"
and then stands there. I hate that.

So talking and doing are completely separate here:

```mermaid
flowchart LR
    P[You say something] --> R[Rule-based reader]
    R -- clear order --> V[Game checks it's possible]
    R -- chatting --> D[Dialogue]
    D -. optional AI .-> M[Language model]
    M -. suggests an action .-> V
    V -- yes --> T[Task runs in game code]
    V -- no, and why --> D
    T -- real progress --> D
    D --> C[Companion replies]
```

Your message goes through a plain rule-based parser first. If it's an order, the game checks
whether it can actually happen, starts the task, and only then lets the companion answer. Progress
and "done" messages come straight from the task, so "Here's your 16 oak logs" only shows up once
the logs are in your inventory. If you hook up an AI model, it can only suggest an action from a
short list, and the game double-checks it the same way. If the game says no, the model's reply gets
thrown out and the companion tells you the real reason instead.

<p align="center">
  <img src="docs/images/party-conversation.png" width="80%" alt="A party chatting, and Charlie explaining he can only build a shelter, a wall, a bridge or a camp">
</p>

<br>

## Meet the cast

There are twelve characters, each with a role and a voice, and every one of them has their own
hand-made skin. You'll also meet wanderers who use the game's default skins (all nine of them,
with wide or slim arms).

| | Role | Voice | Who they are |
|---|---|---|---|
| **Charlie** | Guardian | Sellsword | Gruff, funny veteran with a clay pipe and a pouch of herbs. Has outlived three mercenary companies. |
| **Dasha** | Guardian | Guardian | Golden-haired shieldmaiden from the north. Charges anything with teeth. |
| **AJ** | Guardian | Rogue | The funny one. Keeps trying his luck and never gets anywhere. Takes it well. |
| **Cole** | Guardian | Rogue | Cheeky, quick-witted adventurer with a rapier and a devilish grin. |
| **Maddie** | Builder | Rogue | Sharp-tongued builder in a crimson bandana. Pockets anything you leave on the ground. |
| **Birintan** | Builder | Naturalist | Curious, clever herbalist with a sprig of something behind his ear. Works out how it all fits together. |
| **Luke** | Builder | Mystic | Young sorcerer in an emerald robe who shapes stone like a spell. Says very little, notices everything. |
| **Hazel** | Collector | Naturalist | Auburn-haired ranger who stops to sketch every flower you walk past. |
| **Daisy** | Collector | Guardian | Woodland warden with a gold circlet and a long braid. Will not hurt a villager, ever. |
| **Luna** | Collector | Mystic | Silver-haired stargazer in a star-stitched cape. Likes the night more than you do. |
| **Samuel** | Farmer | Farmhand | Farm lad who still isn't sure leaving home was a good idea. |
| **Honey** | Farmer | Farmhand | Flower crown, first real adventure, nervous about caves, adores every cow she meets. |

<p align="center">
  <img src="docs/images/cast/group-1.png" width="32%" alt="Dasha, Charlie, Hazel and Birintan">
  <img src="docs/images/cast/group-2.png" width="32%" alt="Maddie, AJ, Daisy and Cole">
  <img src="docs/images/cast/group-3.png" width="32%" alt="Samuel, Honey, Luna and Luke">
</p>

More about them (and how I drew the skins) in [CAST.md](docs/CAST.md), and how the six
personalities differ in [PERSONALITIES.md](docs/PERSONALITIES.md).

<br>

## Cheat sheet

The left-hand column is just examples. Say it however you like; the wording is flexible.

| You want to | Say something like | Or use a command |
|---|---|---|
| Recruit someone | "@Name join me", "want to travel with me?" | `/companion recruit Name` |
| Follow / wait / come | "follow me", "let's go" · "wait here", "stay put" · "come here", "get over here" | `/companion follow Name` |
| Get resources | "get me a stack of cobblestone", "I need wood", "could you grab 16 logs?" | `/companion gather Name 64 cobblestone` |
| Get your stuff back | "give me everything", "hand over the logs" | `/companion give Name` |
| Fight something | look at it and say "attack that", "kill the nearest creeper" | |
| Dig | "go mining", "dig a tunnel", "dig down 20" | |
| Build | "build a shelter", "put up a wall", "bridge this gap", "set up camp" | |
| Get a job done by whoever's around | "let's build a house", "we need a farm", "can someone get wood?" | |
| Use what you're looking at | "collect that", "fight that", "build this" (they ask if it's unclear) | |
| Tell them about you | "call me Captain", "I love the ocean", "I hate spiders" | |
| Check on them | "what are you doing?", "how's it going?" | `/companion list` |
| Stop a task | "stop", "never mind", "forget it" | `/companion cancel Name` |
| Swap items | sneak + right-click them | `/companion inventory Name` |
| Let them go | "you're dismissed", "you can go home" | `/companion dismiss Name` |
| Talk to everyone with you | "@party follow me" (or `@all`) | `/companion tell party follow me` |
| Talk to every companion you have | "@everyone come to me" | `/companion tell everyone come to me` |
| Talk to a group, no @ | "everyone, follow me", "all my builders, ...", "farmers, ..." | |
| Big projects | "all my builders, everyone should have their own house", "farmers, build a farm" | |
| Send them home | "go home", "teleport home" | `/companion sethome Name here`, `/companion home party teleport` |
| See a home's places | | `/companion places` |
| Mark a place | | `/companion setplace bed\|commons\|kitchen\|farm\|storage\|patrol [name\|role]` |
| Fight someone, on your say-so | "@Name attack Steve" (players only where PvP is on) | |
| Make everyone listen (host or operators) | | `/companion obey on` (already on for the single-player host) |
| Change the party limit (host or operators) | | `/companion partylimit 5`, `/companion partylimit unlimited` |
| Make them quieter | | `/companion chattiness low` (or `off`, `normal`, `high`) |
| Let them do work of their own | | `/companion autonomy on` |
| Use a chest | look at it: "put everything in that chest", "take the bread out of that chest" | |
| Smelt or craft | "smelt this iron", "cook the beef", "craft me a pickaxe", "make 4 torches" | |
| Save a build | | `/companion blueprint corner1`, `corner2`, `save <name>` |
| Copy a build | "@Maddie build my cottage here", "builders, build cottage" | `/companion blueprint list` |
| Find out why they didn't | | `/companion explain Name` |
| Turn on AI replies (host or operators) | | `/companion ai setup` (Ollama), `/companion ai setup openai\|anthropic [model]`, `/companion ai status\|off` |
| Who can hurt companions (host or operators) | | `/companion damage players on\|off`, `/companion damage others on\|off`, `/companion godmode on\|off` |
| See everything | "what can you do?" | `/companion help` |

| Key | What it does |
|---|---|
| <kbd>G</kbd> | Opens chat with the name of the companion you're looking at |
| <kbd>J</kbd> | Opens the conversation screen (with the mod on the server too) |
| <kbd>H</kbd> | Shows or hides the party list in the corner |
| Right-click | Say hi, or hand over the item you're holding |
| Sneak + right-click | Open their pack |
| Crouch twice | Say hello the Minecraft way |

<br>

## Playing on a server

You can run Hearthbound on a multiplayer server too, and your friends don't need to install
anything. With [Polymer](https://modrinth.com/mod/polymer) on the server, companions show up for
unmodded players as player-shaped mannequins with a name tag and a short grey line under it (their
role, or who they're travelling with). Talking, recruiting and orders all work exactly the
same.

On my own server the whole cast lives at a hub, hanging around until someone asks them along. Each
player gets their own party, only you can give your companions orders, nobody can hurt someone
else's companion, and they won't break blocks near the hub or anyone's builds.

<p align="center">
  <img src="docs/images/server/vanilla-client-hub.png" width="80%" alt="The cast around the campfire at the hub, seen from an unmodded client">
</p>

The residents live their day at the hub like anyone else. If you'd rather tell them where things are than
let them look, list the beds, meeting spot, kitchen, farm, storage and patrol points under
`server.hub.places` ([example for an island hub](docs/examples/skyhaven-places.json)). Operators can make
every companion listen with `/companion obey on`, and change how many companions each player can have
with `/companion partylimit`.

Setup, the hub config and the multiplayer rules are in [SERVER.md](docs/SERVER.md).

<br>

## Settings

<details>
<summary><b>The config file</b></summary>
<br>

The config lives at `config/hearthbound.json` and gets created the first time you play.

| Setting | Default | What it does |
|---|---|---|
| `essentialCompanions` | `true` | Knocked down instead of killed |
| `partyLimit` | `3` | How many companions you can have at once (0 means no limit; `/companion partylimit`) |
| `partyLimits` | `{}` | Per-player limits (by UUID) set with `/companion partylimit <player> <number>` |
| `dailyLife` | `true` | Companions at home follow a daily schedule (work, meals, evenings, bed) |
| `diary` | `true` | Write a "while you were away" page into a diary on an empty lectern at the meeting spot |
| `ambientChatter` | `1.0` | How much they talk on their own (0 is silent, 2 is very chatty) |
| `autonomy` | `1.0` | How often they wander off to do their own thing |
| `routines` | `false` | Autonomy: let companions do work of their own that changes the world (`/companion autonomy on`) |
| `obeyAll` | `true` | Single-player: every companion takes the host's orders, even other players' (`/companion obey off` to turn it off) |
| `obeyPlayers` | `[]` | Servers: the operators (by UUID) who turned `/companion obey on` for themselves |
| `server.hub.places` | | Where things are at the hub (beds by name, meeting spot, kitchen, farm, storage, patrol); anything left out is found on its own |
| `server.playersCanHurt` | `true` | Players can hurt companions at all; your own hits only sting (`/companion damage players on\|off`) |
| `server.otherPlayersCanHurt` | `false` | Other players can hurt your companions (`/companion damage others on\|off`) |
| `server.godmode` | `false` | Companions take no damage at all (`/companion godmode on\|off`) |
| `ai.provider` | `none` | `none`, `ollama`, `openai` or `anthropic` (`/companion ai setup` sets it for you) |

</details>

<details>
<summary><b>Optional: AI conversations</b></summary>
<br>

Everything above works without any AI at all. If you want proper free-form conversations on top,
you can plug in a language model.

The option I'd recommend is **[Ollama](https://ollama.com)**, which runs on your own PC, costs
nothing, and keeps everything private. Install it, run `ollama pull qwen3.5:9b` (about 6.6 GB,
wants roughly 8 to 10 GB of graphics memory, or use `qwen3.5:4b` on smaller cards), then type
`/companion ai setup` in game. It lists the models Ollama has; click one and it asks for a test reply to
check it works. (Or copy [this config](docs/examples/hearthbound-ollama.json) if you'd rather.)

You can also use a cloud API: anything OpenAI-compatible (OpenAI, OpenRouter, Groq, LM Studio) or
Anthropic's Claude. Put your API key in an environment variable (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY` or
`HEARTHBOUND_API_KEY`) or in `config/hearthbound-secrets.json` ([example](docs/examples/hearthbound-secrets.example.json)),
never in the main config and never in chat, then run `/companion ai setup openai <model>` or
`/companion ai setup anthropic`. It checks with one test reply and puts things back if that fails.
`/companion ai status` shows what's on, and `/companion ai off` turns it off. A typical reply is cheap, around $0.03 per hundred messages on the
smallest models. Only your conversation with that one companion, a short memory, and a basic
description of where you are (biome, time, weather, health) gets sent. Your coordinates never are.

If the AI is slow or offline, companions just fall back to their own lines and orders keep working.
More detail in [AI_DESIGN.md](docs/AI_DESIGN.md).

</details>

<br>

## FAQ

<details>
<summary><b>The game won't start, or it says Fabric API is required</b></summary>
<br>

Make sure both jars from the kit are in your `mods` folder: `hearthbound-0.7.0.jar` and
`fabric-api-0.164.0+26.3.jar`. If the error mentions a Minecraft version, re-run the Fabric
installer and pick 26.3.

</details>

<details>
<summary><b>The game starts, but nothing's different</b></summary>
<br>

You probably launched "Latest release" instead of **fabric-loader-26.3**. Pick the Fabric profile
next to the Play button. The title screen mentions Fabric in the corner when it worked.

</details>

<details>
<summary><b>It says "Unknown command" when I type /companion</b></summary>
<br>

Commands are off in that world. Use the Open to LAN trick from [Your first companion](#your-first-companion),
or grab a spawn egg in Creative. Talking to companions with `@Name` works without commands.

</details>

<details>
<summary><b>A companion doesn't answer me</b></summary>
<br>

Check the name is spelled close enough, with @ in front, and that you're within about 60 blocks.
If you're chatting with friends nearby, look at the companion you're talking to, so your chat with
people isn't taken over.

</details>

<details>
<summary><b>My companion is asleep, or keeps wandering off</b></summary>
<br>

That's their day. Companions who aren't travelling with you follow the clock: they work, eat, meet up
in the evening and sleep at night. Talk to them and they stop to listen (even in bed), and any order
comes first. Ask them to join you and they stay with you. If you'd rather they just stood about, set
`dailyLife` to `false` in the config.

</details>

<details>
<summary><b>A companion took my bed</b></summary>
<br>

Right-click it. They get up, and that bed is yours from then on, so nobody claims it again. Any bed
you've slept in is left alone already. You can also give someone a particular bed with
`/companion setplace bed Name`.

</details>

<details>
<summary><b>They keep saying they need something</b></summary>
<br>

They're telling you what's missing (a bed, farmland, a chest for the harvest), once a day. Put one
down near their home, or mark one with `/companion setplace`, and they'll use it. With
`/companion autonomy on` they also sort out a roof and a farm for themselves where they can.

</details>

<details>
<summary><b>Does it work with other mods?</b></summary>
<br>

It should. I've run it alongside Lithium, FerriteCore, Polymer, Ledger and Universal Graves on a
server without problems. If something clashes, please open an issue.

</details>

<details>
<summary><b>Bedrock, consoles or phones?</b></summary>
<br>

Sorry, Java Edition only. Bedrock players can't join a Java server running it either.

</details>

<br>

## For the curious

<details>
<summary><b>Building it yourself</b></summary>
<br>

Gradle needs Java 25 (the one that comes with the Minecraft launcher works fine).

```bash
./gradlew build
```

That builds the jar into `build/libs/` and runs all the tests. If you want to poke around in a dev
copy of the game:

```bash
./gradlew runClient
```

The skins are generated by a small Python script, so you can tweak colours and rebuild them:

```bash
python tools/skins/cast.py
```

</details>

<details>
<summary><b>Testing</b></summary>
<br>

I tried to test this properly instead of just eyeballing it. There are over 350 unit tests (the chat
parser, plain requests and statements, chests, smelting and crafting requests, blueprint layout, the daily
schedule, needs, crops growing while you're away, the gathering logic, the AI response handling), 122 tests
that run inside a real game server (gathering logs and handing them over, digging a tunnel, stopping before
lava, building a shelter, a bridge and a camp, orders typed in plain chat, a house built by three of them
together from "let's build a house", a saved build copied in front of you, climbing, bridging and swimming
after you, bows, shields and mending at an anvil, smelting iron and crafting a pickaxe at a table they put
down, a resident going to bed in their own bed, the farm caught up after three days with the harvest in the
storage chest and nowhere else, a bare new world, obey mode for the host and operators but not for anyone
else, saving and loading), and a few scripted runs in the actual game client.
One of those drops two companions into a normal generated world, walks them across the terrain,
fights zombies at night, goes to the Nether and back, then quits and reloads the world to check
nothing got lost. It caught a couple of real bugs along the way.

The full write-up, including what still needs testing, is in [TESTING.md](docs/TESTING.md).

</details>

<details>
<summary><b>How it's put together</b></summary>
<br>

- [ARCHITECTURE.md](docs/ARCHITECTURE.md): how a chat message becomes a real action in the world
- [AI_DESIGN.md](docs/AI_DESIGN.md): the optional AI side, prompts, costs and privacy
- [TECH_DECISIONS.md](docs/TECH_DECISIONS.md): why Fabric, why a mob with a player model, and more
- [PERSONALITIES.md](docs/PERSONALITIES.md): the six archetypes and what they change
- [SERVER.md](docs/SERVER.md): running it for friends with no client mod
- [RESEARCH.md](docs/RESEARCH.md): what I looked at before starting
- [CHANGELOG.md](CHANGELOG.md): what changed in each version

</details>

<br>

## Known issues

- Getting about, they climb, build up out of holes and bridge gaps of up to three blocks, but they don't dig
  through walls to reach you. If something is out of reach (like the top of a tall tree) they'll say so and
  skip it.
- Companions don't turn up on their own yet: summon one, use the spawn egg, or meet the cast at a server's hub.
- They can't make beds (no wool), so a home needs you to put the beds down.
- While a home's chunks are unloaded only the farm is caught up when you come back; nobody builds or
  wanders while you're away.
- If you teleport really far and their chunk unloads before they notice, they'll stay put until you
  come back.
- Only guardians use bows and crossbows so far.
- Blueprints top out at 48 by 48 by 32 (4096 blocks), and crafting on request only uses what's in their pack.
- The conversation screen needs the mod on the server as well as your game.
- English only.
- On a server, unmodded players see default skins unless you set up signed skins or the resource
  pack ([how](docs/SERVER.md#skins-for-players-without-the-mod)).
- I haven't tried the AI side with a live model yet. It's tested against fake servers, but I still
  need to sit down with Ollama and play.

## What's next

Bows for everyone, saying "copy this" at a build instead of marking corners, companions turning up on
their own in a world, and more cast members. If you have ideas, open an issue!

## Credits

- Built on [Fabric](https://fabricmc.net/), with [Polymer](https://modrinth.com/mod/polymer) for server support.
- The cast skins are my own originals, drawn with [`tools/skins/cast.py`](tools/skins/cast.py).
  Default skins come from the game itself at runtime; nothing from Mojang is copied here.
- Other Minecraft companion mods like CreatureChat, MCA Reborn and Human Companions were a big help
  in working out what I wanted ([research notes](docs/RESEARCH.md)).

Minecraft is a trademark of Mojang and Microsoft. This mod isn't affiliated with either of them.

## License

[MIT](LICENSE). Do what you like with it.
