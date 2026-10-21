package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandParserTest {
	private final CommandParser parser = new CommandParser(Set.of("zombie", "skeleton", "creeper", "cow", "villager", "cave spider")::contains);

	private Intent command(String message) {
		ParseResult result = parser.parse(message, List.of("Hazel", "Charlie"));
		return assertInstanceOf(ParseResult.Command.class, result, () -> "Expected a command for: " + message + " but got " + result).intent();
	}

	@ParameterizedTest
	@CsvSource({
			"follow me, FOLLOW",
			"'Hazel, follow me please', FOLLOW",
			"@Hazel come with me, FOLLOW",
			"let's go, FOLLOW",
			"stay close, FOLLOW",
			"wait here, WAIT",
			"stay put, WAIT",
			"hold position, WAIT",
			"don't move, WAIT",
			"stop following me, WAIT",
			"guard this place, WAIT",
			"come here, COME",
			"get over here, COME",
			"stop, CANCEL",
			"cancel that, CANCEL",
			"never mind, CANCEL",
			"stop gathering, CANCEL",
			"what are you doing?, STATUS",
			"how's the gathering going, STATUS",
			"show me your inventory, INVENTORY",
			"let's trade, INVENTORY",
			"will you join me?, RECRUIT",
			"would you like to join my party, RECRUIT",
			"I could use your help, RECRUIT",
			"you're dismissed, DISMISS",
			"you can go home now, DISMISS",
			"go home, HOME",
			"I don't need you anymore, DISMISS"
	})
	void recognisesSimpleOrders(String message, IntentType expected) {
		assertEquals(expected, command(message).type());
	}

	@Test
	void gatherWithCountAndItem() {
		Intent intent = command("Charlie, could you gather 16 oak logs for me?");
		assertEquals(IntentType.GATHER, intent.type());
		assertEquals("oak logs", intent.item());
		assertEquals(16, intent.count());
	}

	@ParameterizedTest
	@CsvSource({
			"get me a stack of cobblestone, cobblestone, 64",
			"fetch half a stack of sand, sand, 32",
			"I need twenty five iron ore, iron ore, 25",
			"collect a dozen birch logs nearby, birch logs, 12",
			"mine x8 coal, coal, 8",
			"go get two stacks of dirt, dirt, 128",
			"grab a couple of flowers, flowers, 2",
			"bring me a log, log, 1"
	})
	void parsesQuantities(String message, String item, int count) {
		Intent intent = command(message);
		assertEquals(IntentType.GATHER, intent.type());
		assertEquals(item, intent.item());
		assertEquals(count, intent.count());
	}

	@Test
	void gatherWithoutCountLeavesCountUnset() {
		Intent intent = command("get some sand");
		assertEquals("sand", intent.item());
		assertNull(intent.count());
	}

	@Test
	void chopTreesMeansLogs() {
		assertEquals("logs", command("chop some trees").item());
		assertEquals("logs", command("chop down a tree").item());
		assertEquals("logs", command("gather wood").item());
	}

	@Test
	void gatherWithoutItemIsUnclear() {
		ParseResult result = parser.parse("gather", List.of());
		assertEquals(ParseResult.UnclearReason.GATHER_WHAT, assertInstanceOf(ParseResult.Unclear.class, result).reason());
	}

	@Test
	void getAMobIsAnAttackNotAGather() {
		Intent intent = command("get that zombie");
		assertEquals(IntentType.ATTACK, intent.type());
		assertEquals("zombie", intent.target());
		assertEquals(IntentType.ATTACK, command("attack it").type());
		assertNull(command("attack it").target());
		assertEquals(IntentType.GATHER, command("get some logs").type());
	}

	@Test
	void giveEverythingOrSpecificItems() {
		Intent all = command("give me everything");
		assertEquals(IntentType.GIVE, all.type());
		assertNull(all.item());
		Intent logs = command("hand over the logs");
		assertEquals("logs", logs.item());
		Intent some = command("give me 5 cobblestone");
		assertEquals("cobblestone", some.item());
		assertEquals(5, some.count());
	}

	@ParameterizedTest
	@CsvSource({
			"hello, HELLO",
			"hi Hazel, HELLO",
			"hi there!, HELLO",
			"hey there, HELLO",
			"how are you?, HOW",
			"who are you, WHO",
			"tell me a joke, JOKE",
			"thanks!, THANKS",
			"good job, COMPLIMENT",
			"you're useless, INSULT",
			"goodbye, BYE",
			"where are we, WHERE",
			"what's your favourite thing, LIKES",
			"what are you afraid of, DISLIKES",
			"do you remember the creeper, MEMORY",
			"tell me about your family, STORY",
			"where did you grow up, STORY",
			"isnt it beautiful, PLACE",
			"look at that sunset, PLACE",
			"what should we do now, PLAN",
			"im bored, PLAN",
			"yeah, YES",
			"sure, YES",
			"nope, NO",
			"haha, LAUGH",
			"lol thats funny, LAUGH",
			"i love you, FONDNESS",
			"glad youre here, FONDNESS",
			"im sad, SAD",
			"i died again, SAD",
			"are you hungry, FOOD",
			"why is the sky blue?, QUESTION",
			"do dragons exist, QUESTION",
			"what can you do, HELP",
			"help, HELP",
			"what do you know about me, ABOUT_ME",
			"whats my name?, ABOUT_ME",
			"are we friends, BOND",
			"do you like me?, BOND",
			"what do you think of me, BOND",
			"i like having you around, FONDNESS",
			"i dont like you, INSULT"
	})
	void recognisesSmallTalk(String message, TalkTopic topic) {
		ParseResult result = parser.parse(message, List.of("Hazel"));
		assertEquals(topic, assertInstanceOf(ParseResult.Talk.class, result, () -> message + " -> " + result).topic());
	}

	@ParameterizedTest
	@CsvSource({
			"call me Captain, NICKNAME, Captain",
			"'Hazel, my name is sam', NICKNAME, Sam",
			"I love the ocean, LIKE, the ocean",
			"i really like diamonds a lot, LIKE, diamonds",
			"my favourite colour is blue, LIKE, blue",
			"cake is my favourite, LIKE, cake",
			"i love chopping trees, LIKE, chopping trees",
			"I hate spiders, DISLIKE, spiders",
			"im scared of the dark, DISLIKE, the dark",
			"i cant stand rain, DISLIKE, rain",
			"dont call me that, FORGET_NAME, ''"
	})
	void playersTellCompanionsAboutThemselves(String message, FactKind kind, String value) {
		ParseResult result = parser.parse(message, List.of("Hazel"));
		ParseResult.Share share = assertInstanceOf(ParseResult.Share.class, result, () -> message + " -> " + result);
		assertEquals(kind, share.kind());
		assertEquals(value, share.value());
	}

	@Test
	void talkAboutTheCompanionOrTheMomentIsNotAFact() {
		for (String message : List.of("i love it here", "call me when youre done", "i hate it", "i like that idea")) {
			ParseResult result = parser.parse(message, List.of());
			assertFalse(result instanceof ParseResult.Share, message + " -> " + result);
		}
		for (String message : List.of("i hate it when you dig down", "i love watching you chop trees", "i like it when you follow me")) {
			ParseResult result = parser.parse(message, List.of());
			assertFalse(result instanceof ParseResult.Command, "talking about an order isn't giving one: " + message + " -> " + result);
		}
	}

	@Test
	void unsupportedActionsAreNamed() {
		ParseResult result = parser.parse("craft a sword", List.of());
		assertEquals("craft", assertInstanceOf(ParseResult.Unsupported.class, result).action());
		assertEquals("make", assertInstanceOf(ParseResult.Unsupported.class, parser.parse("make a cake", List.of())).action());
	}

	@ParameterizedTest
	@CsvSource({
			"build me a house, house",
			"build a wall, wall",
			"could you build us a bridge, bridge",
			"bridge this gap, bridge",
			"make a shelter, shelter",
			"build a castle, castle"
	})
	void buildOrders(String message, String thing) {
		Intent intent = command(message);
		assertEquals(IntentType.BUILD, intent.type());
		assertEquals(thing, intent.item());
	}

	@ParameterizedTest
	@CsvSource({
			"go mining, trip, ",
			"lets go mining, trip, ",
			"mine here, trip, ",
			"dig a tunnel, tunnel, ",
			"dig a tunnel 20 blocks long, tunnel, 20",
			"dig down, down, ",
			"dig down 30, down, 30",
			"dig a staircase, down, "
	})
	void digOrders(String message, String shape, Integer length) {
		Intent intent = command(message);
		assertEquals(IntentType.MINE, intent.type());
		assertEquals(shape, intent.target());
		assertEquals(length, intent.count());
	}

	@Test
	void miningAnOreIsStillGathering() {
		assertEquals(IntentType.GATHER, command("mine some iron").type());
		assertEquals(IntentType.GATHER, command("mine x8 coal").type());
		assertEquals(IntentType.GATHER, command("dig up some clay").type());
	}

	@Test
	void needingHelpIsNotAGatherRequest() {
		ParseResult result = parser.parse("I need help", List.of());
		assertInstanceOf(ParseResult.Unrecognised.class, result);
	}

	@Test
	void freeFormChatIsUnrecognised() {
		assertInstanceOf(ParseResult.Unrecognised.class, parser.parse("my cousin makes terrible soup", List.of()));
	}
}
