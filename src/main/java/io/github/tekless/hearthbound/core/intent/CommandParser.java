package io.github.tekless.hearthbound.core.intent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based reader for messages to a companion. It is deliberately conservative: it only returns
 * a {@link ParseResult.Command} when the request is clear, so that companions never act on a
 * guess. Anything it can't place becomes {@link ParseResult.Unrecognised}, which the dialogue
 * layer answers with small talk (or hands to an optional AI model).
 *
 * <p>Pure Java - no Minecraft classes - so it is covered by plain JUnit tests.
 */
public final class CommandParser {
	private static final Set<String> LEADING_FILLERS = Set.of(
			"hey", "hi", "ok", "okay", "alright", "right", "so", "now", "um", "uh", "yo", "oi", "listen",
			"please", "pls", "plz", "kindly", "and", "then", "also", "oh", "well");
	private static final List<String> LEADING_PHRASES = List.of(
			"would you mind", "do you mind", "would you like to", "would you", "could you", "can you", "will you",
			"i need you to", "i want you to", "i would like you to", "id like you to", "go ahead and",
			"you should", "you can", "you could", "i want you", "like to", "try to", "be a dear and");
	private static final List<String> TRAILING_PHRASES = List.of(
			"please", "pls", "plz", "for me", "for us", "thanks", "thank you", "right now", "now", "asap",
			"quickly", "if you can", "if you could", "ok", "okay", "would you", "will you", "yeah", "mate",
			"buddy", "friend", "pal");

