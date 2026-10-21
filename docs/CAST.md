# The cast

Twelve original characters, each with a role, a voice and a skin I designed for them. When a companion spawns you'll usually meet one of these; about one in four
is a wanderer wearing one of the game's default skins instead. Every cast member has a fixed
name, look, personality and short backstory, and the backstory also goes into the AI character
sheet so they stay consistent.

Wanderers can wear any of the nine default skins, with wide or slim arms
([wide](images/skins-wide.png), [slim](images/skins-slim.png), [side by side](images/slim-vs-wide.png)).

![Dasha, Charlie, Hazel and Birintan](images/cast/group-1.png)
![Maddie, AJ, Daisy and Cole](images/cast/group-2.png)
![Samuel, Honey, Luna and Luke](images/cast/group-3.png)

| Name | Role | Voice | Look | Who they are |
|---|---|---|---|---|
| Charlie (he/him) | Guardian | Sellsword | Short dark hair, salt-and-pepper beard, scar over the brow; oxblood quilted gambeson over mail, one steel pauldron | A scarred, dry-witted veteran who has outlived three mercenary companies and always has a clay pipe and a pouch of herbs he picked that morning |
| Dasha (she/her, slim) | Guardian | Guardian | Long golden hair with thin braids framing her face; teal tunic with silver trim, laced leather corset, white fur mantle, fur-topped boots | A shieldmaiden from the north who left her clan to see the world |
| AJ (he/him) | Guardian | Rogue | Tousled chestnut hair, stubble, gold earring; plum doublet with brass buttons over a loose white shirt, bandolier | The funny one: a cheerful chancer who keeps trying his luck with anyone who'll talk to him, never gets anywhere, and takes it on the chin |
| Cole (he/him) | Guardian | Rogue | Tousled golden-blond hair, clean-shaven, freckles, an arched brow and a smirk; black leather brigandine with gold rivets, one steel pauldron, crimson cuffs, buff riding breeches, tall boots, a rapier and a half-cape | A cheeky, quick-witted adventurer who talks his way into trouble and out again |
| Maddie (she/her, slim) | Builder | Rogue | Dark shoulder-length waves under a crimson bandana, green eyes; cream blouse, brown leather vest with brass toggles, crimson sash, a dagger at the hip, tall boots | A sharp-tongued builder-for-hire who has patched more roofs and old tomb walls than she'll admit |
| Birintan (he/him) | Builder | Naturalist | Dark curls, goatee, a sprig of herbs behind his ear; olive vest, rolled sleeves, green neckerchief, little vials on his belt | A curious, quick-minded tinkerer who works out how a thing goes together before he builds it |
| Luke (he/him) | Builder | Mystic | Black hair tied back with one silver streak, amber eyes; linen shirt with rolled sleeves under a brown leather jerkin, emerald scarf, leather bracers, a belt with a spellbook and a pouch, and a glowing amber charm | A young sorcerer who shapes stone and timber the way others cast spells, speaks rarely and notices everything |
| Hazel (she/her, slim) | Collector | Naturalist | Long auburn hair with a white flower, freckles, green eyes; sage coat over a cream blouse and laced corset, satchel strap | A scholar-ranger who sketches every creature and flower she meets |
| Daisy (she/her, slim) | Collector | Guardian | Long dark braid over one shoulder, gold circlet; silver breastplate with gold trim, white tabard with a golden sun, royal-blue cape | A steady woodland warden who keeps travellers supplied on the wild roads and guards everything she gathers |
| Luna (she/her, slim) | Collector | Mystic | Waist-length silver-lavender hair, violet eyes, gold circlet with a blue gem; midnight tunic over leggings, leather bracers, tall buckled boots, violet cape stitched with stars, amethyst amulet | A stargazer who reads omens in the night sky and scouts for what they promise |
| Samuel (he/him) | Farmer | Farmhand | Dark brown curls, freckles, green eyes; cream shirt with rolled sleeves, denim overalls with a green patch and brass buckles, red kerchief, muddy boots | A farm lad still not sure leaving home was a good idea |
| Honey (she/her, slim) | Farmer | Farmhand | Honey-blonde waves and a flower crown, freckles; puff-sleeved blouse with the sleeves rolled up, sky-blue laced bodice and short skirt over leggings, satchel, sturdy boots | A sunny farm girl who calms animals and bakes when she's nervous |

**Role** is what they're best at (see [GUIDE.md](GUIDE.md#implemented-roles)). **Voice** is the
personality they talk in (see [PERSONALITIES.md](PERSONALITIES.md)). Four of them have extra lines
of their own on top of their voice: Charlie is gruff and funny and picks herbs for his pipe,
Cole is cheeky and clever, Birintan is curious and clever, and AJ is the funny one who never gets
the girl.

## Design notes

The women have taller eyes with lashes and a catch-light, a soft blush and small rosy lips. Long
hair frames the face on the outer layer and falls down the back and over the shoulders. The men
keep simpler, sturdier faces with brows, beards and stubble.

I wanted everyone to be easy to tell apart at a distance, so each character has a signature colour
(Dasha's teal, Charlie's oxblood, Hazel's sage, Maddie's crimson bandana, Cole's black and gold,
Daisy's white and gold, Luna's midnight blue) and a silhouette detail on the outer layer, like a
fur mantle, bandana, cape, coat tails or crown.

The gear is practical adventuring stuff (belts, straps, corsets, bracers and boots) rather than
over-the-top costumes, in the spirit of classic fantasy adventures.
For shading I used a four-step colour ramp per material, with cooler shadows and warmer
highlights, side and back faces a little darker, and no random noise.

I checked them all in the game with front and back line-ups, close-ups and a running shot
([walking](images/cast/walking.png), [back view](images/cast/lineup-back.png)). There's also a flat
sheet of all twelve, front and back: [skin-sheet.png](images/cast/skin-sheet.png).

The skins are generated by [`tools/skins/cast.py`](../tools/skins/cast.py) (plain Python, no
dependencies). The men are in `cast.py` and the women in [`women.py`](../tools/skins/women.py),
built from reusable pieces in [`pieces.py`](../tools/skins/pieces.py) and
[`details.py`](../tools/skins/details.py). Change a colour and run `python tools/skins/cast.py` to
regenerate. They're original art, MIT-licensed with the rest of the repo.

## Names

The twelve cast names are reserved for these characters. Wanderers use separate name pools per
personality, cleaned up so no two names are easy to confuse in chat.

### Older worlds

Companions saved by an older version of the mod load as the current cast automatically,
with the same skin, memories and pack.

## Commands

- `/companion summon hazel` summons a specific cast member (with cheats on).
- `/companion skin <name> <outfit>` changes a companion's look to a default skin (`noor slim`)
  or a cast outfit (`daisy`).
