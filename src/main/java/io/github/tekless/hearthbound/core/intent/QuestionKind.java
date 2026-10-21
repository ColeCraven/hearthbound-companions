package io.github.tekless.hearthbound.core.intent;

/** What a companion asked the player, which decides how the reply is read. */
public enum QuestionKind {
	/** "What should I call you?" The reply becomes their nickname. */
	NICKNAME,
	/** "Favourite thing to find down a mine?" The reply is remembered as a like. */
	LIKE,
	/** "Anything you can't stand?" Remembered as a dislike. */
	DISLIKE,
	/** "How are you holding up?" Answered with sympathy or cheer. */
	HOW,
	/** Anything else; any reply gets a short, honest acknowledgement. */
	OPEN
}