	private static final Pattern NEGATED_FOLLOW = Pattern.compile("^(dont|do not|stop|quit|no longer) (follow|following|come|coming)( me| us| with me)?$");
	private static final Pattern NEGATED_MOVE = Pattern.compile("^(dont|do not) (move|go anywhere|leave|budge)\\b");
	private static final Pattern CANCEL = Pattern.compile(
			"^(stop|cancel|abort|halt|enough|nevermind|never mind|forget it|forget that|forget about it|quit|stand down|call it off|drop it|belay that|thats enough|scratch that|no stop)"
					+ "( (it|that|this|now|everything|all|the task|your task|the job|what youre doing|what you are doing|doing that|that task|gathering|collecting|mining|chopping|digging|fighting|attacking|working))*$");
	private static final Pattern DISMISS = Pattern.compile(
			"\\b(dismiss(ed)?|youre dismissed|you are dismissed|go (back )?home|head (back )?home|return home|"
					+ "you (can|may) (go|leave)( home| now)?$|leave (the|my|our) (party|group|team)|part ways|"
					+ "i (dont|do not|no longer) need you|youre free to go|you are free to go|youre fired|you are fired|i release you)\\b");
	private static final Pattern RECRUIT = Pattern.compile(
			"\\b(join (me|us|my party|the party|my group|my team|our party)|(be|become) my (companion|follower|partner|ally|sidekick)|"
					+ "travel with me|adventure with me|(i|we) could use (your|a|some) (help|hand|sword|company)|i need your help|"
					+ "(want|like) to (join|come with|travel with|adventure with) (me|us)|youre hired|you are hired|^hire$|^recruit$|"
					+ "lets travel together|lets team up|team up with me|come adventuring)\\b");
	private static final Pattern INVENTORY = Pattern.compile(
			"\\b((open|show me|show|let me see|lemme see|check|look at|see) (your )?(inventory|pack|bag|backpack|pockets|items|stuff|gear|things)|"
					+ "^trade$|lets trade|^swap items$|what are you carrying|what do you have on you|whats in your (pack|bag|inventory|pockets))\\b");
	private static final Pattern STATUS = Pattern.compile(
			"\\b(what are you (doing|up to)|whatre you doing|whatcha doing|^status$|^report$|status report|progress|"
					+ "how is (the|your) \\w+ going|hows (the|your) \\w+ going|are you (done|finished)( yet)?|"
					+ "how many (do you have|have you got|did you get|have you (got|gathered|collected))|"
					+ "what is your (task|job|order)|whats your (task|job|order)|what are your orders)\\b");
	private static final Pattern COME = Pattern.compile(
			"^((come|get|run|hurry|walk) (over )?(here|back)( to me)?( now)?|come (to|towards) me|over here|here|to me|"
					+ "come back|get back( here)?|return( to me)?|come closer|come here)$");
	private static final Pattern GIVE = Pattern.compile("^(give|hand|pass|toss|throw|drop) (me |us )?(over )?(.*)$");
	private static final Pattern HAND_OVER = Pattern.compile("^hand (it |them |everything |that |those )?over\\b(.*)$");
	private static final Pattern ATTACK = Pattern.compile(
			"^(attack|kill|fight|hit|slay|smite|charge|go after|take down|take out|get|deal with|shoot) (.+)$");
	private static final Pattern HELP_FIGHT = Pattern.compile("^(help me (fight|kill|with) |defend me( from)? |protect me from )(.+)$");
	private static final Pattern DEFEND_ME = Pattern.compile("^(help me fight|defend me|fight with me|to arms|attack|fight|charge|kill them|get them|sic em|attack them|fight them)$");
	private static final Pattern FOLLOW = Pattern.compile(
			"(^follow( me| us| along| behind( me)?| my lead)?$|^(come|walk|run) (with|along with) (me|us)$|^come (along|on)$|"
					+ "^(lets go|lets move|lets head out|lets roll|move out|with me|on me|this way|after me|keep up|stay close|"
					+ "stick with me|stick close|stay with me|heel|onward|forward|march|come)$|resume following|keep following|"
					+ "start following|follow me\\b|you can follow|^(lets )?get going$|^protect me$|^guard me$|^escort me$)");
	private static final Pattern WAIT = Pattern.compile(
			"(^(wait|stay|hold|halt|remain|camp|sit|stand|freeze)( (here|there|put|on|position|this position|your position|the line|still|"
					+ "guard|watch|a (moment|minute|sec|second|bit|while)|for me|right here|where you are|by the door|outside))*$|"
					+ "^(guard|watch|defend|protect) (this|here|the area|this (place|spot|area|house|base|door|camp)|my (house|base|stuff|camp))$|"
					+ "^keep watch$|^stand guard$|^hold the line$|^wait here\\b|^stay here\\b)");
	private static final Pattern CHOP_TREES = Pattern.compile("^(chop|cut|fell)( down)?( (some|a few|a|the|those|these|that))?( trees?| wood)?$");
	private static final List<String> GATHER_VERBS = List.of(
			"go and gather", "go and collect", "go and get", "go and fetch", "go and mine", "go and chop", "go and find",
			"go gather", "go collect", "go get", "go fetch", "go mine", "go chop", "go find", "go grab", "go dig",
			"gather me", "collect me", "get me", "fetch me", "grab me", "bring me", "find me", "mine me", "chop me",
			"gather us", "collect us", "get us", "fetch us", "grab us", "bring us", "find us",
			"i need", "we need", "i want", "we want", "i could use", "we could use", "we are out of", "were out of", "im out of",
			"gather up", "pick up", "round up", "dig up", "cut down", "chop down",
			"gather", "collect", "get", "fetch", "grab", "chop", "cut", "mine", "dig", "harvest", "bring", "find",
			"acquire", "obtain", "scavenge", "pick");
	private static final Set<String> UNSUPPORTED_VERBS = Set.of(
			"build", "craft", "make", "construct", "plant", "sow", "farm", "breed", "tame", "ride", "fly", "swim", "sleep",
			"cook", "smelt", "brew", "enchant", "sell", "buy", "fish", "teleport", "tp", "explore", "scout", "map", "sing",
			"dance", "jump", "open", "close", "light", "place", "put", "repair", "fix", "heal", "cure", "feed", "shear",
			"milk", "read", "write", "tunnel", "bridge", "climb", "sail", "steal", "rob", "burn", "break", "destroy",
			"hunt", "trade", "kiss", "marry", "eat", "drink");
	private static final Set<String> PRONOUN_TARGETS = Set.of(
			"that", "it", "this", "him", "her", "them", "those", "these", "that one", "this one", "that thing", "this thing",
			"the enemy", "the monster", "the mob", "enemies", "monsters", "mobs");
	private static final Set<String> NON_ITEMS = Set.of(
			"help", "a hand", "hand", "you", "your help", "rest", "a rest", "sleep", "a break", "break", "some rest", "time",
			"a moment", "a minute", "going", "lost", "out of here", "ready", "moving", "to work", "started", "inside", "out",
			"away", "back", "home", "there", "here", "it", "that", "them", "this", "a friend", "friends", "company", "advice",
			"a plan", "an idea", "ideas", "directions", "a hug", "food", "something to eat");
	private static final Set<String> GIVE_ALL = Set.of(
			"", "everything", "it all", "all", "all of it", "your items", "your stuff", "your loot", "what you have",
			"what youve got", "what you got", "it", "them", "those", "that", "the items", "the loot", "the stuff",
			"your inventory", "everything you have", "everything youve got", "all your stuff", "all your items",
			"all of your stuff", "all of your items", "your things", "the goods", "all you have");

