# Testing

This is how I test the mod, what I've actually checked, and what I haven't got to yet. Last
updated 2026-10-22 (v0.6).

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

### Unit tests (JUnit 5): 339 passing

| Suite | Covers |
|---|---|
| `ChatOrdersParserTest` (53) | "this" and "that" (collect that, fight that, get that), build with no subject, hunt, "go and kill", "I need a shelter", "set up camp", the follow-ups "why?", "really?", "and you?", "tell me more", "what do you mean?", and conversational phrasing landing on the right topic ("what are you good at?", "it's getting dark", "let's go home" is not a dismissal) |
| `CommandParserTest` (114) | Every order type, politeness and addressing ("Hazel, could you..."), quantities in digits/words/stacks/dozens, "chop trees" = logs, attack vs gather ("get that zombie" vs "get some logs"), give-all vs give-item, build and dig orders ("dig down 30", "bridge this gap"), "mine some iron" is still gathering, small-talk topics, telling a companion about yourself ("call me Captain", "I hate spiders"), and the things that aren't facts ("I love it here", "call me when you're done"), unsupported actions, non-items ("I need help") |
| `AnswersTest` (42) | Reading replies to a companion's own questions: the thing in "has to be the ocean", a nickname in "just call me the boss", non-answers ("dunno", "you") left alone, mood ("not bad" is good, "not great" is bad), and forgiving `@name` matching (short forms and one-letter typos) |
| `PlayerFactsTest` (6) | Likes match the world ignoring plurals and articles ("diamonds" for diamond ore), changing your mind, keeping the most recent few, the "you go by..., you like..." summary, facts kept per player, and memories stored as full clauses even from old saves |
| `GatherMachineTest` (9) | Success only after full delivery; nothing found (no invented items); partial delivery when supply runs out; unreachable targets skipped then explained; missing tool fails before moving; full pack brings back what fits; cancel is immediate and final; owner leaving keeps the items |
| `MovementPolicyTest` (5) | Follow band, hysteresis, hurry and teleport thresholds, dimension/cliff/stuck teleport, stuck detection windows |
| `ScheduleTest` (13) | Every personality works in the morning, eats at noon, meets the others in the evening and sleeps at night (fifty different companions each); waking is spread over dawn; farmhands rise early and mystics stay up; rain sends everyone indoors but nobody out of bed; hunger, tiredness and a full moon move the day; lonely people spend time off with the others; afternoons mix work and leisure; the moon is full every eighth night |
| `NeedsAndGrowthTest` (6) | Hunger builds and a meal puts it right; sleep rests and company cheers; how needs change chattiness; crops grow about a stage a quarter day on wet farmland; harvests only happen when allowed and are capped; bonus drops; most neighbours warm to each other and clashing ones drift apart |
| `TeamRequestTest` (35) | Plain requests with nobody named ("let's build a house", "we need a farm", "can someone get wood", "let's set up camp here") and the job each one is; statements and vague ones that must start nothing ("I built a house yesterday", "I love building houses", "we don't need a farm", "build something"); gather jobs keep the item and count |
| `LineBankTest` (19) | Every personality has lines for every situation the game uses, including the 0.4 keys (questions, mentions, role remarks, chats between companions, routines); per-character files only use placeholders the game supplies; placeholders match what code supplies; frequent situations have 3+ variants; personalities don't share lines; selector cycles through all variants and never repeats back-to-back; a filled-in word that starts a sentence is capitalised; articles agree with what fills them ("an apple", "some sweet berries") |
| `AiContractTest` (7) | Model JSON parsing, intent allow-list, count clamping, formatting-code stripping, promise guard, prompt-injection containment (player text, including what they told the companion about themselves, can't close a data block), schema validity |
| `CastMemberTest` (6) | Cast names unique and not reused by wanderers; every role has at least two characters; old names still find the right character; roles are fair (never below 80%); every cast member has a valid 64x64 skin; skin variants round-trip |
| `RateLimiterTest` (4) | Per-player AI limit per rolling minute; refused requests don't use up the allowance; 0 means unlimited |
| `CompanionMemoryTest` (3) | Switching conversation partner forgets the previous player's lines but keeps shared events and affinity |
| `HttpModelsTest` (7) | Ollama / OpenAI-compatible / Anthropic request shapes and response parsing against a local fake server; refusal, HTTP 401, timeout and unreachable server all fail cleanly |

### Server GameTests: 81 passing (plus a smoke test)

These run in a real Minecraft server world on every `./gradlew build`:

| Test | What it checks |
|---|---|
| `gatherLogsAndHandThemOver` | Companion walks to a tree, breaks 3 oak logs, returns, and the player's inventory really contains them; "delivered" is said only after hand-over |
| `gatherReportsNothingFound` | Asked for diamonds with none around: explains, returns to following, gives nothing |
| `stoneNeedsAPickaxe` | Without a pickaxe: asks for one; the stone is untouched |
| `cancellingStopsTheTask` | Cancel mid-gather: acknowledged, back to following, and no logs broken afterwards |
| `followerTeleportsWhenLeftBehind` | Owner moved 40 blocks away: companion catches up onto solid ground |
| `essentialCompanionsAreDownedNotKilled` | Lethal damage knocks down instead of killing |
| `guardianRefusesToAttackVillagers` | The ethical refusal (no villagers) is enforced by game code, for every companion |
| `strangersDontTakeOrdersAndPartyIsLimited` | Unrecruited companions don't take orders directly (in chat they offer to join first); the 4th recruit is refused |
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
| `chatOrderReachesTheRightCompanion` | Three companions, `@name wait here` reaches only that one; then plain "follow me" goes to the one you're talking to |
| `buildThisAsksOneQuestionThenBuilds` | "build this" gets "which one?", nothing starts, then "a shelter please" starts a shelter |
| `collectThatGathersTheBlockYouAreLookingAt` | Looking at an oak log and saying "collect that" starts gathering oak logs |
| `collectThatWithNothingInSightAsksWhat` | Nothing in sight: they ask what, and the answer ("8 cobblestone") is used |
| `stopAndReplaceTasks` | "stop" cancels the task for real, and a new order cancels the old one |
| `strangerOffersToJoinThenDoesTheJob` | A stranger asked to gather offers to join; "yes" recruits them and starts the job |
| `farCompanionComesBeforeWorking` | Asked to gather from 40 blocks away: they say they're coming and travel first |
| `everyoneHearsAtOnce` | `@everyone` and `@party` both reach every companion you have |
| `followUpsLeanOnTheTopic` | "tell me more" stays on the story, "why?" gets a why, a mentioned creeper is picked up |
| `farmerHarvestsAndReplantsOnlyWhenAutonomyIsOn` | Nothing by itself with autonomy off; with it on, a ripe crop is harvested, replanted, and the harvest is in the pack |
| `routinesLeaveProtectedAndBuiltAreasAlone` | A log beside planks is left alone; a lone log is fair game |
| `buildersLightDarkSpotsOnlyWithTorches` | No torches in the pack, no torch job |
| `buildsACampWithAFire` | No campfire: refused with the reason; with one, a campfire and seats appear |
| `companionsChatToEachOther` | A short exchange between two companions: opening line, then the answer |
| `oldCastNamesLoadAsTheNewCharacters` / `aCustomNameOnACastSkinIsLeftAlone` | Companions saved under the pre-0.4 names (with their old voices) load as the new characters with the right voice and role; a custom name on a cast skin is left alone |
| `whyAfterARefusalGivesTheRealReason` | Refused to build for lack of blocks, then "why?" gets the same real reason again |
| `everyoneObeyedWhenObeyIsOn` / `strangersIgnoreGroupOrdersWithoutObey` | "Everyone, come here" and "everyone, follow me" with three strangers; one answers; nothing happens without obey mode |
| `roleGroupsReachOnlyThatRole` | "Builders, ...", `@farmers ...` and "all my builders, ..." reach only that role |
| `sendCompanionsHomeAndTeleportThem` / `homeCommandsRespectObey` | sethome, "go home" walks, "teleport home" teleports, and strangers only obey with obey mode on |
| `hubHomeWorksFromTheConsoleAndRespawnsOnRequest` | Console `hub list` and `hub home`; a cast member with no body is respawned when asked |
| `directedFightEndsWhenOneIsKnockedDown` / `companionsNeverStartFightsOrAttackTheirOwner` / `fightingAPlayerIsRefusedWhenPvpIsOff` | Fights only on command, never the owner, refused when PvP is off, over when someone is down |
| `craftingOnlyUsesWhatIsCarried` | Planks, sticks, chest, doors, furnace, torches and hoe from the pack, and nothing from an empty one |
| `autonomyAppliesToCompanionsWhoAreNotFollowing` / `wanderersChatToEachOtherWhenChattinessIsUp` | Wanderers chop a log by themselves and chat without a hub |
| `buildersBuildAHouseEachFromTheFirstMessage` | The first example message, verbatim: two houses with doors, a collector sent to gather |
| `aBuilderShortOfBlocksGathersLogsCraftsPlanksThenBuilds` / `noMaterialsMeansAnHonestBlockerAndNoHouse` | Short of blocks: chops, crafts, builds. No wood anywhere: says what is missing and builds nothing |
| `farmersBuildAFarmAndABuilderMakesTheStorehouse` | The second example message, verbatim: a farm is sown, ripe wheat is harvested and put into a storehouse chest |
| `aBuilderMakesAStorehouseWithAChestAndAFurnace` / `aFarmWithNoWaterIsReportedNotFaked` | A storehouse gets its chest and furnace; no water means no farm and a clear message |
| `crouchingTwiceGetsACrouchBack` | Two quick crouches while looking at a companion: they crouch back |
| `anAdvancementGetsAReaction` | Completing "Stone Age" gets a line from the nearest companion (this also proves the mixin is applied) |
| `aLevelMilestoneGetsAReaction` | Reaching level 10 gets a reaction |
| `theScheduleFollowsTheClock` | A farmhand at home goes through work, a meal, the evening at the meeting spot and bed as the clock moves |
| `aResidentSleepsInTheirOwnBed` | At night a resident finds the bed near home, lies down in it (the vanilla sleeping pose) and it is theirs |
| `ordersComeBeforeTheSchedule` | A sleeping resident told to come here (obey mode) gets up and comes, then goes back to their day |
| `setplaceMarksTheSpotYouStandOn` | `/companion setplace storage`, `bed`, `farm` and `commons` mark the chest, the bed, the farmland and the meeting spot |
| `catchUpHarvestsTheFarmIntoTheStorageChestOnly` | Three days away: the farm is harvested and replanted, every bit of reported wheat is in the storage chest, the other chest and the crop outside the farm are untouched, and the summary names who did it |
| `theMeetingFireIsLitAtDusk` | A resident out for the evening lights the campfire at the meeting spot |
| `aFavourIsPaidFromTheirOwnPack` | A collector asks a favour only with something to give, takes the logs, and pays with the emerald from their own pack |
| `aFarmerInANewWorldStartsAFarmByTheWater` | Bare grass and a pool of water: with autonomy on, a farmer with seeds and a hoe starts a farm of their own |
| `withNothingToWorkWithTheySayWhatTheyNeedOnce` | No farm and autonomy off: the farmer says what they'd need, once a day, and carries on |
| `theHostCommandsEveryoneIncludingOtherPlayersCompanions` | The host's obey mode reaches a companion with nobody and a friend's companion; the friend's stays the friend's and comes back when called; "join me" is still refused |
| `operatorsCanUseObeyButOtherPlayersCannot` | A player without operator rights can't turn obey on (by command or otherwise) and is ignored; an operator can, and is obeyed |
| `everyKindOfOrderWorksInObeyMode` | Wait, come, status, gather, stop, build, dig, give and go home for a companion with nobody and for a friend's companion; attack and follow; the host can send someone else's companion home |
| `thePartyLimitCanBeLiftedByTheHostButNotByOthers` | A guest can't change the limit; the host lifts it and recruits four; one player can be given their own limit |
| `aPlainRequestBecomesOneHouseBuiltTogether` | "let's build a house" with two builders and a collector nearby: one plan, one shared blueprint, the collector brings wood, and one house stands with more blocks than the builders started with |
| `aStatementStartsNoWork` | "I built a house yesterday", "I love building houses" and "we don't need a farm" change nothing |

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
