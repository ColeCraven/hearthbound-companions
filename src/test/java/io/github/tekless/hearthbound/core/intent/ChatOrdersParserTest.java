package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** "This", "that" and follow-ups: the wording the game resolves from what the player looks at or just said. */
class ChatOrdersParserTest {
	private final CommandParser parser = new CommandParser(Set.of("zombie", "skeleton", "creeper", "cow", "pig")::contains);

	private Intent intent(String message) {
		ParseResult result = parser.parse(message, List.of("Hazel"));
		return assertInstanceOf(ParseResult.Command.class, result, message).intent();
	}

	@ParameterizedTest
	@CsvSource({"collect that", "gather this", "can you collect that for me", "get more of this", "fetch those", "grab it", "mine that"})
	void pointingAtSomethingToCollectIsAGatherOfThat(String message) {
		Intent intent = intent(message);
		assertEquals(IntentType.GATHER, intent.type(), message);
		assertEquals("that", intent.item(), message);
	}

	@Test
	void namingTheItemStillWins() {
		assertEquals("stone", intent("collect that stone").item());
		assertEquals(20, intent("collect 20 logs").count());
	}

	@ParameterizedTest
	@CsvSource({"fight that enemy", "attack the monsters", "kill it", "fight that thing", "deal with the nearest enemy", "attack that"})
	void fightingSomethingUnnamedMeansTheOneInFront(String message) {
		Intent intent = intent(message);
		assertEquals(IntentType.ATTACK, intent.type(), message);
		assertNull(intent.target(), message);
	}

	@Test
	void fightingANamedCreatureKeepsItsName() {
		assertEquals("zombie", intent("kill the nearest zombie").target());
		assertEquals("pigs", intent("hunt pigs").target());
	}

	@ParameterizedTest
	@CsvSource({"build this", "build something", "can you build something nice", "construct that"})
	void buildingWithoutSayingWhatIsStillABuildRequest(String message) {
		assertEquals(IntentType.BUILD, intent(message).type(), message);
	}

	@ParameterizedTest
	@CsvSource({"why?,WHY", "but why,WHY", "really?,REALLY", "are you sure,REALLY", "and you?,AND_YOU", "what about you,AND_YOU",
			"tell me more,MORE", "go on,MORE", "what do you mean?,MEAN", "huh?,MEAN"})
	void followUpsAreTopicsOfTheirOwn(String message, TalkTopic topic) {
		ParseResult result = parser.parse(message, List.of("Hazel"));
		assertEquals(topic, assertInstanceOf(ParseResult.Talk.class, result, message).topic(), message);
	}

	@ParameterizedTest
	@CsvSource({"come on over here,COME", "get over here,COME", "lets build something,BUILD", "I need a shelter,BUILD", "I want a bridge,BUILD",
			"set up camp,BUILD", "go fight that skeleton,ATTACK", "go and kill the spiders,ATTACK", "hunt some pigs,ATTACK",
			"dig that out,GATHER", "break that block,GATHER", "dig for coal,GATHER"})
	void everydayPhrasingIsUnderstood(String message, IntentType type) {
		assertEquals(type, intent(message).type(), message);
	}

	@Test
	void getThatIsLeftForTheGameToDecide() {
		Intent intent = intent("get that");
		assertEquals(IntentType.ATTACK, intent.type());
		assertEquals("get", intent.item());
		assertEquals("coal", intent("dig for coal").item());
		assertEquals("skeleton", intent("go fight that skeleton").target());
	}
}
