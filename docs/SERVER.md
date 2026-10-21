# Running Hearthbound on a server

Hearthbound works on a multiplayer server where **nobody else needs the mod**. Players join with
plain Minecraft 26.3 and see the companions as player-shaped mannequins, each with a name tag and a
short grey line underneath (their role, like "Guardian", or "with Tekless" once someone has
recruited them). Everything else
(talking, recruiting, following, fighting, gathering) happens on the server, so it works the same
for everyone.

![What an unmodded player sees: the cast hanging around the hub](images/server/vanilla-client-hub.png)

## What the server needs

- Fabric Loader 0.19.5+ and Fabric API for 26.3
- [Polymer](https://modrinth.com/mod/polymer) (the `polymer-bundled` jar for 26.3). It's what turns
  companions into something an unmodded client understands. Without it the mod still loads, but
  players without the mod can't join.
- `hearthbound-0.2.0.jar` (or newer) in the `mods` folder

The mod notices it's on a dedicated server with Polymer installed and switches this mode on by
itself (`server.vanillaClients: "auto"`). Nothing is added that a player would have to download:
no custom items (the spawn egg is left out in this mode), no blocks, no required resource pack.

## The hub

Set the hub in `config/hearthbound.json` (use your own spawn coordinates) and the cast moves in:

```json
"server": {
  "hub": {
    "enabled": true,
    "dimension": "minecraft:overworld",
    "x": 0, "y": 64, "z": 0,
    "wanderRadius": 16,
    "protectRadius": 64,
    "residents": ["all"],
    "welcomeTip": true
  }
}
```

Every listed cast member who doesn't exist yet is spawned once, in a ring around that spot, with
their starting gear. After that they're ordinary companions who happen to live there. A world
roster (`world/data/hearthbound_roster.json`) remembers who exists, even in unloaded chunks, so
nobody is spawned twice. `residents` can be `["all"]` or a list of names, e.g.
`["dasha", "charlie", "honey"]`.

At the hub they stroll around within `wanderRadius`, turn to look at people nearby, and say hello
when someone walks up. A newcomer gets a one-time tip on how to talk to them. Recruit one and they
follow you anywhere. Dismiss them and they make their own way back to the hub, even from the Nether.

Operator commands: `/companion hub list`, `/companion hub spawn`, `/companion hub home <name>`.

## Sharing a world

| Rule | How |
|---|---|
| Only the owner gives orders | Anyone can chat with any companion, but orders from someone else get an in-character "I'm with Tekless" |
| Party limit per player | `partyLimit` (default 3) counts companions in unloaded chunks too |
| Who hears replies | The owner, whoever just spoke to them, and anyone within 16 blocks |
| No friendly fire on players | Companions never target players or other people's pets. Other players can't hurt a companion (`otherPlayersCanHurt`) |
| No griefing | No gathering within `protectRadius` of the hub, in `protectedAreas`, or within `buildGuardRadius` (4) blocks of anything man-made (planks, glass, doors, beds, bricks...) |
| No stealing | The rogue's "pick up loot" pastime only takes items dropped by their owner or by mobs, never inside protected areas |
| Privacy | A companion's remembered conversation is per speaker: when someone new talks to them, the previous chat is forgotten, so it never reaches another player or an AI prompt |
| Performance | Loaded companions are tracked in a list instead of scanning large areas each tick |

## Skins for players without the mod

`server.skins` picks where a mannequin's skin comes from, per viewer:

1. Signed skins: put a `value`/`signature` pair per cast member in
   `config/hearthbound-skins.json`. Every vanilla client shows these, with nothing to download.
   `python tools/skins/mineskin_upload.py config/hearthbound-skins.json` uploads the cast PNGs to
   [MineSkin](https://mineskin.org) (unlisted, no account needed), which has Mojang sign and host them,
   and writes the file for you. This is what my server uses.
2. Resource pack: the cast textures are added to Polymer's resource pack. A player who accepts the
   pack sees the hand-drawn outfits. Polymer's pack hosting must be on for this.
3. Default skins: otherwise each cast member wears one of the game's default skins picked to suit
   them. This is what you get with no extra setup.

## AI on a shared server

AI dialogue is off by default here too. If you turn it on, a few things matter more with friends
around. Every reply is one request, and `ai.maxRequestsPerPlayerPerMinute` (default 6) and
`ai.maxConcurrentRequests` (default 4) cap it. Past the limit, companions just answer with their own
lines, so nothing breaks.

What a player says to a companion goes to the AI provider you configure. Only that player's own
conversation with that one companion is sent (see Privacy above), plus a short description of the
surroundings. Tell your friends if you turn it on.

The easiest setup is Ollama on the server machine. It's free, and nothing leaves that machine.

## Talking to companions on a server

- `@Name` reaches that companion if they are loaded and in your dimension, at any distance.
  `@party` (or `@all`) is everyone with you, `@everyone` is every companion you have, and
  `/companion tell <name|party|everyone> <message>` does the same from the command line. You get
  a short grey report of who heard it and who couldn't.
- Companions in unloaded chunks can't hear you or act. Messages aren't queued: go back near them.
- Other players' companions don't take your orders, and yours don't take theirs.
- `/companion chattiness` and `/companion autonomy` change the world's settings and are saved in
  `config/hearthbound.json`. On a server they need operator rights.
- There's nothing to do after updating: old cast names in saved worlds,
  the roster and config files keyed by cast name (such as `hearthbound-skins.json` and
  `server.hub.residents`) still work. See [CAST.md](CAST.md#renamed-in-04).

## How I tested it

There are 47 in-game server tests, 7 of them for multiplayer: stranger orders refused, stranger
damage blocked, companions never targeting players, protected areas and the build guard, the hub
spawning each resident once and taking them back, replies and per-speaker memory, `@name` in the
middle of a message, and plain chat carrying on a conversation.

I also ran a copy of my own server (Fabric API, Polymer, Universal Graves, Ledger, Lithium,
FerriteCore plus Hearthbound) and joined it with Mojang's official, unmodified 26.3 client, after
checking the client's jar against Mojang's SHA-1. The twelve residents spawned at the hub, rendered
as mannequins with names and the line under them, greeted me in chat, and could be recruited (the
line changed to say who they're with, and they followed). The recruited companion was still in the
party after a server restart. I checked 0.3.0 the same way: the signed skins and the new grey lines
show up correctly for the unmodified client.
