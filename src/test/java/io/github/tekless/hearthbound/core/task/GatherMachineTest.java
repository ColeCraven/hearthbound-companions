package io.github.tekless.hearthbound.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The gather order's decision logic, against a scripted fake world. */
class GatherMachineTest {
	/** A tiny world: numbered targets, each one block worth one item, with knobs for failures. */
	static final class FakeWorld implements GatherWorld<Integer> {
		final Deque<Integer> targets = new ArrayDeque<>();
		final Set<Integer> unreachable = new HashSet<>();
		final List<GatherMachine.Event> seen = new ArrayList<>();
		int collected;
		int delivered;
		int capacity = Integer.MAX_VALUE;
		String missingTool;
		boolean ownerHere = true;
		int ticksToArrive = 2;
		int ticksToBreak = 3;
		private int moving;
		private int breaking;

		@Override
		public Optional<Integer> findTarget(Set<Integer> excluded) {
			return targets.stream().filter(t -> !excluded.contains(t)).findFirst();
		}

		@Override
		public Optional<String> missingTool(Integer target) {
			return Optional.ofNullable(missingTool);
		}

		@Override
		public Move approach(Integer target) {
			if (unreachable.contains(target)) {
				return Move.UNREACHABLE;
			}
			return ++moving >= ticksToArrive ? reset(Move.ARRIVED) : Move.MOVING;
		}

		private Move reset(Move move) {
			moving = 0;
			return move;
		}

		@Override
		public Break breakStep(Integer target) {
			if (!targets.contains(target)) {
				return Break.GONE;
			}
			if (++breaking < ticksToBreak) {
				return Break.IN_PROGRESS;
			}
			breaking = 0;
			targets.remove(target);
			collected++;
			return Break.DONE;
		}

		@Override
		public int collected() {
			return collected - delivered;
		}

		@Override
		public boolean inventoryFull() {
			return collected - delivered >= capacity;
		}

		@Override
		public boolean ownerAvailable() {
			return ownerHere;
		}

		@Override
		public Move returnToOwner() {
			return Move.ARRIVED;
		}

		@Override
		public int deliver(int max) {
			int amount = Math.min(max, collected());
			delivered += amount;
			return amount;
		}
	}

	private static List<GatherMachine.Event> run(GatherMachine<Integer> machine, int maxTicks) {
		List<GatherMachine.Event> events = new ArrayList<>();
		for (int i = 0; i < maxTicks && !machine.status().isFinished(); i++) {
			machine.tick();
			events.addAll(machine.drainEvents());
		}
		return events;
	}

	@Test
	void succeedsOnlyAfterDeliveringTheFullAmount() {
		FakeWorld world = new FakeWorld();
		for (int i = 0; i < 10; i++) {
			world.targets.add(i);
		}
		GatherMachine<Integer> machine = new GatherMachine<>(world, 8);
		List<GatherMachine.Event> events = run(machine, 2000);
		assertEquals(TaskStatus.SUCCEEDED, machine.status());
		assertEquals(8, world.delivered);
		assertInstanceOf(GatherMachine.Event.HalfWay.class, events.get(0));
		assertInstanceOf(GatherMachine.Event.CollectedAll.class, events.get(events.size() - 2));
		assertEquals(new GatherMachine.Event.Delivered(8), events.get(events.size() - 1));
	}

	@Test
	void reportsNoneFoundWithoutClaimingAnything() {
		FakeWorld world = new FakeWorld();
		GatherMachine<Integer> machine = new GatherMachine<>(world, 4);
		List<GatherMachine.Event> events = run(machine, 100);
		assertEquals(TaskStatus.FAILED, machine.status());
		assertEquals(FailureReason.NONE_FOUND, machine.failure());
		assertEquals(List.of(new GatherMachine.Event.Failed(FailureReason.NONE_FOUND, null)), events);
		assertEquals(0, world.delivered);
	}

