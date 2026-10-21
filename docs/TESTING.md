# Testing

This is how I test the mod, what I've actually checked, and what I haven't got to yet. Last
updated 2026-10-21 (v0.3 preview).

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

### Unit tests (JUnit 5): 219 passing

| Suite | Covers |
|---|---|
| `CommandParserTest` (115) | Every order type, politeness and addressing ("Hazel, could you..."), quantities in digits/words/stacks/dozens, "chop trees" = logs, attack vs gather ("get that zombie" vs "get some logs"), give-all vs give-item, build and dig orders ("dig down 30", "bridge this gap"), "mine some iron" is still gathering, small-talk topics, telling a companion about yourself ("call me Captain", "I hate spiders"), and the things that aren't facts ("I love it here", "call me when you're done"), unsupported actions, non-items ("I need help") |
| `AnswersTest` (42) | Reading replies to a companion's own questions: the thing in "has to be the ocean", a nickname in "just call me the boss", non-answers ("dunno", "you") left alone, mood ("not bad" is good, "not great" is bad), and forgiving `@name` matching (short forms and one-letter typos) |
| `PlayerFactsTest` (6) | Likes match the world ignoring plurals and articles ("diamonds" for diamond ore), changing your mind, keeping the most recent few, the "you go by..., you like..." summary, facts kept per player, and memories stored as full clauses even from old saves |
| `GatherMachineTest` (9) | Success only after full delivery; nothing found (no invented items); partial delivery when supply runs out; unreachable targets skipped then explained; missing tool fails before moving; full pack brings back what fits; cancel is immediate and final; owner leaving keeps the items |
| `MovementPolicyTest` (5) | Follow band, hysteresis, hurry and teleport thresholds, dimension/cliff/stuck teleport, stuck detection windows |
| `LineBankTest` (17) | Every personality has lines for every situation the game uses; placeholders match what code supplies; frequent situations have 3+ variants; personalities don't share lines; selector cycles through all variants and never repeats back-to-back; a filled-in word that starts a sentence is capitalised; articles agree with what fills them ("an apple", "some sweet berries") |
| `AiContractTest` (7) | Model JSON parsing, intent allow-list, count clamping, formatting-code stripping, promise guard, prompt-injection containment (player text, including what they told the companion about themselves, can't close a data block), schema validity |
| `CastMemberTest` (4) | Cast names unique and not reused by wanderers; one woman and one man per personality; every cast member has a valid 64x64 skin; skin variants round-trip |
| `RateLimiterTest` (4) | Per-player AI limit per rolling minute; refused requests don't use up the allowance; 0 means unlimited |
| `CompanionMemoryTest` (3) | Switching conversation partner forgets the previous player's lines but keeps shared events and affinity |
| `HttpModelsTest` (7) | Ollama / OpenAI-compatible / Anthropic request shapes and response parsing against a local fake server; refusal, HTTP 401, timeout and unreachable server all fail cleanly |

### Server GameTests: 32 passing (plus a smoke test)

These run in a real Minecraft server world on every `./gradlew build`:

| Test | What it checks |
|---|---|
| `gatherLogsAndHandThemOver` | Companion walks to a tree, breaks 3 oak logs, returns, and the player's inventory really contains them; "delivered" is said only after hand-over |
| `gatherReportsNothingFound` | Asked for diamonds with none around: explains, returns to following, gives nothing |
| `stoneNeedsAPickaxe` | Without a pickaxe: asks for one; the stone is untouched |
| `cancellingStopsTheTask` | Cancel mid-gather: acknowledged, back to following, and no logs broken afterwards |
| `followerTeleportsWhenLeftBehind` | Owner moved 40 blocks away: companion catches up onto solid ground |
| `essentialCompanionsAreDownedNotKilled` | Lethal damage knocks down instead of killing |
| `guardianRefusesToAttackVillagers` | Personality refusal is enforced by game code |
| `strangersDontTakeOrdersAndPartyIsLimited` | Unrecruited companions refuse orders; the 4th recruit is refused |
| `saveAndLoadKeepsIdentityOrdersAndMemory` | Save/load round trip keeps name, archetype, skin + slim flag, owner, wait order, inventory, conversation, events, and the player's nickname, likes and dislikes |
| `giveHandsOverCarriedItems` / `cannotGiveWhatYouDontHave` | Exactly the requested items are handed over; asking for something they don't carry is refused with a reason |
| `dismissedCompanionsWalkHome` | Dismissal removes them from the party and they walk back to where they were recruited |
| `followersComeThroughDimensions` | A follower (even knocked down) comes through a dimension change; a waiting companion stays |
| `onlyTheOwnerGivesOrders` | Another player's orders (and dismissal) are refused with "I'm with ..."; the owner's name is synced |
| `strangersCantHurtCompanionsAndCompanionsNeverFightPlayers` | A stranger's hit does nothing; companions never target any player |
| `noGatheringInsideProtectedAreasOrNextToBuilds` | Gathering refused in a protected area (with a reason); blocks beside planks are off limits |
| `hubSpawnsResidentsOnceAndTakesThemBack` | The hub spawns each resident once, with gear; the roster counts a recruited one; dismissed ones head home to the hub |
| `summonedCastMembersGetTheirOwnGear` | `/companion summon charlie` gives Charlie a sellsword's kit |
| `repliesReachTheSpeakerAndPeopleNearby` | Conversation memory is per speaker; `@name` mid-message works; mentions of players are ignored |
| `plainChatCarriesOnAConversation` | After one `@name`, plain chat reaches that companion; with another player nearby, only if you're facing them |
| `tunnelThroughStone` | "dig a tunnel 4": the tunnel is really there, it stops where asked, and the stone is in their pack |
| `diggingStopsBeforeLava` | Digging toward lava stops one block short and says why |
| `noPickaxeNoDigging` | Asked to go mining without a pickaxe: refuses and asks for one |
| `buildsAShelterFromPlanks` | 80 planks in the pack: walls, roof and an open doorway appear, and exactly the placed planks are used |
| `refusesToBuildWithoutEnoughBlocks` | 10 blocks for a 71-block shelter: refuses with both numbers; "a castle" is refused with what they can build |
| `bridgesWater` | A bridge across a water channel, three blocks from the pack |
| `aNicknameIsUsedFromThenOn` | "call me Captain" changes what they call you; "don't call me that" changes it back |
| `answersToTheirQuestionsAreRemembered` | A reply to "what's your favourite thing?" is remembered; "nah" to a nickname keeps their own name; a bad mood gets sympathy; an order straight after a question is still an order; "what do you know about me?" says it back |
| `crouchingTwiceGetsACrouchBack` | Two quick crouches while looking at a companion: they crouch back |
| `anAdvancementGetsAReaction` | Completing "Stone Age" gets a line from the nearest companion (this also proves the mixin is applied) |
| `aLevelMilestoneGetsAReaction` | Reaching level 10 gets a reaction |

### Client GameTest: passing

`CompanionClientGameTest` drives the real client:
- renders all 9 default skins with wide arms and with slim arms, plus a side-by-side comparison
  (screenshots in `docs/images/`);
- summons three companions and recruits them through the chat path, then sends "build me a
  castle" (refused, with what they can build instead), "gather 20 diamonds" (accepted, searched, then
  truthfully reported none nearby), and "wait here" (party HUD switches to "waiting here");
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

