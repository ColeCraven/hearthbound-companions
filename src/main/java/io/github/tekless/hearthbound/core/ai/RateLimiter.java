package io.github.tekless.hearthbound.core.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * At most {@code perMinute} requests per key (a player) in any rolling 60-second window. On a
 * shared server this keeps one chatty player from running up the AI bill or starving everyone else.
 */
public final class RateLimiter {
	private static final long WINDOW_MILLIS = 60_000;

	private final int perMinute;
	private final Map<String, Deque<Long>> calls = new HashMap<>();

	public RateLimiter(int perMinute) {
		this.perMinute = perMinute;
	}

	/** Records a request and returns true if it is within the limit; returns false (recording nothing) otherwise. */
	public synchronized boolean tryAcquire(String key, long nowMillis) {
		if (perMinute <= 0) {
			return true;
		}
		Deque<Long> recent = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
		while (!recent.isEmpty() && nowMillis - recent.peekFirst() >= WINDOW_MILLIS) {
			recent.removeFirst();
		}
		if (recent.size() >= perMinute) {
			return false;
		}
		recent.addLast(nowMillis);
		return true;
	}
}
