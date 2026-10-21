package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Replies to a companion's own questions: what gets remembered, and what is just conversation. */
class AnswersTest {
	@ParameterizedTest
	@CsvSource({
			"diamonds, diamonds",
			"probably diamonds, diamonds",
			"has to be the ocean, the ocean",
			"oh definitely cake!, cake",
			"I love cats a lot, cats",
			"spiders too, spiders",
			"my favourite is sunsets, sunsets",
			"I guess emeralds, emeralds"
	})
	void picksTheThingOutOfAnAnswer(String reply, String thing) {
		assertEquals(Optional.of(thing), Answers.thing(reply));
	}

	@ParameterizedTest
	@ValueSource(strings = {"idk", "dunno", "not telling", "nothing", "you", "it here", "this is a much longer answer than a thing would ever be"})
	void nonAnswersAndSentencesAreNotRemembered(String reply) {
		assertTrue(Answers.thing(reply).isEmpty(), reply);
	}

	@ParameterizedTest
	@CsvSource({
			"steve, Steve",
			"call me Captain, Captain",
			"just call me the boss, The Boss",
			"my name is Sam, Sam",
			"it's Jo please, Jo",
			"i go by Red, Red"
	})
	void readsANickname(String reply, String name) {
		assertEquals(Optional.of(name), Answers.nickname(reply));
	}

	@ParameterizedTest
	@ValueSource(strings = {"yes", "whatever", "hi", "call me later", "my friends have so many names for me honestly", "dunno"})
	void notEveryReplyIsAName(String reply) {
		assertFalse(Answers.nickname(reply).isPresent(), reply);
	}

	@ParameterizedTest
	@CsvSource({
			"pretty good actually, GOOD",
			"not bad, GOOD",
			"fine thanks, GOOD",
			"not great, BAD",
			"tired, BAD",
			"could be better, BAD",
			"i just found a cave, NEUTRAL"
	})
	void readsTheMood(String reply, Answers.Sentiment mood) {
		assertEquals(mood, Answers.sentiment(reply));
	}

	@ParameterizedTest
	@CsvSource({
			"ros, rosie, true",
			"rosi, rosie, true",
			"rsoie, rosie, true",
			"rosiee, rosie, true",
			"ro, rosie, false",
			"well, nell, true",
			"bob, rosie, false",
			"zzzz, cass, false"
	})
	void forgivingNames(String typed, String name, boolean matches) {
		assertEquals(matches, NameMatch.closeEnough(typed, name));
	}
}
