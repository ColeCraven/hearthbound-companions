package io.github.tekless.hearthbound.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.entity.CompanionSkin;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The player profile (skin) a mannequin shows for a companion, per viewer:
 * <ol>
 * <li>a signed texture from {@code config/hearthbound-skins.json} (e.g. made on mineskin.org) -
 * every vanilla client can show these without downloading anything extra;</li>
 * <li>the cast texture from the server resource pack, if that viewer accepted the pack;</li>
 * <li>otherwise one of the game's default skins, picked to roughly suit the character.</li>
 * </ol>
 */
public final class SkinProfiles {
	/** A default skin for each cast member when their own outfit can't be shown. */
	private static final Map<CastMember, Integer> FALLBACK = new EnumMap<>(CastMember.class);
	private static final Map<String, Signed> SIGNED = new HashMap<>();
	private static final Map<String, ResolvableProfile> CACHE = new java.util.concurrent.ConcurrentHashMap<>(); // read while packets are written

	private record Signed(String value, String signature) {
	}

	static {
		fallback(CastMember.CHARLIE, CompanionSkin.STEVE, false);
		fallback(CastMember.DASHA, CompanionSkin.SUNNY, true);
		fallback(CastMember.HAZEL, CompanionSkin.ALEX, true);
		fallback(CastMember.BIRINTAN, CompanionSkin.EFE, false);
		fallback(CastMember.MADDIE, CompanionSkin.NOOR, true);
		fallback(CastMember.AJ, CompanionSkin.KAI, false);
		fallback(CastMember.DAISY, CompanionSkin.MAKENA, true);
		fallback(CastMember.COLE, CompanionSkin.ZURI, false);
		fallback(CastMember.SAMUEL, CompanionSkin.ARI, false);
		fallback(CastMember.HONEY, CompanionSkin.EFE, true);
		fallback(CastMember.LUNA, CompanionSkin.ZURI, true);
		fallback(CastMember.LUKE, CompanionSkin.MAKENA, false);
	}

	private SkinProfiles() {
	}

	private static void fallback(CastMember member, CompanionSkin skin, boolean slim) {
		FALLBACK.put(member, skin.encode(slim));
	}

	/** Reads {@code hearthbound-skins.json}: {"dasha": {"value": "...", "signature": "..."}, ...}. */
	public static void load(Path configDir) {
		SIGNED.clear();
		CACHE.clear();
		Path file = configDir.resolve("hearthbound-skins.json");
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject raw = new Gson().fromJson(reader, JsonObject.class);
			if (raw != null) {
				for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
					String name = entry.getKey();
					if (name.startsWith("_")) {
						continue; // notes and comments
					}
					Optional<CastMember> member = CastMember.byName(name);
					JsonObject skin = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : null;
					String value = skin != null && skin.has("value") ? skin.get("value").getAsString() : null;
					String signature = skin != null && skin.has("signature") ? skin.get("signature").getAsString() : null;
					if (member.isPresent() && value != null && !value.isBlank() && signature != null && !signature.isBlank()) {
						SIGNED.put(member.get().id(), new Signed(value.trim(), signature.trim()));
					} else {
						Hearthbound.LOGGER.warn("hearthbound-skins.json: skipping '{}' (needs a cast name with \"value\" and \"signature\")", name);
					}
				}
			}
			Hearthbound.LOGGER.info("Loaded signed skins for {} cast members", SIGNED.size());
		} catch (IOException | JsonParseException e) {
			Hearthbound.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
		}
	}

	/**
	 * The profile to show this viewer.
	 *
	 * @param viewerHasPack whether the viewer has the server resource pack with the cast textures
	 */
	public static ResolvableProfile forViewer(int skinVariant, boolean viewerHasPack) {
		String mode = Hearthbound.config().server.skins == null ? "auto" : Hearthbound.config().server.skins.toLowerCase(Locale.ROOT);
		Optional<CastMember> cast = CastMember.bySkinVariant(skinVariant);
		if (cast.isPresent()) {
			CastMember member = cast.get();
			Signed signed = SIGNED.get(member.id());
			if (signed != null && (mode.equals("auto") || mode.equals("signed"))) {
				return CACHE.computeIfAbsent("signed:" + member.id(), k -> signedProfile(signed));
			}
			if (viewerHasPack && (mode.equals("auto") || mode.equals("resource_pack"))) {
				return CACHE.computeIfAbsent("pack:" + member.id(),
						k -> textureProfile("hearthbound:entity/companion/" + member.id(), member.slim()));
			}
			return defaultProfile(FALLBACK.get(member));
		}
		return defaultProfile(skinVariant);
	}

	private static ResolvableProfile defaultProfile(int variant) {
		boolean slim = CompanionSkin.decodeSlim(variant);
		String id = CompanionSkin.decodeSkin(variant).id();
		return CACHE.computeIfAbsent("default:" + id + (slim ? ":slim" : ":wide"),
				k -> textureProfile("minecraft:entity/player/" + (slim ? "slim/" : "wide/") + id, slim));
	}

	private static ResolvableProfile textureProfile(String texture, boolean slim) {
		JsonObject json = new JsonObject();
		json.addProperty("texture", texture);
		json.addProperty("model", slim ? "slim" : "wide");
		return parse(json);
	}

	private static ResolvableProfile signedProfile(Signed signed) {
		JsonObject property = new JsonObject();
		property.addProperty("name", "textures");
		property.addProperty("value", signed.value());
		property.addProperty("signature", signed.signature());
		JsonArray properties = new JsonArray();
		properties.add(property);
		JsonObject json = new JsonObject();
		json.add("properties", properties);
		return parse(json);
	}

	private static ResolvableProfile parse(JsonElement json) {
		return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(error ->
				Hearthbound.LOGGER.warn("Bad skin profile {}: {}", json, error)).orElse(ResolvableProfile.Static.EMPTY);
	}
}