This one caught real bugs, which I've fixed. Knocked-down followers were left behind on a
dimension change. With no free space next to the player, followers couldn't catch up (now they
step into the player's own position). And companions said "I'm hurt" lines while being killed by
commands.

### Chat path playtest (client): passing

Types `@Charlie join me` into the real chat box (recruits him), then presses the Talk key, which
pre-fills `@Charlie `, and asks "what are you doing?" (correct follow status). Then a free
conversation with Honey: one `@Honey`, then plain messages, including "call me Captain" and "I love
cherry trees", and "what do you know about me?" gets both said back. Last, Cole is handed a pack
of planks, a door and a torch, and builds a shelter in front of the player.

### Cast showcase (client): passing

`CastShowcase` renders the twelve cast members (Dasha, Charlie, Hazel, Birintan, Maddie, AJ, Daisy,
Cole, Honey, Samuel, Luna, Luke) in the real client: front and back line-ups, close-ups of each
group of four, and three of them running. I use it to check seams, outer layers and motion. The
results are in [docs/images/cast](images/cast) and [CAST.md](CAST.md).

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
| Companions asking questions, recalling likes in the world | Needs a long manual session | Answer reading is unit and GameTested; the timing of questions in real play isn't |
| Mining trips in real caves | Needs a manual session | Tunnels, lava and tool checks are GameTested on built test sites |
| Interrupted conversations | Partly verified | New messages supersede pending AI replies (code path); scripted replies are instant |
| Save, quit to title, reload in the real client | Verified (adventure playtest) | |
| Server with unmodded players | Verified on a copy of my real server | Mojang's unmodified 26.3 client joins; mannequins, names, status lines, signed skins, greeting, recruiting and following all work. Details in [SERVER.md](SERVER.md#how-i-tested-it) |
| Player with the mod on that server | Verified (dev client) | Joins and sees companions fine |
| AI dialogue with a real model | **Not tested** | Requires Ollama or an API key; client code is tested against fake servers only |
