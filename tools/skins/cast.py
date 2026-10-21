"""
The Hearthbound cast: twelve original adventurers, two per personality.
Run `python tools/skins/cast.py` to (re)generate the skin textures and previews.

Every skin is original pixel art drawn by this script; nothing is traced or copied.
"""
import os
import sys

from skinlib import Part, Ramp, Skin, render_preview, save_rgba, shade, hex_rgb
import pieces as P

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hearthbound', 'textures', 'entity', 'companion')


def bram():
    """Sellsword. Scarred veteran: oxblood gambeson over mail, one steel pauldron, salt-and-pepper beard."""
    s = Skin(slim=False)
    skin, hair = Ramp('#c99a76'), Ramp('#3b2a20')
    P.body_skin(s, skin)
    P.chainmail(s, Ramp('#8d9196'), parts=('body', 'rarm', 'larm'), rows=(0, 9))
    P.trousers(s, Ramp('#4a4038'))
    P.boots(s, Ramp('#5a3a24'), top_row=8, cuff=Ramp('#6e4a2e'))
    P.coat(s, Ramp('#7a2e2a'), rows=(0, 11), open_front=False, sleeves_to=6, skirt_rows=3)
    P.quilting(s, Ramp('#7a2e2a'), rows=range(1, 11), every=3)
    P.belt(s, Ramp('#3a2618'), buckle=hex_rgb('#c9a227'), row=8, height=1, layer='jacket')
    P.pouch(s, Ramp('#5a3a24'), col=6, row=9, layer='jacket')
    P.bracers(s, Ramp('#5b3b22'), rows=(7, 9))
    P.pauldron(s, Ramp('#a7adb4'), side='right', rows=(0, 2))
    P.hair(s, hair, 'short', fringe='none')
    P.face(s, skin, eyes=hex_rgb('#4b6b8a'), brows=hair['deep'], mouth='stern', scar=True)
    P.beard(s, hair, 'short')
    # grey flecks in beard and temples
    head = Part(s, 'head')
    for col, row in ((1, 7), (6, 7), (0, 6)):
        head.paint(head.front_x(col), row, hex_rgb('#8a8580'))
    head.paint(2, 2, hex_rgb('#8a8580'))
    return s


def hilde():
    """Sellsword. Northern shieldmaiden: blonde braids, leather lamellar over a slate tunic, fur collar."""
    s = Skin(slim=False)
    skin, hair = Ramp('#efd2bd'), Ramp('#e0bf72')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#4f6a8a'), arms_to=9)
    P.trousers(s, Ramp('#5c5650'))
    P.boots(s, Ramp('#4a3424'), top_row=8, cuff=Ramp('#d9d4cc'))
    lamellar = Ramp('#6b4a2e')
    P.coat(s, lamellar, rows=(1, 10), open_front=False, sleeves_to=2)
    P.quilting(s, lamellar, rows=range(2, 10), every=2)
    P.fur_collar(s, Ramp('#d9d4cc'))
    P.belt(s, Ramp('#3a2618'), buckle=hex_rgb('#9aa0a8'), row=8, layer='jacket')
    P.bracers(s, Ramp('#6b4a2e'), rows=(6, 9))
    P.hair(s, hair, 'braids', fringe='centre')
    P.face(s, skin, eyes=hex_rgb('#5b8cc7'), brows=shade(hair['deep'], -0.12), mouth='neutral', lip=hex_rgb('#c98d84'))
    # leather headband holding the braids back
    hat = Part(s, 'hat')
    for x in range(hat.wrap_width):
        hat.paint(x, 1, hex_rgb('#6b4a2e') if x % 4 else hex_rgb('#8a6440'))
    return s


