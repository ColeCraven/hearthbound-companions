package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tekless.hearthbound.core.persona.Role;
import org.junit.jupiter.api.Test;

class GroupAddressTest {
	@Test
	void everyoneAtTheStartIsAGroup() {
		GroupAddress.Result r = GroupAddress.parse("Everyone, follow me");
		assertNotNull(r);
		assertEquals(GroupAddress.Scope.NEARBY, r.scope());
		assertEquals("follow me", r.body());
		assertTrue(r.vocative());
		assertEquals(GroupAddress.Scope.NEARBY, GroupAddress.parse("hey everyone come here").scope());
		assertEquals("come here", GroupAddress.parse("hey everyone come here").body());
		assertEquals("should have their own house", GroupAddress.parse("everyone should have their own house").body());
		assertFalse(GroupAddress.parse("everyone should have their own house").vocative());
		assertNotNull(GroupAddress.parse("guys, wait here"));
		assertNotNull(GroupAddress.parse("all of you gather some wood"));
	}

	@Test
	void youAllAnywhereIsAnAddress() {
		GroupAddress.Result r = GroupAddress.parse("can you all come over here please");
		assertNotNull(r);
		assertEquals(GroupAddress.Scope.NEARBY, r.scope());
		assertEquals("can you all come over here please", r.body());
		assertNotNull(GroupAddress.parse("I need you guys to wait"));
		assertEquals("follow me", GroupAddress.parse("follow me, everyone").body());
	}

	@Test
	void roleGroupsNeedToBeAddressedAtTheStart() {
		GroupAddress.Result r = GroupAddress.parse("All my builders, please help expand this village. Work with the collectors.");
		assertNotNull(r);
		assertEquals(GroupAddress.Scope.ROLE, r.scope());
		assertEquals(Role.BUILDER, r.role());
		assertTrue(r.body().startsWith("please help expand"));
		assertEquals(Role.FARMER, GroupAddress.parse("All my farmers, build a farm").role());
		assertEquals(Role.COLLECTOR, GroupAddress.parse("collectors, gather some logs").role());
		assertEquals(Role.GUARDIAN, GroupAddress.parse("guardians stay close").role());
		assertEquals(Role.BUILDER, GroupAddress.parse("hey builders can you start on the roof").role());
	}

	@Test
	void atRoleWorksToo() {
		GroupAddress.Result r = GroupAddress.parse("@farmers harvest the wheat");
		assertNotNull(r);
		assertEquals(Role.FARMER, r.role());
		assertTrue(r.explicit());
		assertEquals("harvest the wheat", r.body());
		assertEquals(Role.BUILDER, GroupAddress.parse("@builder build a shelter").role());
	}

	@Test
	void aRoleMentionedLaterIsCoordinationNotTheAddressee() {
		assertNull(GroupAddress.parse("Please work with the collectors and gather materials"));
		assertNull(GroupAddress.parse("Hazel, connect with the builders"));
		assertNull(GroupAddress.parse("I think the farmers are doing well"));
		assertNull(GroupAddress.parse("tell the builders to wait"));
	}

	@Test
	void namesAndExistingGroupWordsAreLeftAlone() {
		assertNull(GroupAddress.parse("@Hazel follow me"));
		assertNull(GroupAddress.parse("@everyone follow me"));
		assertNull(GroupAddress.parse("@party wait here"));
		assertNull(GroupAddress.parse("Hazel, wait here"));
		assertNull(GroupAddress.parse("I hope everyone is well"));
		assertNull(GroupAddress.parse("what a lovely day"));
	}
}