	private static final Pattern T_HELLO = Pattern.compile("^(hi|hello|hey|heya|hiya|greetings|good (morning|afternoon|evening|day)|howdy|yo|sup|whats up|hail|well met|hullo|hi there|hello there|hey there)( \\w+)?$");
	private static final Pattern T_HOW = Pattern.compile("\\b(how are (you|ya)|how r u|how do you feel|you (ok|okay|alright)\\b|are you (ok|okay|alright|hurt|well|good)|how (are )?you (doing|holding up)|hows it going|how is it going|hows life)");
	private static final Pattern T_WHO = Pattern.compile("\\b(who are you|whats your name|what is your name|tell me (about|something about) (yourself|you)|introduce yourself|where are you from|whats your story|who r u|what do you do)\\b");
	private static final Pattern T_JOKE = Pattern.compile("\\b(joke|make me laugh|something funny|cheer me up|funny story)\\b");
	private static final Pattern T_THANKS = Pattern.compile("^(thanks|thank you|thx|ty|cheers|much appreciated|appreciate it|thank u|many thanks)\\b");
	private static final Pattern T_COMPLIMENT = Pattern.compile("\\b(good (job|work)|well done|nice (work|job|one|going|fighting)|great (job|work)|"
			+ "(youre|you are) (great|awesome|amazing|the best|cool|good|brilliant|wonderful|a good friend|helpful|so helpful|brave|strong|smart|funny)|"
			+ "i (like|love) you|love you|good friend|best companion)\\b");
	private static final Pattern T_INSULT = Pattern.compile("\\b((youre|you are) (useless|stupid|dumb|annoying|bad|terrible|slow|an idiot|worthless|lame|the worst|pathetic)|"
			+ "shut up|idiot|stupid|you suck|i hate you|moron|dummy|useless)\\b");
	private static final Pattern T_BYE = Pattern.compile("^(bye|goodbye|good bye|see (you|ya)|later|farewell|good night|night|cya|ttyl|until next time)\\b");
	private static final Pattern T_LIKES = Pattern.compile("\\b(what do you (like|love|enjoy)|whats your fav(ou?rite)?|fav(ou?rite)|what makes you happy|what are you into)\\b");
	private static final Pattern T_DISLIKES = Pattern.compile("\\b(what do you (hate|dislike|fear)|what dont you like|what are you (afraid|scared) of|what scares you|what annoys you)\\b");
	private static final Pattern T_WHERE = Pattern.compile("\\b(where are we|what is this place|what place is this|where am i|what biome|which biome|what is this biome)\\b");
	private static final Pattern T_MEMORY = Pattern.compile("\\b(remember (when|that|the)|do you remember|what do you remember|our (adventures|journey|travels)|what have we (done|been through))\\b");

	private final Predicate<String> isMobName;

	/**
	 * @param isMobName answers whether a phrase names a creature ("zombie", "cave spider"), used to
	 *                  tell "get that zombie" (attack) from "get some logs" (gather)
	 */
	public CommandParser(Predicate<String> isMobName) {
		this.isMobName = isMobName;
	}

