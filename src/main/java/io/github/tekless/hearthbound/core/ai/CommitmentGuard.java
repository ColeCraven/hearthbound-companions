package io.github.tekless.hearthbound.core.ai;

import io.github.tekless.hearthbound.core.intent.IntentType;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catches replies that promise an action the game isn't doing ("Sure, I'll go chop some wood!"
 * with no gather order). Such replies are replaced with a truthful scripted line, so companions
 * never agree to something they then don't do.
 */
public final class CommitmentGuard {
	private static final Pattern PROMISE = Pattern.compile(
			"\\b(i'll|i will|i'm going to|i am going to|i'm gonna|gonna|let me|i shall|on my way to|heading off to|off to)\\s+"
					+ "(go\\s+(and\\s+)?)?(get|fetch|gather|collect|chop|cut|mine|dig|bring|grab|harvest|find|follow|wait|stay|guard|"
					+ "attack|kill|fight|slay|build|craft|make|hunt|plant|farm|fish|cook|smelt|explore|scout|light|place|come|head|hand|give)\\b");
	private static final Pattern READY_MADE = Pattern.compile(
			"\\b(on it|right away|consider it done|coming right up|be right back|on my way|i'm on it|will do|as you wish|at once)\\b");

	private CommitmentGuard() {
	}

	/**
	 * @param reply    what the model wants to say
	 * @param accepted the intent the game actually accepted for this turn, if any
	 * @return true if the reply promises something other than what is really happening
	 */
	public static boolean breaksPromise(String reply, Optional<IntentType> accepted) {
		String text = reply.toLowerCase(Locale.ROOT).replace('’', '\'');
		Matcher promise = PROMISE.matcher(text);
		boolean promisesAction = promise.find();
		if (!promisesAction && !READY_MADE.matcher(text).find()) {
			return false;
		}
		if (accepted.isEmpty()) {
			return true;
		}
		if (!promisesAction) {
			return false; // "On it!" is fine when an order really was accepted.
		}
		return !compatible(promise.group(4), accepted.get());
	}

	private static boolean compatible(String verb, IntentType type) {
		return switch (type) {
			case GATHER -> verb.matches("get|fetch|gather|collect|chop|cut|mine|dig|bring|grab|harvest|find|head|come");
			case FOLLOW -> verb.matches("follow|come|stay|head|guard");
			case WAIT -> verb.matches("wait|stay|guard");
			case COME -> verb.matches("come|head");
			case GIVE -> verb.matches("bring|give|hand|come|get|fetch");
			case ATTACK -> verb.matches("attack|kill|fight|slay|get|hunt");
			case BUILD -> verb.matches("build|make|place|put|get");
			case MINE -> verb.matches("mine|dig|head|go|get|find");
			case HOME -> verb.matches("go|head|walk|return|come|teleport|send");
			case CANCEL, STATUS, INVENTORY, RECRUIT, DISMISS -> verb.matches("follow|wait|stay|come|head|give|hand");
		};
	}
}
