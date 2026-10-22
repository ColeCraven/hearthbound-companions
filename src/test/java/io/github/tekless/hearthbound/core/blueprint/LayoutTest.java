package io.github.tekless.hearthbound.core.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Turning and placing a saved build, and its names. Compass numbers: 0 south, 1 west, 2 north, 3 east. */
class LayoutTest {
	@Test
	void turnsTakeTheSavedFacingToTheFacingNow() {
		assertEquals(0, Layout.turnsBetween(2, 2));
		assertEquals(1, Layout.turnsBetween(2, 3), "north to east is one quarter turn clockwise");
		assertEquals(2, Layout.turnsBetween(0, 2));
		assertEquals(3, Layout.turnsBetween(0, 3), "south to east is three quarter turns clockwise");
	}

	@Test
	void aQuarterTurnTakesNorthToEast() {
		Layout.Offset north = Layout.step(2);
		assertEquals(Layout.step(3), Layout.turn(north, 1));
		assertEquals(Layout.step(0), Layout.turn(north, 2));
		assertEquals(Layout.step(1), Layout.turn(north, 3));
		assertEquals(new Layout.Offset(4, 7, 5), Layout.turn(new Layout.Offset(4, 7, 5), 4), "four turns go all the way round, height untouched");
	}

	@Test
	void theBuildStartsTwoAheadAndIsCentredOnThePlayer() {
		// A row of three along x, saved facing south, built facing east: it should run along z, two blocks ahead.
		List<Layout.Offset> turned = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			turned.add(Layout.turn(new Layout.Offset(x, 0, 0), Layout.turnsBetween(0, 3)));
		}
		Layout.Offset anchor = Layout.anchor(turned, 3, 2);
		List<Integer> zs = new ArrayList<>();
		for (Layout.Offset o : turned) {
			assertEquals(2, anchor.x() + o.x(), "every block is two ahead of the player");
			zs.add(anchor.z() + o.z());
		}
		zs.sort(Integer::compare);
		assertEquals(List.of(-1, 0, 1), zs, "and centred on them");
	}

	@Test
	void namesAreCleanedAndFoundInRequests() {
		assertEquals(Optional.of("my_house"), Layout.cleanName("My House!"));
		assertEquals(Optional.empty(), Layout.cleanName("!!!"));
		assertEquals("big house", Layout.spoken("big_house"));
		List<String> names = List.of("house", "big_house", "tower");
		assertEquals(Optional.of("big_house"), Layout.findName("build my big house here", names), "the longest name wins");
		assertEquals(Optional.of("house"), Layout.findName("put the house up again", names));
		assertEquals(Optional.empty(), Layout.findName("build a towers", names), "whole words only");
		assertEquals(Optional.empty(), Layout.findName(null, names));
	}

	@Test
	void bigBoxesAreRefused() {
		assertTrue(Layout.sizeProblem(10, 10, 10).isEmpty());
		assertTrue(Layout.sizeProblem(Layout.MAX_SIDE + 1, 4, 4).isPresent());
		assertTrue(Layout.sizeProblem(4, Layout.MAX_HEIGHT + 1, 4).isPresent());
	}
}
