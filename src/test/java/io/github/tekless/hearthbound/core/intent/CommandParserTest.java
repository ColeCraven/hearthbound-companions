package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		ParseResult result = parser.parse(message, List.of("Wren", "Bram"));
		return assertInstanceOf(ParseResult.Command.class, result, () -> "Expected a command for: " + message + " but got " + result).intent();
	}

	@ParameterizedTest
	@CsvSource({
			"follow me, FOLLOW",
			"'Wren, follow me please', FOLLOW",
			"@Wren come with me, FOLLOW",
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
			"go home, DISMISS",
			"I don't need you anymore, DISMISS"
	})
	void recognisesSimpleOrders(String message, IntentType expected) {
		assertEquals(expected, command(message).type());
	}

	@Test
	void gatherWithCountAndItem() {
		Intent intent = command("Bram, could you gather 16 oak logs for me?");
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
			"hi Wren, HELLO",
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
			"do you remember the creeper, MEMORY"
	})
	void recognisesSmallTalk(String message, TalkTopic topic) {
		ParseResult result = parser.parse(message, List.of("Wren"));
		assertEquals(topic, assertInstanceOf(ParseResult.Talk.class, result, () -> message + " -> " + result).topic());
	}

	@Test
	void unsupportedActionsAreNamed() {
		ParseResult result = parser.parse("build me a house", List.of());
		assertEquals("build", assertInstanceOf(ParseResult.Unsupported.class, result).action());
		assertInstanceOf(ParseResult.Unsupported.class, parser.parse("craft a sword", List.of()));
	}

	@Test
	void needingHelpIsNotAGatherRequest() {
		ParseResult result = parser.parse("I need help", List.of());
		assertInstanceOf(ParseResult.Unrecognised.class, result);
	}

	@Test
	void freeFormChatIsUnrecognised() {
		assertInstanceOf(ParseResult.Unrecognised.class, parser.parse("the sunset is pretty tonight", List.of()));
	}
}
