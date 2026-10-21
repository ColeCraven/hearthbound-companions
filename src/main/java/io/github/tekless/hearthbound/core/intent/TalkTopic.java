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
	/** Their past: home, family, how they ended up on the road. */
	STORY,
	/** What they think of where you are ("isn't it beautiful?"). */
	PLACE,
	/** What to do next ("any ideas?", "I'm bored"). */
	PLAN,
	YES,
	NO,
	LAUGH,
	/** Warm words aimed at them ("I like you", "glad you're here"). */
	FONDNESS,
	/** The player is having a bad time. */
	SAD,
	FOOD,
	/** "What can you do?" A short answer in character, plus a pointer to /companion help. */
	HELP,
	/** "What do you know about me?" What the player has told them, said back. */
	ABOUT_ME,
	/** "Are we friends?" How they feel about the player, from affinity. */
	BOND,
	/** Follow-ups that lean on what was just said: "why?", "really?", "and you?", "tell me more", "what do you mean?". */
	WHY,
	REALLY,
	AND_YOU,
	MORE,
	MEAN,
	/** "What are you good at?": their role, in their own voice. */
	ROLE,
	/** Weather and time of day: "it's getting dark", "it's raining", "what time is it?" */
	SKY,
	/** Any other question; a reply in character that doesn't pretend to know the answer. */
	QUESTION,
	GENERIC;

	/** Line-bank key for this topic, e.g. {@code talk.joke}. */
	public String lineKey() {
		return "talk." + name().toLowerCase(Locale.ROOT);
	}
}