def wren():
    """Naturalist. Scholar-ranger: auburn bun with a quill, open moss-green coat, satchel strap, freckles."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f1d0b5'), Ramp('#a4502a')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#e9e1cc'), arms_to=9, neck='v', skin=skin)
    P.trousers(s, Ramp('#8b7355'))
    P.boots(s, Ramp('#5a3d26'), top_row=8, cuff=Ramp('#7a5636'))
    P.coat(s, Ramp('#4f6b3a'), rows=(0, 11), open_front=True, opening=(3, 4), sleeves_to=8, cuff=Ramp('#3e5530')['base'], skirt_rows=4)
    P.strap(s, Ramp('#6b4a2e'), direction='down_right', buckle=hex_rgb('#c9a227'))
    P.belt(s, Ramp('#5a3d26'), buckle=hex_rgb('#c9a227'), row=8, layer='body')
    P.pouch(s, Ramp('#a07a50'), col=1, row=9, layer='body')
    P.hair(s, hair, 'bun', fringe='side')
    P.face(s, skin, eyes=hex_rgb('#4f8a4a'), brows=hair['lo'], mouth='smile', lip=hex_rgb('#c98074'), freckles=True)
    # a quill tucked into the bun
    hat = Part(s, 'hat')
    hat.paint(hat.back_x(5), 0, (236, 232, 222))
    hat.paint(hat.back_x(6), 1, (214, 210, 200))
    return s


def tobias():
    """Naturalist. Gentle herbalist: curly hair, round spectacles, olive vest, rolled sleeves, vials on the belt."""
    s = Skin(slim=False)
    skin, hair = Ramp('#8d5a3b'), Ramp('#2b1d14')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#e6dfcf'), arms_to=4, neck='v', skin=skin)
    P.rolled_sleeves(s, Ramp('#e6dfcf'), roll_row=4)
    P.trousers(s, Ramp('#5e4632'))
    P.boots(s, Ramp('#3e2a1c'), top_row=9)
    vest = Ramp('#6b6b2f')
    P.coat(s, vest, rows=(0, 9), open_front=True, opening=(3, 4), sleeves_to=-1)
    for y in (2, 4, 6):
        P.emblem(s, 'jacket', [(2, y)], hex_rgb('#c9a227'))
    # neckerchief
    P.emblem(s, 'jacket', [(2, 0), (3, 0), (4, 0), (5, 0), (3, 1), (4, 1)], hex_rgb('#2f5a3a'))
    P.belt(s, Ramp('#4a3020'), buckle=hex_rgb('#c9a227'), row=9, layer='body')
    # little coloured vials on the belt
    P.emblem(s, 'body', [(0, 10)], hex_rgb('#b0404a'))
    P.emblem(s, 'body', [(1, 10)], hex_rgb('#3f74c8'))
    P.emblem(s, 'body', [(6, 10)], hex_rgb('#4aa060'))
    P.hair(s, hair, 'curly', fringe='messy')
    P.face(s, skin, eyes=hex_rgb('#3b2a1e'), brows=hair['base'], mouth='smile', lip=hex_rgb('#6e3e2c'))
    P.beard(s, hair, 'goatee')
    # a sprig of herbs tucked behind the ear
    hat = Part(s, 'hat')
    hat.paint(hat.d + hat.w + 2, 2, hex_rgb('#5aa04a'))
    hat.paint(hat.d + hat.w + 3, 1, hex_rgb('#7cc060'))
    return s


def cass():
    """Rogue. Treasure hunter: black bob, charcoal leather jacket, crimson scarf, crossed belts, hood down."""
    s = Skin(slim=True)
    skin, hair = Ramp('#d6a77f'), Ramp('#1c1a22')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#3a3a40'), arms_to=9)
    P.trousers(s, Ramp('#2f2c33'))
    P.boots(s, Ramp('#1f1c22'), top_row=6)
    jacket_ramp = Ramp('#3a3742')
    P.coat(s, jacket_ramp, rows=(0, 9), open_front=True, opening=(3, 4), sleeves_to=9, trim=hex_rgb('#55505e'))
    P.gloves(s, Ramp('#3b2a22'), rows=(10, 11), fingertips_skin=skin['base'])
    # crossed belts
    P.strap(s, Ramp('#5a3a24'), direction='down_right', back=False, buckle=hex_rgb('#b8b8c0'))
    P.strap(s, Ramp('#5a3a24'), direction='down_left', back=False)
    P.hood_down(s, Ramp('#4a4452'))
    # crimson scarf with a tail
    scarf = Ramp('#9e2b2b')
    jacket = Part(s, 'jacket')
    for x in range(jacket.wrap_width):
        jacket.paint(x, 0, scarf['base'])
    for y in (1, 2, 3, 4):
        jacket.paint(jacket.front_x(1), y, scarf['lo'] if y % 2 else scarf['base'])
    P.belt(s, Ramp('#6b4630'), buckle=hex_rgb('#b8b8c0'), row=9, layer='jacket')
    # scarf tail and a small pack on the back
    for y in (1, 2, 3):
        jacket.paint(jacket.back_x(6), y, scarf['lo'])
    for y in range(4, 8):
        for bx in range(2, 6):
            jacket.paint(jacket.back_x(bx), y, Ramp('#6b4630')['base'] if y > 4 else Ramp('#6b4630')['hi'])
    P.hair(s, hair, 'bob', fringe='side', highlight_cols=(2,))
    P.face(s, skin, eyes=hex_rgb('#3b2f2a'), brows=hair['base'], mouth='smirk', lip=hex_rgb('#9a5a4a'))
    return s


def marlo():
    """Rogue. Charming thief: tousled chestnut hair, stubble, plum doublet with brass buttons, gold earring."""
    s = Skin(slim=False)
    skin, hair = Ramp('#e0b08a'), Ramp('#5a3420')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#ece8e0'), arms_to=9, neck='open', skin=skin)
    P.trousers(s, Ramp('#3d2b22'))
    P.boots(s, Ramp('#2e2019'), top_row=8, cuff=Ramp('#4a3226'))
    doublet = Ramp('#5e2b4f')
    P.coat(s, doublet, rows=(1, 9), open_front=True, opening=(3, 4), sleeves_to=-1, trim=hex_rgb('#7a3d66'))
    for y in (3, 5, 7):
        P.emblem(s, 'jacket', [(2, y), (5, y)], hex_rgb('#c9a227'))
    P.strap(s, Ramp('#6b4a2e'), direction='down_left', buckle=hex_rgb('#c9a227'))
    P.belt(s, Ramp('#4a3020'), buckle=hex_rgb('#c9a227'), row=9, layer='body')
    P.pouch(s, Ramp('#6b4a2e'), col=6, row=10)
    P.hair(s, hair, 'short', fringe='messy', highlight_cols=(1,))
    P.face(s, skin, eyes=hex_rgb('#6b4a2b'), brows=hair['base'], mouth='smirk', lip=hex_rgb('#b07a62'))
    P.beard(s, hair, 'stubble', skin=skin)
    # gold earring on the left ear
    head = Part(s, 'head')
    head.paint(head.d + head.w + 4, 5, hex_rgb('#e0c050'))
    return s


def isolde():
    """Guardian. Paladin: polished breastplate under a white tabard with a golden sun, dark hair tied back."""
    s = Skin(slim=False)
    skin, hair = Ramp('#6b4430'), Ramp('#20160f')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#2f4c86'), arms_to=9)
    steel = Ramp('#b8bec6')
    P.plate(s, steel, 'body', rows=(0, 6))
    P.trousers(s, Ramp('#26324d'))
    P.boots(s, Ramp('#7a8088'), top_row=8)
    P.plate(s, steel, 'rpants', rows=(3, 7), faces=('front', 'right', 'left'))
    P.plate(s, steel, 'lpants', rows=(3, 7), faces=('front', 'right', 'left'))
    # white tabard with blue edges, front and back
    tabard = Ramp('#e8e6e0')
    jacket = Part(s, 'jacket')
    for y in range(3, 12):
        for fx in range(1, 7):
            tone = tabard['base'] if fx not in (1, 6) else hex_rgb('#3a5aa0')
            if y == 11:
                tone = tabard['lo']
            jacket.paint(jacket.front_x(fx), y, tone)
            jacket.paint(jacket.back_x(fx), y, shade(tone, -0.04))
    sun = hex_rgb('#d8b44a')
    P.emblem(s, 'jacket', [(3, 5), (4, 5), (3, 6), (4, 6), (2, 5), (5, 6), (3, 4), (4, 7)], sun)
    P.belt(s, Ramp('#4a3020'), buckle=hex_rgb('#d8b44a'), row=8, layer='jacket')
    P.pauldron(s, steel, side='right', rows=(0, 2))
    P.pauldron(s, steel, side='left', rows=(0, 2))
    P.bracers(s, steel, rows=(7, 9))
    P.gloves(s, Ramp('#8a9098'), rows=(10, 11))
    P.hair(s, hair, 'tied', fringe='centre')
    P.face(s, skin, eyes=hex_rgb('#3a2618'), brows=hair['base'], mouth='neutral', lip=hex_rgb('#5a3226'))
    return s


def aldric():
    """Guardian. Grey-bearded knight-protector: blue surcoat with a silver tower crest over mail and plate."""
    s = Skin(slim=False)
    skin, hair = Ramp('#e8c2a6'), Ramp('#9a9a98')
    P.body_skin(s, skin)
    P.chainmail(s, Ramp('#8d9196'), parts=('body', 'rarm', 'larm'), rows=(0, 9))
    P.trousers(s, Ramp('#3a3a40'))
    P.boots(s, Ramp('#8a9098'), top_row=8)
    surcoat = Ramp('#243a6e')
    P.coat(s, surcoat, rows=(1, 11), open_front=False, sleeves_to=-1, skirt_rows=4)
    silver = hex_rgb('#c8ccd2')
    jacket = Part(s, 'jacket')
    for x in range(jacket.wrap_width):
        jacket.paint(x, 1, silver)
    # heraldic chevron with a star above
    P.emblem(s, 'jacket', [(3, 3), (4, 3), (3, 5), (4, 5), (2, 6), (5, 6), (1, 7), (6, 7)], silver)
    P.belt(s, Ramp('#4a3020'), buckle=silver, row=8, layer='jacket')
    steel = Ramp('#a7adb4')
    P.pauldron(s, steel, side='right', rows=(0, 3))
    P.pauldron(s, steel, side='left', rows=(0, 3))
    P.plate(s, steel, 'rpants', rows=(4, 7), faces=('front', 'right', 'left'))
    P.plate(s, steel, 'lpants', rows=(4, 7), faces=('front', 'right', 'left'))
    P.gloves(s, Ramp('#6b4a2e'), rows=(9, 11))
    P.hair(s, hair, 'crop', fringe='none')
    P.face(s, skin, eyes=hex_rgb('#5a7a9a'), brows=hair['lo'], mouth='stern', lip=hex_rgb('#8a5a50'))
    P.beard(s, hair, 'full')
    return s


def pip():
    """Farmhand. Farm lad: messy black curls, freckles, yellow shirt, brown overalls with a patch, red kerchief."""
    s = Skin(slim=False)
    skin, hair = Ramp('#9a6744'), Ramp('#1e1610')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#d9c27a'), arms_to=4)
    P.rolled_sleeves(s, Ramp('#d9c27a'), roll_row=4)
    overalls = Ramp('#6b5a3a')
    P.trousers(s, overalls, to_row=7)
    # rolled cuffs and socks, then muddy boots
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        for x in range(p.wrap_width):
            p.paint(x, 7, overalls['hi'])
            p.paint(x, 8, hex_rgb('#e2d8c0'))
    P.boots(s, Ramp('#4a3626'), top_row=9)
    for leg, xs in (('rleg', (5, 10)), ('lleg', (2, 13))):
        p = Part(s, leg)
        for x in xs:
            p.paint(x, 10, hex_rgb('#7a5a3a'))
    # bib and straps
    jacket = Part(s, 'jacket')
    for y in range(4, 12):
        for fx in range(1, 7):
            jacket.paint(jacket.front_x(fx), y, overalls['base'] if y < 11 else overalls['lo'])
        for bx in range(1, 7):
            if y >= 7:
                jacket.paint(jacket.back_x(bx), y, overalls['lo'])
    for y in range(0, 4):
        for fx in (1, 6):
            jacket.paint(jacket.front_x(fx), y, overalls['lo'])
            jacket.paint(jacket.back_x(fx), y, overalls['lo'])
    P.emblem(s, 'jacket', [(1, 4), (6, 4)], hex_rgb('#b8b8b0'))
    P.emblem(s, 'jacket', [(4, 7), (5, 7), (4, 8), (5, 8)], hex_rgb('#8a7a52'))  # patch
    P.emblem(s, 'body', [(2, 0), (3, 0), (4, 0), (5, 0), (3, 1), (4, 1)], hex_rgb('#b23a2e'))  # kerchief
    P.hair(s, hair, 'curly', fringe='messy')
    P.face(s, skin, eyes=hex_rgb('#3b2a1e'), brows=hair['base'], mouth='smile', lip=hex_rgb('#6e3e2c'), freckles=True)
    return s


def tilly():
    """Farmhand. Cheerful farm girl: ginger braids, red headscarf, green dress, cream apron and blouse."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f3d6c2'), Ramp('#c8642c')
    P.body_skin(s, skin)
    blouse = Ramp('#efe6d2')
    P.shirt(s, blouse, arms_to=4, neck='round')
    P.rolled_sleeves(s, blouse, roll_row=4)
    dress = Ramp('#4f7a3a')
    body = Part(s, 'body')
    body.fill_rows(3, 11, dress)
    for y in (4, 6):
        P.emblem(s, 'body', [(3, y), (4, y)], hex_rgb('#e8dcc0'))  # bodice laces
    # stockings and boots
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#4a3a30'))
    P.boots(s, Ramp('#6b4a2e'), top_row=9)
    # skirt (outer layer on the legs) with an apron in front
    for leg in ('rpants', 'lpants'):
        p = Part(s, leg)
        for y in range(0, 7):
            for x in range(p.wrap_width):
                face, fx = p.face_of(x)
                inner = (leg == 'rpants' and face == 'left') or (leg == 'lpants' and face == 'right')
                if inner:
                    continue
                p.paint(x, y, dress['lo'] if y == 6 else dress['base'])
            for fx in range(4):
                if (leg == 'rpants' and fx >= 1) or (leg == 'lpants' and fx <= 2):
                    p.paint(p.front_x(fx), y, shade(blouse['base'], -0.02 if y < 6 else -0.08))
    jacket = Part(s, 'jacket')
    for y in range(8, 12):
        for fx in range(1, 7):
            jacket.paint(jacket.front_x(fx), y, blouse['base'] if y < 11 else blouse['lo'])
    P.belt(s, Ramp('#d8cdb2'), buckle=hex_rgb('#e8dcc0'), row=8, layer='jacket')
    P.hair(s, hair, 'braids', fringe='side')
    # red headscarf band on the hat layer
    hat = Part(s, 'hat')
    for x in range(hat.wrap_width):
        hat.paint(x, 1, hex_rgb('#b23a2e'))
    P.face(s, skin, eyes=hex_rgb('#5a8a5a'), brows=hair['lo'], mouth='smile', lip=hex_rgb('#d0807a'), freckles=True)
    return s


