package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain chat nobody is named in, read as a job for whoever is around, and the chat that isn't one. */
class TeamRequestTest {
	private static final CommandParser PARSER = new CommandParser(name -> name.equals("zombie"));

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"build a house|HOUSE",
			"let's build a house|HOUSE",
			"Let's build a house!|HOUSE",
			"I need you to build a house|HOUSE",
			"we need a place to live|HOUSE",
			"we need a farm|FARM",
			"we should make a farm|FARM",
			"let's set up camp here|CAMP",
			"can someone build a wall?|WALL",
			"could somebody make us a shelter please|SHELTER",
			"please build a bridge|BRIDGE",
			"time to build a storehouse|STOREHOUSE",
			"can someone get wood|GATHER",
			"I need wood|GATHER",
			"we need some stone|GATHER",
			"someone chop some wood|GATHER",
			"get me 16 oak logs|GATHER"})
	void requestsForWhoeverIsAround(String message, TeamRequest.Kind kind) {
		Optional<TeamRequest.Job> job = TeamRequest.parse(message, PARSER);
		assertTrue(job.isPresent(), message);
		assertEquals(kind, job.get().kind(), message);
	}

	@ParameterizedTest
	@ValueSource(strings = {"I built a house yesterday", "I love building houses", "we don't need a farm", "did you build a house?",
			"I'm going to build a house", "is there a farm", "build something", "build this", "collect that", "build me a castle",
			"we need food", "follow me", "how are you", "lets go mining", "I saw a creeper last night", "we built a farm last week"})
	void butNotStatementsOrVagueOnes(String message) {
		assertEquals(Optional.empty(), TeamRequest.parse(message, PARSER), message);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {"can someone get wood|logs|", "get me 16 oak logs|oak logs|16"})
	void gatherJobsKeepTheItemAndCount(String message, String item, Integer count) {
		TeamRequest.Job job = TeamRequest.parse(message, PARSER).orElseThrow();
		assertEquals(item, job.item());
		assertEquals(count, job.count());
	}
}
