"""Adds the 0.4 dialogue keys to every bank. Safe to re-run: existing lines are kept, new ones appended once."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'hearthbound', 'dialogue')

NEW = {
    'sellsword': {
        'ask.join_first': ["Not on your crew yet. Want me to sign on first, then do it?", "I don't work for strangers. Say yes and I'll join, then get it done.", "Join you first, then the job? Say yes."],
        'ask.build_what': ["Build what? {things}. Pick one.", "I can do {things}. Which?", "{things}. Which one do you want?"],
        'talk.why': ["Because that's how it is.", "Reasons. Mostly practical ones.", "Long story. Short answer: it works."],
        'talk.really': ["Really.", "Would I lie to you?", "Yes. Don't make me say it twice."],
        'talk.more': ["There's not much more to it.", "That's the short of it. The long of it is boring.", "Ask me something specific."],
        'talk.and_you': ["Me? Still standing.", "I'm fine. You?", "Same as always. Sharp and a bit sore."],
        'talk.mean': ["Meant what I said.", "Plain enough, I thought. Ask it another way.", "Say what you want to know, straight."],
        'talk.mention.mob': ["{thing}? Keep your guard up around those.", "Ah, {thing}. I've bled for less.", "{thing}. Not a fight I'd pick, not one I'd run from."],
        'talk.mention.thing': ["{thing}. Useful, if you can carry it.", "{thing}? Fine by me.", "Hm. {thing}. Go on."],
        'talk.generic': ["Keep talking. I'm listening, mostly.", "Fair."],
        'talk.question': ["Beats me.", "Ask the next one. I might know that."],
    },
    'naturalist': {
        'ask.join_first': ["I'm not travelling with you yet. Shall I join you first, and then do it?", "Happy to! Do you want me to come along first? Say yes and I will.", "I'd love to help. Join up first, then the work?"],
        'ask.build_what': ["Ooh, what shall I build? {things}?", "I can manage {things}. Which one would you like?", "{things}. Which sounds right?"],
        'talk.why': ["Oh, there's a reason for most things, if you watch long enough.", "Good question. I think it comes down to how things grow.", "Because everything is connected, somehow."],
        'talk.really': ["Really! I wouldn't make it up.", "Truly. I've seen it myself.", "Yes! Surprising, isn't it?"],
        'talk.more': ["Oh, there's loads more. Where shall I start?", "I could go on all day. Stop me if I do.", "Well, there's a bit more to it, actually."],
        'talk.and_you': ["Me? Quite well. The air out here helps.", "I'm good, thank you for asking!", "Happy, mostly. And curious. You?"],
        'talk.mean': ["Oh, sorry, was I unclear? Let me try again.", "I mean it plainly, I think. What part is odd?", "Ask me again, gently? I'll do better."],
        'talk.mention.mob': ["{thing}! Fascinating creatures, in their way.", "Oh, {thing}. I've watched them for hours. From a distance.", "{thing}? Tell me what you saw."],
        'talk.mention.thing': ["{thing}! I do love that.", "Ah, {thing}. There's more to that than you'd think.", "{thing}? Now that's interesting."],
        'talk.generic': ["Mm, tell me more about that?", "I'm still thinking it over, in a nice way."],
        'talk.question': ["I'm not sure, but I'd love to find out.", "Good question. I'll think on it."],
    },
    'rogue': {
        'ask.join_first': ["I'm not on the payroll yet. Join up first, then I do the job? Say yes.", "Free work for a stranger? No. Sign me on and it's a deal. Yes?", "Hire me first, partner, then I'll do it. Yes?"],
        'ask.build_what': ["Build what, boss? {things}.", "{things}. Take your pick.", "I can do {things}. What'll it be?"],
        'talk.why': ["Why not, is my usual answer.", "Profit, mostly. Or fun. Sometimes both.", "Long story, and it costs extra."],
        'talk.really': ["Really. Would I kid a friend?", "Cross my heart. Mostly.", "Absolutely. Check for yourself, if you're nervous."],
        'talk.more': ["Oh, there's more. There's always more. For a price.", "Alright, a bit more, since it's you.", "That's most of it. The rest is gossip."],
        'talk.and_you': ["Me? Better than I look. Which isn't hard.", "Living the dream. You?", "Can't complain. Well, I can. I won't."],
        'talk.mean': ["Meant what I said. Which part lost you?", "Come again? I'll say it slower.", "Don't overthink it, boss."],
        'talk.mention.mob': ["{thing}? Nasty. Profitable, sometimes.", "Ha, {thing}. Don't turn your back on them.", "{thing}. I have stories. None end well."],
        'talk.mention.thing': ["{thing}? Now you're talking.", "Ooh, {thing}. Worth a coin or two.", "{thing}. Keep going, I'm intrigued."],
        'talk.generic': ["Is that so? Go on.", "Right, right. And then?"],
        'talk.question': ["Couldn't tell you. Could find out, for a fee.", "No idea, boss. Guess away."],
    },
    'guardian': {
        'ask.join_first': ["I do not yet walk with you. Shall I join you first, and then see to it? Say yes.", "Gladly, but I should join you properly first. Yes?", "Let me pledge to your company first, then I will do it. Yes?"],
        'ask.build_what': ["What shall I raise? {things}?", "I can build {things}. Which will serve you?", "{things}. Tell me which, and I will begin."],
        'talk.why': ["Because it is right, or the nearest thing to it.", "A fair question. The answer is duty, mostly.", "I have thought on it long. It comes to this: it is what I must do."],
        'talk.really': ["Truly. I would not say it otherwise.", "Yes. You have my word.", "I do not speak idly. Yes."],
        'talk.more': ["There is more, though it is not all easy to tell.", "If you wish to hear it, I will speak on.", "A little more, then. Listen well."],
        'talk.and_you': ["I am well, and glad of your company.", "Steady, thank you. And yourself?", "Well enough. Better for asking."],
        'talk.mean': ["Forgive me. I will say it plainly.", "I meant only what I said. What troubles you in it?", "Let me put it another way."],
        'talk.mention.mob': ["{thing}. A real danger. Stay behind me.", "Ah, {thing}. I have met them. I will not flinch.", "{thing}? Tell me where, and I will watch for them."],
        'talk.mention.thing': ["{thing}. A good thing to speak of.", "{thing}? That is worth remembering.", "Ah, {thing}. Go on, I am listening."],
        'talk.generic': ["That is worth thinking on. Go on.", "I hear you."],
        'talk.question': ["I do not know. I wish I did.", "A fine question. I have no answer yet."],
    },
    'farmhand': {
        'ask.join_first': ["Oh! I'm not with you yet. Want me to join up first and then do it? Say yes!", "I'd love to, but we're not travelling together. Join first, then the job? Yes?", "Sure! But can I join you first? Say yes!"],
        'ask.build_what': ["Ooh, build what? {things}?", "I can do {things}! Which one?", "{things}! Pick one, please!"],
        'talk.why': ["Hm, I'm not sure! It just seems right.", "My ma always said it was just how things go.", "Good question! Wish I knew."],
        'talk.really': ["Yes, really! Honest!", "Really, really. I wouldn't fib!", "Oh yeah! Cross my heart!"],
        'talk.more': ["Oh, there's more! Hang on, let me think.", "I can tell you more, if you like!", "A bit more, sure. Where was I?"],
        'talk.and_you': ["Me? I'm good! Really good!", "Pretty well! Better with you here!", "Happy as a pig in clover. You?"],
        'talk.mean': ["Oh, sorry! Was that muddled? I'll try again!", "Hm? I meant what I said! I think!", "Um, ask me again? Slowly?"],
        'talk.mention.mob': ["{thing}! Oh, those scare me a bit!", "Ooh, {thing}! Stay close, okay?", "{thing}? Yikes. I'll be brave. Mostly."],
        'talk.mention.thing': ["{thing}! Oh, I like that!", "Ooh, {thing}! Tell me more!", "{thing}? That's a good one!"],
        'talk.generic': ["Oh! Interesting! Go on!", "Hm! I'll think on that!"],
        'talk.question': ["Gosh, I don't know! Do you?", "Ooh, good one. I'm not sure!"],
    },
    'mystic': {
        'ask.join_first': ["I walk alone, for now. Shall I join you, then do it? Say yes.", "Join first, then work. If you want that, say yes.", "I am not yours yet. Say yes, and I will be, and the work will follow."],
        'ask.build_what': ["Build what? {things}. Be precise.", "{things}. One of them.", "I can shape {things}. Choose."],
        'talk.why': ["Because the pattern allows it.", "Why is a deep well. Shallow answer: because.", "Ask me again when the moon is up."],
        'talk.really': ["Yes. Really.", "I rarely say what is not so.", "Truly. Though truth bends a little."],
        'talk.more': ["There is more. There is always more, underneath.", "A little more, if you listen.", "Enough for now. The rest unfolds."],
        'talk.and_you': ["I am. Which is most of it.", "Quiet. Content. You?", "Well. In my own odd way."],
        'talk.mean': ["I meant what I said. Perhaps sideways.", "Listen again. It will come.", "Do not chase the meaning. Let it find you."],
        'talk.mention.mob': ["{thing}. They have their own dark logic.", "Ah. {thing}. Be watchful.", "{thing}? I have felt them near, at times."],
        'talk.mention.thing': ["{thing}. Interesting. Go on.", "{thing}? There is more in that than most see.", "Hm. {thing}. Noted."],
        'talk.generic': ["Mm. Go on.", "Perhaps. Say more."],
        'talk.question': ["I do not know. That is also an answer.", "Hm. Ask the stars. They won't reply either."],
    },
}

for archetype, keys in NEW.items():
    path = os.path.join(ROOT, archetype + '.json')
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    lines = data['lines']
    for key, added in keys.items():
        existing = lines.setdefault(key, [])
        for line in added:
            if line not in existing:
                existing.append(line)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')
print('ok')
