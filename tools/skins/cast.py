"""
The Hearthbound cast: twelve original adventurers, two per personality.
Run `python tools/skins/cast.py` to (re)generate the skin textures and previews.

Every skin is original pixel art drawn by this script; nothing is traced or copied.
"""
import os
import sys

from skinlib import Part, Ramp, Skin, render_preview, save_rgba, shade, hex_rgb
import pieces as P
import details as Q
import women as W

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hearthbound', 'textures', 'entity', 'companion')


def charlie():
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


def birintan():
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


def aj():
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


def cole():
    """Guardian. Retired rogue-knight: slicked-back blond hair greying at the temples, a devilish goatee and arched brow,
    black leather brigandine with gold rivets, one old steel pauldron, crimson-lined half-cape, rapier at the hip."""
    s = Skin(slim=False)
    skin, hair = Ramp('#ecc2a4'), Ramp('#e0c070')
    grey = hex_rgb('#dcd8cc')
    leather = Ramp('#2c2a30')
    crimson = Ramp('#a8283a')
    steel = Ramp('#aab1ba')
    gold = hex_rgb('#e2b84c')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#efe9de'), arms_to=9, neck='open', skin=skin)
    P.trousers(s, Ramp('#c8b48a'))  # buff riding breeches
    P.boots(s, Ramp('#1e1c22'), top_row=6, cuff=Ramp('#6b4a2e'))
    # fitted brigandine: closed over the chest, gold rivets in rows, the white collar showing at the throat
    jacket = Part(s, 'jacket')
    for y in range(1, 10):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if face == 'front' and fx in (3, 4) and y < 3:
                continue
            tone = leather['base']
            if face == 'front' and fx in (3, 4):
                tone = leather['hi']
            if y == 9:
                tone = leather['lo']
            jacket.paint(x, y, tone)
    for y in (3, 5, 7):
        for fx in (1, 6):
            jacket.paint(jacket.front_x(fx), y, gold)
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.fill_rows(0, 9, leather)
        p.top(lambda x, y: leather['base'])
        for x in range(p.wrap_width):
            p.paint(x, 9, crimson['base'])  # turned-back crimson cuffs
    P.pauldron(s, steel, side='left', rows=(0, 3))
    P.belt(s, Ramp('#5a3a24'), buckle=gold, row=9, layer='jacket')
    # rapier hilt and scabbard at the left hip
    for y, c in ((8, gold), (9, steel['hi']), (10, leather['lo']), (11, leather['lo'])):
        jacket.paint(jacket.front_x(7), y, c)
    # half-cape over the left shoulder, crimson lining showing at the edge
    for y in range(0, 9):
        for bx in range(3, 8):
            jacket.paint(jacket.back_x(bx), y, crimson['lo'] if bx == 3 else (leather['deep'] if y < 8 else crimson['base']))
    left = jacket.d + jacket.w
    for y in range(0, 8):
        for x in range(left, left + jacket.d):
            jacket.paint(x, y, leather['deep'] if x < left + jacket.d - 1 else crimson['base'])
    P.gloves(s, Ramp('#3a2a20'), rows=(10, 11))
    # slicked-back hair: combed lines on the crown, no fringe, grey at the temples
    P.hair(s, hair, 'short', fringe='none')
    head = Part(s, 'head')
    for col in (1, 3, 5):
        head.paint(head.front_x(col), 0, hair['hi'])
    for x in (head.d - 2, head.d - 1, head.d + head.w, head.d + head.w + 1):
        for y in (2, 3):
            head.paint(x, y, grey)
    P.face(s, skin, eyes=hex_rgb('#4f86c8'), brows=hair['lo'], mouth='smirk', lip=hex_rgb('#a86a5a'))
    # one brow arched, a pointed goatee and moustache corners
    head.paint(head.front_x(6), 2, hair['lo'])
    head.paint(head.front_x(6), 3, skin['base'])
    P.beard(s, hair, 'goatee')
    head.paint(head.front_x(3), 7, hair['lo'])
    head.paint(head.front_x(4), 7, grey)
    head.paint(head.front_x(1), 6, shade(skin['base'], -0.04))  # laugh line
    return s


