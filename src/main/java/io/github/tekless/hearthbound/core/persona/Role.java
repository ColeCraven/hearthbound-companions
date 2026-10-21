package io.github.tekless.hearthbound.core.persona;

import java.util.Locale;
import java.util.Set;

/**
 * What a companion is good at, separate from how they talk (their {@link Archetype}). A role never
 * stops anyone doing a job: it makes the things they are good at faster or tougher, and the things
 * they are not so good at a little slower.
 */
public enum Role {
	GUARDIAN("Guardian", Set.of(Task.COMBAT), Set.of(Task.FARM)),
	BUILDER("Builder", Set.of(Task.BUILD, Task.DIG), Set.of(Task.COMBAT)),
	COLLECTOR("Collector", Set.of(Task.GATHER), Set.of(Task.BUILD)),
	FARMER("Farmer", Set.of(Task.FARM), Set.of(Task.DIG));

	/** The kinds of work a role can be better or worse at. */
	public enum Task {
		COMBAT("fight"), BUILD("build"), DIG("dig"), GATHER("gather"), FARM("farm");

		private final String verb;

		Task(String verb) {
			this.verb = verb;
		}

		/** A word for lines like "Building is what I do." */
		public String verb() {
			return verb;
		}
	}

	/** How much better (or worse) a role is at a task, as a speed or strength multiplier. */
	public static final double STRONG = 1.4;
	public static final double WEAK = 0.85;

	private final String label;
	private final Set<Task> strong;
	private final Set<Task> weak;

	Role(String label, Set<Task> strong, Set<Task> weak) {
		this.label = label;
		this.strong = strong;
		this.weak = weak;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Short name shown under a companion's name ("Guardian"). */
	public String label() {
		return label;
	}

	public double multiplier(Task task) {
		return strong.contains(task) ? STRONG : weak.contains(task) ? WEAK : 1.0;
	}

	public boolean isStrongAt(Task task) {
		return strong.contains(task);
	}

	public boolean isWeakAt(Task task) {
		return weak.contains(task);
	}

	/** The role a wanderer without a cast identity gets from their personality. */
	public static Role forArchetype(Archetype archetype) {
		return switch (archetype) {
			case SELLSWORD, GUARDIAN -> GUARDIAN;
			case NATURALIST, ROGUE -> COLLECTOR;
			case MYSTIC -> BUILDER;
			case FARMHAND -> FARMER;
		};
	}
}
