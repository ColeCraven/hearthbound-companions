package io.github.tekless.hearthbound.core.task;

/** Why an order could not be completed. Each maps to a line the companion says. */
public enum FailureReason {
	NONE_FOUND("fail.none_found"),
	UNREACHABLE("fail.unreachable"),
	NEEDS_TOOL("fail.needs_tool"),
	INVENTORY_FULL("fail.inventory_full"),
	STUCK("fail.stuck"),
	TIMED_OUT("fail.stuck"),
	OWNER_GONE("fail.owner_gone"),
	TARGET_GONE("fail.no_target"),
	DOWNED("fail.downed");

	private final String lineKey;

	FailureReason(String lineKey) {
		this.lineKey = lineKey;
	}

	public String lineKey() {
		return lineKey;
	}
}
