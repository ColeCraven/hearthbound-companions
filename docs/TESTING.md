# Testing

How Hearthbound Companions is tested, and an honest record of what is **verified** versus what
**still needs testing**. Last updated: 2026-10-20 (v0.1 preview).

## How to run

```bash
./gradlew build
```

```bash
./gradlew runClientGameTest
```

`build` runs the unit tests and the server GameTests. `runClientGameTest` opens a game window,
runs a scripted session and saves screenshots to `build/run/clientGameTest/screenshots`.

## Automated tests

### Unit tests (JUnit 5): 106 passing

| Suite | Covers |
|---|---|
| `CommandParserTest` (58) | Every order type, politeness and addressing ("Wren, could you..."), quantities in digits/words/stacks/dozens, "chop trees" = logs, attack vs gather ("get that zombie" vs "get some logs"), give-all vs give-item, small-talk topics, unsupported actions ("build me a house"), non-items ("I need help") |
| `GatherMachineTest` (9) | Success only after full delivery; nothing found (no invented items); partial delivery when supply runs out; unreachable targets skipped then explained; missing tool fails before moving; full pack brings back what fits; cancel is immediate and final; owner leaving keeps the items |
| `MovementPolicyTest` (5) | Follow band, hysteresis, hurry and teleport thresholds, dimension/cliff/stuck teleport, stuck detection windows |
| `LineBankTest` (16) | Every personality has lines for every situation the game uses; placeholders match what code supplies; frequent situations have 3+ variants; personalities don't share lines; selector cycles through all variants and never repeats back-to-back |
| `AiContractTest` (7) | Model JSON parsing, intent allow-list, count clamping, formatting-code stripping, promise guard, prompt-injection containment (player text can't close a data block), schema validity |
| `CastMemberTest` (4) | Cast names unique and not reused by wanderers; one woman and one man per personality; every cast member has a valid 64x64 skin; skin variants round-trip |
| `HttpModelsTest` (7) | Ollama / OpenAI-compatible / Anthropic request shapes and response parsing against a local fake server; refusal, HTTP 401, timeout and unreachable server all fail cleanly |

### Server GameTests: 14 passing (plus a smoke test)

These run in a real Minecraft server world on every `./gradlew build`:

| Test | Verifies |
|---|---|
| `gatherLogsAndHandThemOver` | Companion walks to a tree, breaks 3 oak logs, returns, and the player's inventory really contains them; "delivered" is said only after hand-over |
| `gatherReportsNothingFound` | Asked for diamonds with none around: explains, returns to following, gives nothing |
| `stoneNeedsAPickaxe` | Without a pickaxe: asks for one; the stone is untouched |
| `cancellingStopsTheTask` | Cancel mid-gather: acknowledged, back to following, and no logs broken afterwards |
| `followerTeleportsWhenLeftBehind` | Owner moved 40 blocks away: companion catches up onto solid ground |
| `essentialCompanionsAreDownedNotKilled` | Lethal damage knocks down instead of killing |
| `guardianRefusesToAttackVillagers` | Personality refusal is enforced by game code |
| `strangersDontTakeOrdersAndPartyIsLimited` | Unrecruited companions refuse orders; the 4th recruit is refused |
| `saveAndLoadKeepsIdentityOrdersAndMemory` | Save/load round trip keeps name, archetype, skin + slim flag, owner, wait order, inventory, conversation and events |
| `giveHandsOverCarriedItems` / `cannotGiveWhatYouDontHave` | Exactly the requested items are handed over; asking for something they don't carry is refused with a reason |
| `dismissedCompanionsWalkHome` | Dismissal removes them from the party and they walk back to where they were recruited |
| `followersComeThroughDimensions` | A follower (even knocked down) comes through a dimension change; a waiting companion stays |

### Client GameTest: passing

`CompanionClientGameTest` drives the real client:
- renders all 9 default skins with wide arms and with slim arms, plus a side-by-side comparison
  (screenshots in `docs/images/`);
- summons three companions and recruits them **through the chat path**, then sends "build me a
  castle" (refused, in character), "gather 20 diamonds" (accepted, searched, then truthfully reported
  none nearby), and "wait here" (party HUD switches to "waiting here");
- grows trees and sends a companion for 6 logs.

### Adventure playtest (client, normally generated world): passing

`AdventurePlaytest` runs in a real generated world (seed "hearthbound") with mobs and day/night,
and prints a report. Latest run:

| Check | Result |
|---|---|
| Follow across 6 terrain hops of ~10 blocks | Both companions 2-4 blocks away after every hop; no teleports needed |
| Gather 4 logs from trees on real terrain | Delivered 4 logs; "delivered" only said after hand-over. (An earlier run with awkwardly placed trees correctly reported "unreachable" and claimed nothing.) |
| Night fight, 3 zombies | Nobody died; the aggressive Sellsword fought until knocked down, the defensive Naturalist stayed out of it |
| Interrupted order ("gather 10 sand" -> "never mind" -> "what are you doing?") | Order really cancelled; status reply matched reality |
| Travel to the Nether and back | The follower (knocked down at the time) came along both ways; the waiting companion stayed put |
| Save, quit to title, reopen the world | Both companions kept personality, skin, follow/wait mode, equipment and conversation memory |

Bugs this playtest found and fixed: knocked-down followers were left behind on dimension change;
with no free space next to the player, followers couldn't catch up (they now step into the player's
own position); companions said "I'm hurt" lines while being killed by commands.

### Chat path playtest (client): passing

Types `@Bram join me` into the real chat box (recruits him), then presses the Talk key, which
pre-fills `@Bram `, and asks "what are you doing?" (correct follow status).

### Cast showcase (client): passing

`CastShowcase` renders the twelve cast members in the real client - front and back line-ups,
close-ups of each group of four, and three of them running - for visual review of seams, outer
layers and motion. Results are in [docs/images/cast](images/cast) and [CAST.md](CAST.md).

## Manual playtests

| Area | Status | Notes |
|---|---|---|
| Skins and models | Verified (client test screenshots) | All 18 looks render; slim arms visibly narrower |
| Recruit / talk / refuse | Verified (client test) | Personality voices clearly distinct |
| Party HUD | Verified (client test) | Shows task and health; updates on order change |
| Gathering trees in a real world | See below | |
| Navigation over rough terrain | Verified (adventure playtest) | Doors, rivers and caves not yet specifically tested |
| Night combat | Verified (adventure playtest) | Creeper avoidance and retreat not yet observed in a long session |
| Dimension travel with followers | Verified by command teleport (adventure playtest + GameTest) | Not yet walked through a real portal by hand |
| Pastimes, suggestions, banter, gifts | Needs a long manual session | Code paths compiled and line coverage tested |
| Interrupted conversations | Partly verified | New messages supersede pending AI replies (code path); scripted replies are instant |
| Save, quit to title, reload in the real client | Verified (adventure playtest) | |
| AI dialogue with a real model | **Not tested** | Requires Ollama or an API key; client code is tested against fake servers only |
