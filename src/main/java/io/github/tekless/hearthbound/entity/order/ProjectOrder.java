package io.github.tekless.hearthbound.entity.order;

import io.github.tekless.hearthbound.core.task.TaskStatus;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.List;

/**
 * A bigger job made of steps: get the materials (crafting and gathering if short), then build. The
 * companion works through them one at a time, and a step that cannot be done ends the whole project
 * after saying why. Cancelling or giving any other order stops it, and nothing is claimed as built
 * unless the blocks are really there.
 */
public final class ProjectOrder implements CompanionOrder {
	public enum State {
		RUNNING, DONE, FAILED
	}

	/** One stage of a project. */
	public interface Step {
		State tick(CompanionEntity companion);

		default void pause(CompanionEntity companion) {
			companion.getNavigation().stop();
		}

		default void stop(CompanionEntity companion) {
			companion.getNavigation().stop();
		}

		/** What this stage is doing right now, for status replies. */
		default String describe() {
			return "";
		}
	}

	private final Kind kind;
	private final String label;
	private final List<Step> steps;
	private final Runnable onEnd;
	private int index;
	private boolean ended;
	private TaskStatus status = TaskStatus.RUNNING;

	/** @param onEnd runs once when the project ends, however it ends (frees the building site) */
	public ProjectOrder(String label, List<Step> steps, Runnable onEnd) {
		this(Kind.PROJECT, label, steps, onEnd);
	}

	/** @param kind what sort of job it is (a project, or crafting on request) */
	public ProjectOrder(Kind kind, String label, List<Step> steps, Runnable onEnd) {
		this.kind = kind;
		this.label = label;
		this.steps = steps;
		this.onEnd = onEnd;
	}

	public String label() {
		return label;
	}

	@Override
	public Kind kind() {
		return kind;
	}

	@Override
	public void tick(CompanionEntity companion) {
		if (status.isFinished()) {
			return;
		}
		if (index >= steps.size()) {
			end(TaskStatus.SUCCEEDED);
			return;
		}
		switch (steps.get(index).tick(companion)) {
			case DONE -> index++;
			case FAILED -> end(TaskStatus.FAILED);
			case RUNNING -> {
			}
		}
		if (index >= steps.size()) {
			end(TaskStatus.SUCCEEDED);
		}
	}

	@Override
	public void pause(CompanionEntity companion) {
		if (index < steps.size()) {
			steps.get(index).pause(companion);
		}
		CompanionOrder.super.pause(companion);
	}

	@Override
	public void cancel(CompanionEntity companion) {
		if (index < steps.size() && !status.isFinished()) {
			steps.get(index).stop(companion);
		}
		end(TaskStatus.CANCELLED);
		companion.getNavigation().stop();
	}

	private void end(TaskStatus result) {
		if (!status.isFinished()) {
			status = result;
		}
		if (!ended) {
			ended = true;
			if (onEnd != null) {
				onEnd.run();
			}
		}
	}

	@Override
	public TaskStatus status() {
		return status;
	}

	@Override
	public String describe() {
		String inner = index < steps.size() ? steps.get(index).describe() : "";
		return "working on " + label + " (step " + Math.min(index + 1, steps.size()) + " of " + steps.size() + (inner.isEmpty() ? "" : ": " + inner) + ")";
	}
}
