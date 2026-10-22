package io.github.tekless.hearthbound.command;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** What {@code /companion help} prints: everything you can say and do, with examples. */
final class HelpText {
	private HelpText() {
	}

	static List<Component> lines() {
		return List.of(
				Component.literal("Hearthbound Companions").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				plain("Talk the way you'd talk to a friend: the wording is up to you. These are only examples."),
				section("Talking"),
				item("@Name hi", " then just keep typing. Say bye when you're done."),
				item("@party ...", " (or ", "@all", ") talks to everyone with you. ", "@everyone", " reaches every companion you have, wherever they are."),
				item("everyone, ...", " · ", "all my builders, ...", " · ", "farmers, ...", " talk to a group with no @."),
				item("build a house", " · ", "we need a farm", " · ", "can someone get wood?", " with nobody named, whoever's close by sorts it out between them."),
				item("G", " opens chat with the name of whoever you're looking at. Right-click someone to say hi."),
				item("J", " opens a conversation screen (with the mod on both sides): history, quick replies, mood and what they're doing."),
				item("call me Captain", " · ", "I love the ocean", " · ", "I hate spiders", " They remember, and bring it up."),
				plain("Crouch twice while looking at someone and they'll crouch back."),
				section("Recruiting"),
				item("@Name join me", " / ", "@Name you're dismissed", " (3 at a time unless the host or an operator changes it)"),
				section("Orders"),
				item("follow me", " · ", "wait here", " · ", "come here", " · ", "stop", " · ", "what are you doing?"),
				item("get me 16 oak logs", " · ", "give me everything", " · ", "attack that", " (look at it)"),
				item("collect that", " · ", "build this", " (look at a block, or answer their question)"),
				item("put everything in that chest", " · ", "take the iron from that chest", " (look at the chest and stay by it)"),
				item("smelt this iron", " · ", "cook the beef", " · ", "craft me a pickaxe", " · ", "make 4 torches", " (from their pack, at a furnace or table)"),
				plain("Anyone can do any job. Guardians fight best, builders build and dig fastest, collectors gather quickest, farmers farm best."),
				item("go home", " · ", "teleport home", " · ", "/companion sethome Name|party|everyone|role", " and ", "/companion home Name teleport"),
				item("attack Steve", " · ", "fight Maddie", " fights only who you name (players only if PvP is on). Stop ends it."),
				section("Big projects"),
				item("all my builders, everyone should have their own house", " · ", "farmers, build a farm", " · ", "build a storehouse"),
				plain("They craft what they can from their pack, chop wood if they're short, and tell you what's missing."),
				section("Digging and building"),
				item("go mining", " · ", "dig a tunnel", " · ", "dig down 20", " (needs a pickaxe)"),
				item("build a shelter", " · ", "build a wall", " · ", "build a bridge", " · ", "set up camp", " (from blocks in their pack)"),
				section("Copying your builds"),
				item("/companion blueprint corner1", " · ", "corner2", " · ", "save <name>", " (look at the corners), then ", "list", " · ", "info <name>", " · ", "delete <name>"),
				item("@Maddie build my cottage here", " · ", "builders, build cottage", " They pack the materials, make or fetch what's missing, and say what they can't get."),
				section("Their life at home"),
				plain("Companions who aren't travelling with you live a day of their own: up at dawn, work by role, a meal at noon,"),
				plain("the evening together at a bell or campfire, bed at night, indoors when it rains. Beds, a kitchen (furnace or smoker by a chest)"),
				plain("and farmland near their home are found on their own. Bring them things they ask for and they'll pay you back."),
				item("/companion places", " what the home nearby has, and what everyone's up to"),
				item("/companion setplace bed|commons|kitchen|farm|storage|patrol [name|role]", " marks the spot you're standing on"),
				section("Gifts and gear"),
				plain("Right-click with an item to give it. Blocks, torches and doors go over as a whole stack."),
				plain("Sneak + right-click to open their pack. They wear armour and use the best sword you give them."),
				item("/companion explain Name", " what they last heard, how it was read, what came of it, and what's in their way"),
				section("Settings (single-player host, or operators on a server)"),
				item("/companion chattiness off|low|normal|high", " how much they say unprompted"),
				item("/companion autonomy on|off", " let them pick jobs of their own (gathering, farming, a roof for themselves)"),
				item("/companion obey on|off", " every companion in the world takes your orders, even other players' (host and operators only)"),
				item("/companion partylimit [player] <number|unlimited>", " how many companions someone can travel with"),
				item("/companion ai setup", " (Ollama on this machine) · ", "/companion ai setup openai|anthropic [model]", " · ", "/companion ai status|off", " optional AI replies"),
				item("/companion damage players on|off", " · ", "/companion damage others on|off", " · ", "/companion godmode on|off", " who can hurt companions (knocked down, not killed, either way)"),
				item("/companion setplace <place> [name|role] [x y z]", " · ", "/companion places", " also work from the console and command blocks"),
				item("/companion list", " · ", "/companion help", " · ", "/companion tell Name|party|everyone message"));
	}

	private static Component section(String title) {
		return Component.literal(title).withStyle(ChatFormatting.YELLOW);
	}

	private static Component plain(String text) {
		return Component.literal("  " + text).withStyle(ChatFormatting.GRAY);
	}

	/** Alternating example (white) and explanation (grey) parts. */
	private static Component item(String... parts) {
		MutableComponent line = Component.literal("  ");
		for (int i = 0; i < parts.length; i++) {
			line.append(Component.literal(parts[i]).withStyle(i % 2 == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		}
		return line;
	}
}