	@Test
	void deliversPartialAmountWhenTheSupplyRunsOut() {
		FakeWorld world = new FakeWorld();
		world.targets.addAll(List.of(1, 2, 3));
		GatherMachine<Integer> machine = new GatherMachine<>(world, 10);
		List<GatherMachine.Event> events = run(machine, 1000);
		assertEquals(TaskStatus.FAILED, machine.status());
		assertEquals(3, world.delivered);
		GatherMachine.Event last = events.get(events.size() - 1);
		assertEquals(new GatherMachine.Event.Partial(3, 10, FailureReason.NONE_FOUND, null), last);
	}

	@Test
	void skipsUnreachableTargetsAndExplainsIfAllAre() {
		FakeWorld world = new FakeWorld();
		world.targets.addAll(List.of(1, 2));
		world.unreachable.addAll(List.of(1, 2));
		GatherMachine<Integer> machine = new GatherMachine<>(world, 2);
		run(machine, 200);
		assertEquals(FailureReason.UNREACHABLE, machine.failure());
	}

	@Test
	void usesReachableTargetsAfterSkippingOthers() {
		FakeWorld world = new FakeWorld();
		world.targets.addAll(List.of(1, 2, 3));
		world.unreachable.add(1);
		GatherMachine<Integer> machine = new GatherMachine<>(world, 2);
		run(machine, 500);
		assertEquals(TaskStatus.SUCCEEDED, machine.status());
		assertTrue(world.targets.contains(1), "unreachable target left alone");
	}

	@Test
	void missingToolFailsBeforeMoving() {
		FakeWorld world = new FakeWorld();
		world.targets.add(1);
		world.missingTool = "stone pickaxe";
		GatherMachine<Integer> machine = new GatherMachine<>(world, 3);
		List<GatherMachine.Event> events = run(machine, 50);
		assertEquals(List.of(new GatherMachine.Event.Failed(FailureReason.NEEDS_TOOL, "stone pickaxe")), events);
		assertTrue(world.targets.contains(1));
	}

	@Test
	void fullInventoryBringsBackWhatFits() {
		FakeWorld world = new FakeWorld();
		for (int i = 0; i < 20; i++) {
			world.targets.add(i);
		}
		world.capacity = 5;
		GatherMachine<Integer> machine = new GatherMachine<>(world, 16);
		List<GatherMachine.Event> events = run(machine, 2000);
		assertEquals(5, world.delivered);
		assertEquals(new GatherMachine.Event.Partial(5, 16, FailureReason.INVENTORY_FULL, null), events.get(events.size() - 1));
	}

	@Test
	void cancelStopsImmediatelyAndIsFinal() {
		FakeWorld world = new FakeWorld();
		for (int i = 0; i < 10; i++) {
			world.targets.add(i);
		}
		GatherMachine<Integer> machine = new GatherMachine<>(world, 10);
		for (int i = 0; i < 7; i++) {
			machine.tick();
		}
		int before = world.collected;
		machine.cancel();
		for (int i = 0; i < 100; i++) {
			machine.tick();
		}
		assertEquals(TaskStatus.CANCELLED, machine.status());
		assertEquals(before, world.collected, "no more blocks broken after cancel");
		assertTrue(machine.drainEvents().stream().noneMatch(e -> e instanceof GatherMachine.Event.Delivered));
		machine.cancel();
		assertEquals(TaskStatus.CANCELLED, machine.status());
	}

	@Test
	void ownerLeavingFailsWithoutLosingItems() {
		FakeWorld world = new FakeWorld();
		world.targets.addAll(List.of(1, 2));
		GatherMachine<Integer> machine = new GatherMachine<>(world, 2);
		while (machine.phase() != GatherMachine.Phase.RETURNING && !machine.status().isFinished()) {
			machine.tick();
		}
		world.ownerHere = false;
		machine.tick();
		assertEquals(FailureReason.OWNER_GONE, machine.failure());
		assertEquals(2, world.collected(), "items still carried");
	}
}
