package io.github.tekless.hearthbound.core.persona;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Keeps the cast consistent: unique names, balanced pairs, a texture for everyone. */
class CastMemberTest {
	@Test
	void namesAreUniqueAndNotReusedByWanderers() {
		Set<String> names = new HashSet<>();
		for (CastMember member : CastMember.values()) {
			assertTrue(names.add(member.displayName().toLowerCase(Locale.ROOT)), "duplicate cast name " + member);
			for (Archetype archetype : Archetype.values()) {
				assertFalse(archetype.names().contains(member.displayName()),
						member.displayName() + " is reserved for the cast but also in the " + archetype + " name pool");
			}
		}
		for (Archetype archetype : Archetype.values()) {
			for (String name : archetype.names()) {
				assertTrue(names.add(name.toLowerCase(Locale.ROOT)), "name used twice: " + name);
			}
		}
	}

	@Test
	void eachPersonalityHasOneWomanAndOneMan() {
		for (Archetype archetype : Archetype.values()) {
			long women = 0;
			long men = 0;
			for (CastMember member : CastMember.values()) {
				if (member.archetype() == archetype) {
					if (member.pronouns().equals("she/her")) {
						women++;
					} else if (member.pronouns().equals("he/him")) {
						men++;
					}
				}
			}
			assertEquals(1, women, archetype + " women");
			assertEquals(1, men, archetype + " men");
		}
	}

	@Test
	void everyCastMemberHasASkinTexture() throws IOException {
		for (CastMember member : CastMember.values()) {
			try (InputStream in = CastMember.class.getResourceAsStream("/assets/hearthbound/textures/entity/companion/" + member.id() + ".png")) {
				assertNotNull(in, "missing skin for " + member);
				byte[] header = in.readNBytes(24);
				assertEquals((byte) 0x89, header[0], "PNG signature");
				int width = ((header[16] & 0xff) << 24) | ((header[17] & 0xff) << 16) | ((header[18] & 0xff) << 8) | (header[19] & 0xff);
				int height = ((header[20] & 0xff) << 24) | ((header[21] & 0xff) << 16) | ((header[22] & 0xff) << 8) | (header[23] & 0xff);
				assertEquals(64, width, member + " skin width");
				assertEquals(64, height, member + " skin height");
			}
		}
	}

	@Test
	void skinVariantsRoundTrip() {
		for (CastMember member : CastMember.values()) {
			assertEquals(member, CastMember.bySkinVariant(member.skinVariant()).orElseThrow());
			assertEquals(member, CastMember.byName(member.displayName().toUpperCase(Locale.ROOT)).orElseThrow());
		}
		assertTrue(CastMember.bySkinVariant(17).isEmpty(), "default skins are not cast skins");
	}
}