def samuel():
    """Farmhand. Farm lad: dark curls, freckles, cream shirt with rolled sleeves, denim overalls with a patch, red kerchief."""
    s = Skin(slim=False)
    skin, hair = Ramp('#f2cdb0'), Ramp('#3a2416')
    P.body_skin(s, skin)
    shirt = Ramp('#f0e8d6')
    P.shirt(s, shirt, arms_to=4)
    P.rolled_sleeves(s, shirt, roll_row=4)
    overalls = Ramp('#46689a')
    P.trousers(s, overalls, to_row=7)
    # rolled cuffs and socks, then muddy boots
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        for x in range(p.wrap_width):
            p.paint(x, 7, overalls['hi'])
            p.paint(x, 8, hex_rgb('#e2d8c0'))
    P.boots(s, Ramp('#6b4630'), top_row=9)
    for leg, xs in (('rleg', (5, 10)), ('lleg', (2, 13))):
        p = Part(s, leg)
        for x in xs:
            p.paint(x, 10, hex_rgb('#8a6446'))
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
    P.emblem(s, 'jacket', [(1, 4), (6, 4)], hex_rgb('#d8c060'))  # brass buckles
    P.emblem(s, 'jacket', [(4, 7), (5, 7), (4, 8), (5, 8)], hex_rgb('#7a9a5a'))  # patch
    P.emblem(s, 'body', [(2, 0), (3, 0), (4, 0), (5, 0), (3, 1), (4, 1)], hex_rgb('#c8463a'))  # kerchief
    P.hair(s, hair, 'curly', fringe='messy')
    P.face(s, skin, eyes=hex_rgb('#5a7a3a'), brows=hair['base'], mouth='smile', lip=hex_rgb('#c07a6a'), freckles=True)
    return s


def luke():
    """Mystic. Young sorcerer: black hair tied back with a silver streak, emerald robe trimmed in gold,
    brown leather mantle and belt with a spellbook, a glowing amber charm."""
    s = Skin(slim=False)
    skin, hair = Ramp('#f0ccb0'), Ramp('#1e1a1e')
    robe = Ramp('#1f6a4a')
    gold = hex_rgb('#e2b84c')
    leather = Ramp('#6b4a30')
    glow = hex_rgb('#ffc85a')
    silver = hex_rgb('#c8ccd4')
    P.body_skin(s, skin)
    P.shirt(s, robe, arms_to=9)
    P.trousers(s, Ramp('#2e2a2a'))
    P.boots(s, Ramp('#4a3426'), top_row=8, cuff=Ramp('#5e4430'))
    body = Part(s, 'body')
    for y in range(0, 12):
        body.paint(body.front_x(3), y, robe['lo'])  # front closure with gold buttons
        body.paint(body.front_x(4), y, robe['deep'])
    for y in (4, 6, 9):
        body.paint(body.front_x(3), y, gold)
    for fx, y in ((3, 0), (4, 0), (3, 1)):
        body.paint(body.front_x(fx), y, hex_rgb('#e8e0cc'))  # undershirt at the collar
    for arm in ('rarm', 'larm'):
        p = Part(s, arm)
        for x in range(p.wrap_width):
            p.paint(x, 9, gold)  # gold cuffs
    # robe skirt to the knee, split at the front
    for leg in ('rpants', 'lpants'):
        p = Part(s, leg)
        for y in range(0, 5):
            for x in range(p.wrap_width):
                face, fx = p.face_of(x)
                inner = (leg == 'rpants' and face == 'left') or (leg == 'lpants' and face == 'right')
                if inner:
                    continue
                if face == 'front' and ((leg == 'rpants' and fx == 3) or (leg == 'lpants' and fx == 0)):
                    p.paint(x, y, gold if y == 4 else robe['lo'])
                    continue
                p.paint(x, y, gold if y == 4 else robe['base'])
    # short leather mantle over the shoulders
    jacket = Part(s, 'jacket')
    for x in range(jacket.wrap_width):
        face, fx = jacket.face_of(x)
        rows = {'front': 2, 'right': 3, 'left': 3, 'back': 4}[face]
        for y in range(rows):
            if face == 'front' and fx in (3, 4):
                continue
            jacket.paint(x, y, leather['lo'] if y == rows - 1 else leather['base'])
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.fill_rows(0, 1, leather)
        p.top(lambda x, y: leather['base'])
    P.belt(s, leather, buckle=gold, row=7, layer='jacket')
    P.emblem(s, 'jacket', [(6, 8), (6, 9)], hex_rgb('#7a2a2a'))  # spellbook on the hip
    P.emblem(s, 'jacket', [(7, 8)], hex_rgb('#e8dcc0'))
    P.emblem(s, 'jacket', [(3, 2), (4, 2)], hex_rgb('#3a2a20'))  # charm cord
    P.emblem(s, 'jacket', [(3, 3)], glow)
    P.hair(s, hair, 'tied', fringe='side')
    head = Part(s, 'head')
    for row in range(0, 3):
        head.paint(head.front_x(6), row, silver)  # a single silver streak
    P.face(s, skin, eyes=hex_rgb('#c08a30'), brows=hair['base'], mouth='neutral', lip=hex_rgb('#b47868'))
    return s


CAST = [
    ('charlie', charlie), ('dasha', W.dasha), ('hazel', W.hazel), ('birintan', birintan), ('maddie', W.maddie), ('aj', aj),
    ('daisy', W.daisy), ('cole', cole), ('samuel', samuel), ('honey', W.honey), ('luna', W.luna), ('luke', luke),
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