	public ParseResult parse(String message) {
		return parse(message, List.of());
	}

	/**
	 * @param message        what the player typed
	 * @param addresseeNames companion names that may appear as a form of address ("Hazel, follow me")
	 */
	public ParseResult parse(String message, Collection<String> addresseeNames) {
		List<String> tokens = tokenize(message);
		Set<String> names = lowerNames(addresseeNames);
		String unstripped = String.join(" ", tokens.stream().filter(t -> !names.contains(t)).toList());
		if (DISMISS.matcher(unstripped).find() && !RECRUIT.matcher(unstripped).find()) {
			return command(Intent.of(IntentType.DISMISS));
		}
		tokens = stripFillers(tokens, names);
		if (tokens.isEmpty()) {
			return new ParseResult.Talk(TalkTopic.HELLO);
		}
		String c = String.join(" ", tokens);

		ParseResult command = parseCommand(c, tokens);
		if (command != null) {
			return command;
		}
		TalkTopic topic = parseTopic(c, tokens);
		if (topic != null) {
			return new ParseResult.Talk(topic);
		}
		String first = tokens.get(0);
		if (UNSUPPORTED_VERBS.contains(first)) {
			return new ParseResult.Unsupported(first);
		}
		return new ParseResult.Unrecognised();
	}

	private ParseResult parseCommand(String c, List<String> tokens) {
		if (NEGATED_FOLLOW.matcher(c).find() || NEGATED_MOVE.matcher(c).find()) {
			return command(Intent.of(IntentType.WAIT));
		}
		if (CANCEL.matcher(c).matches()) {
			return command(Intent.of(IntentType.CANCEL));
		}
		if (DISMISS.matcher(c).find()) {
			return command(Intent.of(IntentType.DISMISS));
		}
		if (RECRUIT.matcher(c).find()) {
			return command(Intent.of(IntentType.RECRUIT));
		}
		if (INVENTORY.matcher(c).find()) {
			return command(Intent.of(IntentType.INVENTORY));
		}
		if (STATUS.matcher(c).find()) {
			return command(Intent.of(IntentType.STATUS));
		}
		if (COME.matcher(c).matches()) {
			return command(Intent.of(IntentType.COME));
		}
		ParseResult give = parseGive(c);
		if (give != null) {
			return give;
		}
		ParseResult attack = parseAttack(c);
		if (attack != null) {
			return attack;
		}
		if (FOLLOW.matcher(c).find()) {
			return command(Intent.of(IntentType.FOLLOW));
		}
		if (WAIT.matcher(c).find()) {
			return command(Intent.of(IntentType.WAIT));
		}
		return parseGather(c, tokens);
	}

	private ParseResult parseGive(String c) {
		Matcher handOver = HAND_OVER.matcher(c);
		if (handOver.find()) {
			return giveFromRest(handOver.group(2).trim());
		}
		Matcher m = GIVE.matcher(c);
		if (!m.matches()) {
			return null;
		}
		String rest = m.group(4).trim();
		// "give me a hand" / "give me a moment" are not item requests.
		if (rest.equals("a hand") || rest.startsWith("a moment") || rest.startsWith("a second") || rest.startsWith("a minute")
				|| rest.equals("a break") || rest.equals("space")) {
			return null;
		}
		return giveFromRest(rest);
	}

	private ParseResult giveFromRest(String rest) {
		String cleaned = stripWords(rest, Set.of("back", "over", "now"));
		if (GIVE_ALL.contains(cleaned)) {
			return command(Intent.give(null, null));
		}
		List<String> restTokens = new ArrayList<>(Arrays.asList(cleaned.split(" ")));
		Quantity.Result quantity = Quantity.parse(restTokens, 0);
		String item = cleanItemPhrase(restTokens.subList(quantity.consumed(), restTokens.size()));
		if (item.isEmpty() || GIVE_ALL.contains(item) || item.equals("of it") || item.equals("of them")) {
			return command(Intent.give(null, quantity.value()));
		}
		return command(Intent.give(item, quantity.value()));
	}

