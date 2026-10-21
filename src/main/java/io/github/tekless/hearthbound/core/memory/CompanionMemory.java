package io.github.tekless.hearthbound.core.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * What a companion remembers: the recent conversation, a handful of notable shared events
 * (written by game code from things that actually happened), and an affinity score.
 * Saved with the companion so it survives quitting and reloading.
 */
public final class CompanionMemory {
	public static final int MAX_LINES = 16;
	public static final int MAX_EVENTS = 12;

	private final Deque<Line> lines = new ArrayDeque<>();
	private final Deque<String> events = new ArrayDeque<>();
	private int affinity;
	/** Who the remembered conversation is with (a player id), or null if nobody yet. */
	private String partner;

	public record Line(boolean fromPlayer, String text) {
	}

	/**
	 * Starts listening to someone. If it's a different person from the remembered conversation,
	 * that conversation is forgotten, so one player's words never reach another player (or an AI
	 * prompt written for them). Shared events and affinity stay.
	 *
	 * @return true if an earlier conversation with someone else was dropped
	 */
	public boolean talkingWith(String partnerId) {
		if (partnerId.equals(partner)) {
			return false;
		}
		boolean dropped = partner != null && !lines.isEmpty();
		if (partner != null) {
			lines.clear();
		}
		partner = partnerId;
		return dropped;
	}

	public String partner() {
		return partner;
	}

	/** Restores the partner from a save (doesn't clear anything). */
	public void setPartner(String partnerId) {
		partner = partnerId;
	}

	public void addPlayerLine(String text) {
		add(new Line(true, text));
	}

	public void addCompanionLine(String text) {
		add(new Line(false, text));
	}

	private void add(Line line) {
		lines.addLast(line);
		while (lines.size() > MAX_LINES) {
			lines.removeFirst();
		}
	}

	/** Records a notable event in plain words, e.g. "fought a creeper with {player}". */
	public void addEvent(String event) {
		if (!events.isEmpty() && events.peekLast().equals(event)) {
			return;
		}
		events.addLast(event);
		while (events.size() > MAX_EVENTS) {
			events.removeFirst();
		}
	}

	public List<Line> lines() {
		return new ArrayList<>(lines);
	}

	public List<String> events() {
		return new ArrayList<>(events);
	}

	public int affinity() {
		return affinity;
	}

	/** Changes affinity by a capped amount (no single event can swing it wildly). */
	public void changeAffinity(int delta) {
		int capped = Math.max(-5, Math.min(5, delta));
		affinity = Math.max(-100, Math.min(100, affinity + capped));
	}

	public void setAffinity(int value) {
		affinity = Math.max(-100, Math.min(100, value));
	}

	public void clear() {
		lines.clear();
		events.clear();
		affinity = 0;
		partner = null;
	}
}
