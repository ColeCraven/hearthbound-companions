package io.github.tekless.hearthbound.core.intent;

/** Something the player tells a companion about themselves. */
public enum FactKind {
	/** "Call me Captain." */
	NICKNAME,
	/** "I love the ocean." */
	LIKE,
	/** "I hate spiders." */
	DISLIKE,
	/** "Don't call me that." Back to their real name. */
	FORGET_NAME
}
