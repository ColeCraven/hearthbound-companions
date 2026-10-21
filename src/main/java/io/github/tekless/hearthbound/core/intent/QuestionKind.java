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
	/** "A shelter, a wall or a bridge?" The reply says what to build. */
	BUILD_WHAT,
	/** "Gather what?" The reply names the item. */
	GATHER_WHAT,
	/** "Attack what?" The reply names the creature. */
	TARGET_WHAT,
	/** A stranger asked to work: "Want me to join you first?" A yes recruits and runs the pending order. */
	JOIN_FIRST,
	/** Anything else; any reply gets a short, honest acknowledgement. */
	OPEN
}