def sable():
    """Mystic. Violet-robed wanderer: long black hair with a silver streak, amethyst pendant, sash and trim."""
    s = Skin(slim=True)
    skin, hair = Ramp('#e9d6cf'), Ramp('#16131c')
    P.body_skin(s, skin)
    robe = Ramp('#3d2a55')
    P.shirt(s, robe, arms_to=9)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#24202c'))
    P.boots(s, Ramp('#2c2430'), top_row=9)
    trim = hex_rgb('#b8b8c8')
    P.coat(s, robe, rows=(0, 11), open_front=False, sleeves_to=9, cuff=trim, skirt_rows=9)
    jacket = Part(s, 'jacket')
    for y in range(1, 12):
        jacket.paint(jacket.front_x(3), y, trim if y % 2 else shade(trim, -0.08))
    P.belt(s, Ramp('#5a2e6e'), buckle=hex_rgb('#b07ae0'), row=8, height=2, layer='jacket')
    P.emblem(s, 'jacket', [(4, 2), (4, 3)], hex_rgb('#b07ae0'))  # amethyst pendant
    P.emblem(s, 'jacket', [(4, 1)], hex_rgb('#c8c8d4'))
    P.hood_down(s, Ramp('#2e1f42'))
    P.hair(s, hair, 'long', fringe='side', highlight_cols=())
    head = Part(s, 'head')
    for row in range(0, 3):
        head.paint(head.front_x(6), row, hex_rgb('#c8c8d4'))  # silver streak
    hat = Part(s, 'hat')
    hat.paint(hat.back_x(1), 2, hex_rgb('#c8c8d4'))
    hat.paint(hat.back_x(1), 3, hex_rgb('#a8a8b8'))
    P.face(s, skin, eyes=hex_rgb('#8a5ac8'), brows=hair['base'], mouth='neutral', lip=hex_rgb('#8a5a7a'))
    return s


