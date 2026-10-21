package io.github.tekless.hearthbound.core.intent;

import java.util.Locale;

/** Small-talk topics the scripted dialogue can answer without an AI model. */
public enum TalkTopic {
	HELLO,
	WHO,
	HOW,
	JOKE,
	THANKS,
	COMPLIMENT,
	INSULT,
	BYE,
	LIKES,
	DISLIKES,
	WHERE,
	MEMORY,
	GENERIC;

	/** Line-bank key for this topic, e.g. {@code talk.joke}. */
	public String lineKey() {
		return "talk." + name().toLowerCase(Locale.ROOT);
	}
}
