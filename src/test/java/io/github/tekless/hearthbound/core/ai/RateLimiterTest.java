package io.github.tekless.hearthbound.core.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimiterTest {
	@Test
	void allowsUpToTheLimitPerMinutePerPlayer() {
		RateLimiter limiter = new RateLimiter(3);
		assertTrue(limiter.tryAcquire("alice", 0));
		assertTrue(limiter.tryAcquire("alice", 1_000));
		assertTrue(limiter.tryAcquire("alice", 2_000));
		assertFalse(limiter.tryAcquire("alice", 3_000), "fourth request in a minute is refused");
		assertTrue(limiter.tryAcquire("bob", 3_000), "other players have their own allowance");
	}

	@Test
	void freesUpAsTheWindowMoves() {
		RateLimiter limiter = new RateLimiter(2);
		assertTrue(limiter.tryAcquire("alice", 0));
		assertTrue(limiter.tryAcquire("alice", 30_000));
		assertFalse(limiter.tryAcquire("alice", 59_999));
		assertTrue(limiter.tryAcquire("alice", 60_000), "the first request has aged out");
		assertFalse(limiter.tryAcquire("alice", 60_001));
	}

	@Test
	void refusedRequestsDontUseUpTheAllowance() {
		RateLimiter limiter = new RateLimiter(1);
		assertTrue(limiter.tryAcquire("alice", 0));
		for (int i = 1; i < 10; i++) {
			assertFalse(limiter.tryAcquire("alice", i * 1_000));
		}
		assertTrue(limiter.tryAcquire("alice", 60_000), "only the accepted request counted");
	}

	@Test
	void zeroMeansNoLimit() {
		RateLimiter limiter = new RateLimiter(0);
		for (int i = 0; i < 100; i++) {
			assertTrue(limiter.tryAcquire("alice", i));
		}
	}
}