	private ParseResult parseAttack(String c) {
		if (DEFEND_ME.matcher(c).matches()) {
			return command(Intent.attack(null));
		}
		Matcher help = HELP_FIGHT.matcher(c);
		if (help.matches()) {
			return command(Intent.attack(cleanTarget(help.group(5))));
		}
		Matcher m = ATTACK.matcher(c);
		if (!m.matches()) {
			return null;
		}
		String verb = m.group(1);
		String target = cleanTarget(m.group(2));
		boolean pronoun = PRONOUN_TARGETS.contains(target);
		boolean mob = isMobName.test(target) || isMobName.test(singular(target));
		if (verb.equals("get") && !pronoun && !mob) {
			return null; // "get some logs" is a gather request
		}
		if (verb.equals("hit") && !pronoun && !mob) {
			return null;
		}
		return command(Intent.attack(pronoun ? null : target));
	}

	private ParseResult parseGather(String c, List<String> tokens) {
		if (CHOP_TREES.matcher(c).matches()) {
			return command(Intent.gather("logs", null));
		}
		for (String verb : GATHER_VERBS) {
			if (c.equals(verb) || c.startsWith(verb + " ")) {
				int verbTokens = verb.split(" ").length;
				List<String> rest = new ArrayList<>(tokens.subList(verbTokens, tokens.size()));
				// "get me", "bring us", "pick up", "dig up" leftovers
				while (!rest.isEmpty() && Set.of("me", "us", "up", "down", "back", "out").contains(rest.get(0))) {
					rest.remove(0);
				}
				Quantity.Result quantity = Quantity.parse(rest, 0);
				String item = cleanItemPhrase(rest.subList(quantity.consumed(), rest.size()));
				if (item.isEmpty()) {
					if (verb.contains("chop") || verb.contains("cut")) {
						return command(Intent.gather("logs", quantity.value()));
					}
					return new ParseResult.Unclear(ParseResult.UnclearReason.GATHER_WHAT);
				}
				if (NON_ITEMS.contains(item)) {
					return null;
				}
				if (item.equals("trees") || item.equals("tree") || item.equals("wood") || item.equals("some wood")) {
					item = "logs";
				}
				return command(Intent.gather(item, quantity.value()));
			}
		}
		return null;
	}

	private static TalkTopic parseTopic(String c, List<String> tokens) {
		if (T_HOW.matcher(c).find()) {
			return TalkTopic.HOW;
		}
		if (T_WHO.matcher(c).find()) {
			return TalkTopic.WHO;
		}
		if (T_JOKE.matcher(c).find()) {
			return TalkTopic.JOKE;
		}
		if (T_THANKS.matcher(c).find()) {
			return TalkTopic.THANKS;
		}
		if (T_INSULT.matcher(c).find()) {
			return TalkTopic.INSULT;
		}
		if (T_COMPLIMENT.matcher(c).find()) {
			return TalkTopic.COMPLIMENT;
		}
		if (T_LIKES.matcher(c).find()) {
			return TalkTopic.LIKES;
		}
		if (T_DISLIKES.matcher(c).find()) {
			return TalkTopic.DISLIKES;
		}
		if (T_WHERE.matcher(c).find()) {
			return TalkTopic.WHERE;
		}
		if (T_MEMORY.matcher(c).find()) {
			return TalkTopic.MEMORY;
		}
		if (T_BYE.matcher(c).find()) {
			return TalkTopic.BYE;
		}
		if (tokens.size() <= 4 && T_HELLO.matcher(c).find()) {
			return TalkTopic.HELLO;
		}
		return null;
	}

	private static ParseResult command(Intent intent) {
		return new ParseResult.Command(intent);
	}

	/** Lower-cases, removes punctuation and apostrophes ("don't" -> "dont"), splits on spaces. */
	static List<String> tokenize(String message) {
		if (message == null) {
			return List.of();
		}
		String s = message.toLowerCase(Locale.ROOT)
				.replace('’', '\'')
				.replace("'", "")
				.replaceAll("[^a-z0-9 ]", " ")
				.trim();
		if (s.isEmpty()) {
			return List.of();
		}
		return new ArrayList<>(Arrays.asList(s.split("\\s+")));
	}

