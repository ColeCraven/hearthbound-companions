package io.github.tekless.hearthbound.core.blueprint;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the blocks of a saved build go when it is put up again: turned so that the way the player faced when
 * they saved it is the way they face now, and set down in front of them, two blocks ahead and centred on them.
 * Pure arithmetic on plain numbers, so it can be checked without a world.
 *
 * <p>Directions are compass points: 0 south (+z), 1 west (-x), 2 north (-z), 3 east (+x), the same order as
 * the game's horizontal directions. A quarter turn is clockwise seen from above (north becomes east).
 */
public final class Layout {
	/** Longest side of a saved build, in blocks. */
	public static final int MAX_SIDE = 48;
	/** Tallest saved build. */
	public static final int MAX_HEIGHT = 32;
	/** Most blocks one saved build may hold. */
	public static final int MAX_BLOCKS = 4096;

	private Layout() {
	}

	/** One block offset: x east, y up, z south. */
	public record Offset(int x, int y, int z) {
	}

	/** Quarter turns (0 to 3, clockwise) that take the saved facing to the facing now. */
	public static int turnsBetween(int savedFacing, int facingNow) {
		// Clockwise from above goes south, west, north, east: the order the compass numbers already run in.
		return Math.floorMod(facingNow - savedFacing, 4);
	}

	/** An offset turned clockwise by some quarter turns about the vertical axis. */
	public static Offset turn(Offset offset, int quarterTurns) {
		int x = offset.x();
		int z = offset.z();
		for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
			int nx = -z;
			int nz = x;
			x = nx;
			z = nz;
		}
		return new Offset(x, offset.y(), z);
	}

	/** The unit step for a compass number. */
	public static Offset step(int facing) {
		return switch (Math.floorMod(facing, 4)) {
			case 0 -> new Offset(0, 0, 1);
			case 1 -> new Offset(-1, 0, 0);
			case 2 -> new Offset(0, 0, -1);
			default -> new Offset(1, 0, 0);
		};
	}

	/**
	 * Where the saved build's corner (offset 0, 0, 0 before turning) ends up, relative to the player's feet, so that
	 * the turned build starts {@code gap} blocks in front of them and is centred left to right.
	 *
	 * @param turned the offsets of every block after turning
	 * @param facing which way the player faces now
	 */
	public static Offset anchor(List<Offset> turned, int facing, int gap) {
		Offset forward = step(facing);
		Offset right = step(facing + 1); // a quarter turn clockwise: the player's right hand
		int nearest = Integer.MAX_VALUE;
		int leftmost = Integer.MAX_VALUE;
		int rightmost = Integer.MIN_VALUE;
		for (Offset o : turned) {
			nearest = Math.min(nearest, dot(o, forward));
			int across = dot(o, right);
			leftmost = Math.min(leftmost, across);
			rightmost = Math.max(rightmost, across);
		}
		if (turned.isEmpty()) {
			return new Offset(0, 0, 0);
		}
		int ahead = gap - nearest;
		int centre = Math.floorDiv(leftmost + rightmost, 2);
		return new Offset(forward.x() * ahead - right.x() * centre, 0, forward.z() * ahead - right.z() * centre);
	}

	private static int dot(Offset a, Offset b) {
		return a.x() * b.x() + a.z() * b.z();
	}

	/** Why a box this size can't be saved, or empty if it can. */
	public static Optional<String> sizeProblem(int width, int height, int depth) {
		if (width > MAX_SIDE || depth > MAX_SIDE) {
			return Optional.of("wider than " + MAX_SIDE + " blocks");
		}
		if (height > MAX_HEIGHT) {
			return Optional.of("taller than " + MAX_HEIGHT + " blocks");
		}
		return Optional.empty();
	}

	/**
	 * A blueprint's name as it is stored: lower case letters, digits and underscores ("My House!" is "my_house").
	 * Empty if nothing usable is left.
	 */
	public static Optional<String> cleanName(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String name = raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (name.length() > 32) {
			name = name.substring(0, 32).replaceAll("_+$", "");
		}
		return name.isEmpty() ? Optional.empty() : Optional.of(name);
	}

	/** The words of a stored name, for saying it out loud ("my_house" is "my house"). */
	public static String spoken(String name) {
		return name.replace('_', ' ');
	}

	/**
	 * The blueprint a request means, from the names saved: "build my cottage here", "put the tower up again",
	 * "build cottage". Whole words only, longest name first, so "big house" wins over "house".
	 */
	public static Optional<String> findName(String phrase, List<String> names) {
		if (phrase == null || names.isEmpty()) {
			return Optional.empty();
		}
		String words = " " + phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
		String best = null;
		for (String name : names) {
			String spoken = " " + spoken(name) + " ";
			if (words.contains(spoken) && (best == null || name.length() > best.length())) {
				best = name;
			}
		}
		return Optional.ofNullable(best);
	}
}