def ashe():
    """Mystic. Hooded seer: ash-grey cloak with the hood up, amber eyes, glowing teal rune marks, rope belt."""
    s = Skin(slim=False)
    skin, hair = Ramp('#4a2f22'), Ramp('#151012')
    P.body_skin(s, skin)
    tunic = Ramp('#2a4f55')
    P.shirt(s, tunic, arms_to=9)
    P.trousers(s, Ramp('#2a2826'))
    P.boots(s, Ramp('#5a5048'), top_row=7)
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        for x in range(p.wrap_width):
            p.paint(x, 8, hex_rgb('#6e6258'))  # boot wraps
    cloak = Ramp('#7d7a76')
    jacket = Part(s, 'jacket')
    for y in range(0, 12):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if face == 'front' and 2 <= fx <= 5:
                continue
            tone = cloak['hi'] if y == 0 else cloak['base']
            if y >= 2 and x % 3 == 0:
                tone = cloak['lo']  # long vertical folds
            if face == 'front' and fx in (1, 6):
                tone = cloak['hi']  # front edges catch the light
            if y == 11:
                tone = cloak['deep']
            jacket.paint(x, y, tone)
    for arm in ('rsleeve', 'lsleeve'):
        Part(s, arm).fill_rows(0, 3, cloak)
    P.belt(s, Ramp('#a08860'), buckle=hex_rgb('#a08860'), row=8)
    teal = hex_rgb('#3fb8ad')
    for arm in ('rarm', 'larm'):
        p = Part(s, arm)
        p.paint(p.front_x(1), 7, teal)
    P.hair(s, hair, 'crop', fringe='none')
    P.face(s, skin, eyes=hex_rgb('#e0c060'), brows=hair['base'], mouth='neutral', lip=hex_rgb('#2e1a14'))
    P.beard(s, hair, 'short')
    head = Part(s, 'head')
    head.paint(head.front_x(1), 5, teal)
    head.paint(head.front_x(6), 5, teal)
    P.hood_up(s, cloak, face_shadow=shade(skin['base'], -0.1))
    return s


CAST = [
    ('bram', bram), ('hilde', hilde), ('wren', wren), ('tobias', tobias), ('cass', cass), ('marlo', marlo),
    ('isolde', isolde), ('aldric', aldric), ('pip', pip), ('tilly', tilly), ('sable', sable), ('ashe', ashe),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    preview_dir = sys.argv[1] if len(sys.argv) > 1 else None
    for name, make in CAST:
        skin = make()
        skin.save(os.path.join(OUT, name + '.png'))
        if preview_dir:
            os.makedirs(preview_dir, exist_ok=True)
            img, w, h = render_preview(skin)
            save_rgba(img, w, h, os.path.join(preview_dir, name + '-flat.png'), scale=8)
        print('wrote', name, 'slim' if skin.slim else 'wide')


if __name__ == '__main__':
    main()
