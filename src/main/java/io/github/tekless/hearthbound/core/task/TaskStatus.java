package io.github.tekless.hearthbound.core.task;

/** Lifecycle of an order. Only {@link #RUNNING} can change; the others are final. */
public enum TaskStatus {
	RUNNING,
	SUCCEEDED,
	FAILED,
	CANCELLED;

	public boolean isFinished() {
		return this != RUNNING;
	}
}
