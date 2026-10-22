package io.github.tekless.hearthbound.core.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The shape of a day at home, and how personality and needs move it about. */
class ScheduleTest {
	private static final Schedule.Conditions CALM = Schedule.Conditions.CALM;

	@ParameterizedTest
	@ValueSource(strings = {"sellsword", "naturalist", "rogue", "guardian", "farmhand", "mystic"})
	void everyPersonalityWorksEatsMeetsAndSleeps(String archetype) {
		for (long seed = 0; seed < 50; seed++) {
			Schedule.Traits traits = Schedule.Traits.of(archetype, seed);
			assertEquals(Activity.WORK, Schedule.at(3000, 1, traits, CALM), archetype + " works in the morning");
			assertEquals(Activity.MEAL, Schedule.at(6100, 1, traits, CALM), archetype + " eats at midday");
			assertEquals(Activity.SOCIAL, Schedule.at(11600, 1, traits, CALM), archetype + " meets the others in the evening");
			assertEquals(Activity.SLEEP, Schedule.at(19000, 1, traits, CALM), archetype + " sleeps at night");
		}
	}

	@Test
	void theyGetUpAroundDawnOneAtATime() {
		Set<Integer> wakeTimes = new HashSet<>();
		for (long seed = 0; seed < 12; seed++) {
			Schedule.Traits traits = Schedule.Traits.of("naturalist", seed);
			int wake = Schedule.wakeTime(traits);
			assertTrue(wake >= 23500 || wake <= 500, "up around six: " + wake);
			assertEquals(Activity.WAKE, Schedule.at(wake + 100, 1, traits, CALM), "getting up just after waking");
			wakeTimes.add(wake);
		}
		assertTrue(wakeTimes.size() > 6, "not everyone at once");
	}

	@Test
	void farmhandsRiseEarlyAndMysticsStayUpLate() {
		for (long seed = 0; seed < 20; seed++) {
			Schedule.Traits farmhand = Schedule.Traits.of("farmhand", seed);
			Schedule.Traits mystic = Schedule.Traits.of("mystic", seed);
			assertTrue(Schedule.bedtime(farmhand, CALM) < Schedule.bedtime(mystic, CALM), "the mystic turns in later");
			assertEquals(Activity.SLEEP, Schedule.at(500, 1, mystic, CALM), "and is still asleep at half six");
			assertNotEquals(Activity.SLEEP, Schedule.at(500, 1, farmhand, CALM), "when the farmhand is already up");
		}
	}

	@Test
	void rainSendsEveryoneIndoorsButNotOutOfBed() {
		Schedule.Traits traits = Schedule.Traits.of("guardian", 3);
		Schedule.Conditions rain = new Schedule.Conditions(true, false, 0, 0, 0);
		assertEquals(Activity.SHELTER, Schedule.at(3000, 1, traits, rain));
		assertEquals(Activity.SHELTER, Schedule.at(11600, 1, traits, rain));
		assertEquals(Activity.SLEEP, Schedule.at(19000, 1, traits, rain));
	}

	@Test
	void needsSteerTheDay() {
		Schedule.Traits traits = Schedule.Traits.of("rogue", 5);
		Schedule.Conditions hungry = new Schedule.Conditions(false, false, 90, 0, 0);
		assertEquals(Activity.MEAL, Schedule.at(3000, 1, traits, hungry), "a hungry worker goes for food");
		Schedule.Conditions tired = new Schedule.Conditions(false, false, 0, 95, 0);
		assertTrue(Schedule.bedtime(traits, tired) < Schedule.bedtime(traits, CALM), "a tired one goes to bed early");
		Schedule.Conditions full = new Schedule.Conditions(false, true, 0, 0, 0);
		assertTrue(Schedule.bedtime(traits, full) > Schedule.bedtime(traits, CALM), "nobody goes to bed early at a full moon");
	}

	@Test
	void lonelyPeopleSpendTheirTimeOffWithTheOthers() {
		for (long day = 0; day < 40; day++) {
			Schedule.Traits traits = Schedule.Traits.of("naturalist", 11);
			if (Schedule.at(9000, day, traits, CALM) == Activity.LEISURE) {
				Schedule.Conditions lonely = new Schedule.Conditions(false, false, 0, 0, 80);
				assertEquals(Activity.SOCIAL, Schedule.at(9000, day, traits, lonely));
				return;
			}
		}
		throw new AssertionError("no afternoon off in forty days");
	}

	@Test
	void afternoonsMixWorkAndLeisure() {
		Schedule.Traits traits = Schedule.Traits.of("rogue", 9);
		int leisure = 0;
		for (long day = 0; day < 200; day++) {
			if (Schedule.at(9000, day, traits, CALM) == Activity.LEISURE) {
				leisure++;
			}
		}
		assertTrue(leisure > 70 && leisure < 170, "a rogue takes most afternoons off, not all: " + leisure);
	}

	@Test
	void theMoonIsFullEveryEighthNight() {
		assertTrue(Schedule.fullMoon(0));
		assertTrue(Schedule.fullMoon(8 * 24000L + 13000));
		assertTrue(!Schedule.fullMoon(3 * 24000L));
	}
}
