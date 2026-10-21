package io.github.tekless.hearthbound.core.intent;

import io.github.tekless.hearthbound.core.persona.Role;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spots a message meant for a whole group without an {@code @name}: "everyone, follow me", "hey guys",
 * "can you all come here", "all my builders, ...", "@farmers harvest". A role word later in the message
 * ("work with the collectors") is a mention, not an address, so only the start of the message counts for
 * roles. {@code @everyone}, {@code @all} and {@code @party} keep their own meanings and are left alone.
 */
public final class GroupAddress {
	/** Everyone close by, or every companion of one role. */
	public enum Scope {
		NEARBY, ROLE
	}

	/**
	 * @param body      what's left to act on once the address is taken off
	 * @param explicit  written with an {@code @}
	 * @param vocative  plainly spoken to the group (a comma after it, "hey", "all my ..."), so even chat that
	 *                  isn't an order is meant for them
	 */
	public record Result(Scope scope, Role role, String body, boolean explicit, boolean vocative) {
	}

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
	private static final String EVERYONE = "everyone|everybody|all of you|you all|y'all|yall|you guys|you lot|guys|team|folks"
			+ "|(?:all|both)(?: of)?(?: my| the| our)? companions|(?:my|the|our) companions|companions";
	private static final String ROLES = "builder|farmer|collector|gatherer|guardian|guard";
	private static final Pattern FILLER = Pattern.compile("^(?:(?:hey|hi|hello|ok|okay|right|so|alright|oi|well|now|yo|hiya)\\b[,!.]?\\s*)+", FLAGS);
	private static final Pattern AT_ROLE = Pattern.compile("^@(" + ROLES + ")(s?)\\b[,:;!]?\\s*(.*)$", FLAGS);
	private static final Pattern START_EVERYONE = Pattern.compile("^(" + EVERYONE + ")\\b\\s*([,:!;.-]?)\\s*(.*)$", FLAGS);
	private static final Pattern START_ROLE = Pattern.compile("^((?:all|both)(?: of)?\\s+)?((?:my|the|our)\\s+)?(" + ROLES + ")(s?)\\b\\s*([,:!;.-]?)\\s*(.*)$", FLAGS);
	private static final Pattern MID_EVERYONE = Pattern.compile("\\b(?:you all|y'all|yall|all of you|you guys|you lot)\\b", FLAGS);
	private static final Pattern TRAIL_EVERYONE = Pattern.compile("^(.*\\S)\\s*,\\s*(?:everyone|everybody|guys|team|folks)\\s*[!.?]*$", FLAGS);
	/** Words that follow a role word when it is being told to do something ("builders start on the roof"). */
	private static final Pattern VERB_NEXT = Pattern.compile("^(?:please|can|could|would|will|should|must|go|come|follow|gather|collect|build|start|help|stop|wait|get|bring|work"
			+ "|dig|mine|chop|cut|farm|harvest|plant|fight|attack|guard|protect|hunt|stay|let's|lets|make|put|take|give|show|return|head)\\b.*", FLAGS);

	private GroupAddress() {
	}

	/** The group this message is for, or null when it is for one companion (or nobody in particular). */
	public static Result parse(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.startsWith("@")) {
			Matcher at = AT_ROLE.matcher(text);
			return at.matches() ? new Result(Scope.ROLE, roleOf(at.group(1)), bodyOr(at.group(3)), true, true) : null;
		}
		Matcher filler = FILLER.matcher(text);
		boolean greeted = filler.find();
		String t = greeted ? text.substring(filler.end()) : text;
		Matcher everyone = START_EVERYONE.matcher(t);
		if (everyone.matches()) {
			boolean comma = !everyone.group(2).isEmpty();
			return new Result(Scope.NEARBY, null, bodyOr(everyone.group(3)), false, comma || greeted);
		}
		Matcher role = START_ROLE.matcher(t);
		if (role.matches()) {
			boolean all = role.group(1) != null;
			boolean prefixed = all || role.group(2) != null;
			boolean plural = !role.group(4).isEmpty();
			boolean punct = !role.group(5).isEmpty();
			String rest = role.group(6);
			if (punct || prefixed && plural || plural && VERB_NEXT.matcher(rest).matches()) {
				return new Result(Scope.ROLE, roleOf(role.group(3)), bodyOr(rest), false, punct || all || greeted);
			}
		}
		if (MID_EVERYONE.matcher(text).find()) {
			return new Result(Scope.NEARBY, null, text, false, true);
		}
		Matcher trailing = TRAIL_EVERYONE.matcher(text);
		if (trailing.matches()) {
			return new Result(Scope.NEARBY, null, trailing.group(1), false, true);
		}
		return null;
	}

	public static Role roleOf(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "builder" -> Role.BUILDER;
			case "farmer" -> Role.FARMER;
			case "collector", "gatherer" -> Role.COLLECTOR;
			default -> Role.GUARDIAN;
		};
	}

	private static String bodyOr(String rest) {
		String body = rest == null ? "" : rest.trim();
		return body.isEmpty() ? "hello" : body;
	}
}