	private static Set<String> lowerNames(Collection<String> names) {
		Set<String> lower = new java.util.HashSet<>();
		for (String name : names) {
			lower.addAll(tokenize(name));
		}
		return lower;
	}

	private static List<String> stripFillers(List<String> input, Set<String> names) {
		List<String> tokens = new ArrayList<>(input);
		boolean changed = true;
		while (changed && !tokens.isEmpty()) {
			changed = false;
			String first = tokens.get(0);
			if (names.contains(first) || (tokens.size() > 1 && LEADING_FILLERS.contains(first))) {
				tokens.remove(0);
				changed = true;
				continue;
			}
			for (String phrase : LEADING_PHRASES) {
				List<String> words = Arrays.asList(phrase.split(" "));
				if (tokens.size() > words.size() && tokens.subList(0, words.size()).equals(words)) {
					tokens.subList(0, words.size()).clear();
					changed = true;
					break;
				}
			}
		}
		changed = true;
		while (changed && tokens.size() > 1) {
			changed = false;
			String last = tokens.get(tokens.size() - 1);
			if (names.contains(last)) {
				tokens.remove(tokens.size() - 1);
				changed = true;
				continue;
			}
			for (String phrase : TRAILING_PHRASES) {
				List<String> words = Arrays.asList(phrase.split(" "));
				if (tokens.size() > words.size() && tokens.subList(tokens.size() - words.size(), tokens.size()).equals(words)) {
					tokens.subList(tokens.size() - words.size(), tokens.size()).clear();
					changed = true;
					break;
				}
			}
		}
		return tokens;
	}

	/** Removes articles and location/politeness tails from an item phrase: "the oak logs nearby" -> "oak logs". */
	static String cleanItemPhrase(List<String> words) {
		List<String> w = new ArrayList<>(words);
		Set<String> leading = Set.of("of", "the", "some", "more", "any", "a", "an", "few", "those", "these", "that", "this", "your", "my", "extra");
		while (!w.isEmpty() && leading.contains(w.get(0))) {
			w.remove(0);
		}
		List<List<String>> tails = List.of(
				List.of("nearby"), List.of("around", "here"), List.of("over", "there"), List.of("from", "around", "here"),
				List.of("if", "you", "can"), List.of("too"), List.of("as", "well"), List.of("for", "me"), List.of("for", "us"),
				List.of("please"), List.of("now"), List.of("from", "the", "trees"), List.of("from", "that", "tree"),
				List.of("around"), List.of("here"), List.of("there"), List.of("quickly"));
		boolean changed = true;
		while (changed && !w.isEmpty()) {
			changed = false;
			for (List<String> tail : tails) {
				if (w.size() >= tail.size() && w.subList(w.size() - tail.size(), w.size()).equals(tail)) {
					w.subList(w.size() - tail.size(), w.size()).clear();
					changed = true;
					break;
				}
			}
		}
		return String.join(" ", w).trim();
	}

	private static String cleanTarget(String target) {
		List<String> words = new ArrayList<>(Arrays.asList(target.trim().split(" ")));
		while (!words.isEmpty() && Set.of("the", "a", "an", "those", "these", "that", "this").contains(words.get(0)) && words.size() > 1) {
			words.remove(0);
		}
		while (words.size() > 1 && Set.of("now", "please", "for", "me", "over", "there", "here").contains(words.get(words.size() - 1))) {
			words.remove(words.size() - 1);
		}
		return String.join(" ", words);
	}

	private static String stripWords(String text, Set<String> words) {
		List<String> kept = new ArrayList<>();
		for (String word : text.split(" ")) {
			if (!words.contains(word)) {
				kept.add(word);
			}
		}
		return String.join(" ", kept).trim();
	}

	static String singular(String phrase) {
		if (phrase.endsWith("ies") && phrase.length() > 4) {
			return phrase.substring(0, phrase.length() - 3) + "y";
		}
		if (phrase.endsWith("s") && !phrase.endsWith("ss") && phrase.length() > 3) {
			return phrase.substring(0, phrase.length() - 1);
		}
		return phrase;
	}
}
