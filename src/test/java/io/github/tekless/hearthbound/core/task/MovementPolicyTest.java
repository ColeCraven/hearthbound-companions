package io.github.tekless.hearthbound.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementPolicyTest {
	private final FollowPolicy policy = new FollowPolicy(3, 5);

	@Test
	void holdsInsideTheComfortBand() {
		assertEquals(FollowPolicy.Action.HOLD, policy.decide(4, 0, true, false, false));
		assertEquals(FollowPolicy.Action.HOLD, policy.decide(1, 0, true, false, false));
	}

	@Test
	void walksThenHurriesThenTeleports() {
		assertEquals(FollowPolicy.Action.WALK, policy.decide(6, 0, true, false, false));
		assertEquals(FollowPolicy.Action.HURRY, policy.decide(15, 0, true, false, false));
		assertEquals(FollowPolicy.Action.TELEPORT, policy.decide(40, 0, true, false, false));
	}

	@Test
	void keepsWalkingUntilComfortablyClose() {
		assertEquals(FollowPolicy.Action.WALK, policy.decide(4, 0, true, true, false));
		assertEquals(FollowPolicy.Action.HOLD, policy.decide(2.5, 0, true, true, false));
	}

	@Test
	void teleportsAcrossDimensionsCliffsAndWhenStuck() {
		assertEquals(FollowPolicy.Action.TELEPORT, policy.decide(2, 0, false, false, false));
		assertEquals(FollowPolicy.Action.TELEPORT, policy.decide(3, 20, true, false, false));
		assertEquals(FollowPolicy.Action.TELEPORT, policy.decide(12, 0, true, true, true));
		assertEquals(FollowPolicy.Action.WALK, policy.decide(6, 0, true, true, true), "stuck but close: keep trying");
	}

	@Test
	void stuckDetectorNeedsAFullWindowWithoutProgress() {
		StuckDetector detector = new StuckDetector(10, 0.5);
		// First call anchors the position; then ten ticks without progress make one stuck window.
		for (int i = 0; i < 10; i++) {
			assertFalse(detector.update(0, 0, true));
		}
		assertTrue(detector.update(0, 0, true));
		assertEquals(1, detector.consecutiveStuck());
		// Moving resets it.
		assertFalse(detector.update(1, 0, true));
		assertEquals(0, detector.consecutiveStuck());
		// Standing still on purpose never counts as stuck.
		for (int i = 0; i < 50; i++) {
			assertFalse(detector.update(1, 0, false));
		}
	}
}
