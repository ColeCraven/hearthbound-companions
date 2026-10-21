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


def emmett():
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


def lucas():
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


def conoby():
    """Guardian. Blond older knight: greying wheat-blond hair and beard, polished plate, crimson tabard and cape."""
    s = Skin(slim=False)
    skin, hair = Ramp('#e2b496'), Ramp('#d2b068')
    grey = hex_rgb('#dcd6c6')
    steel = Ramp('#a6adb6')
    crimson = Ramp('#a02a34')
    gold = hex_rgb('#e2b84c')
    P.body_skin(s, skin)
    P.chainmail(s, Ramp('#8d9196'), parts=('body', 'rarm', 'larm', 'rleg', 'lleg'), rows=(0, 11))
    P.plate(s, steel, 'body', rows=(0, 8))
    P.belt(s, Ramp('#5a3a24'), buckle=gold, row=8, layer='body')
    P.gloves(s, steel, rows=(10, 11))
    P.boots(s, steel, top_row=6)
    for leg in ('rpants', 'lpants'):
        P.plate(s, steel, leg, rows=(4, 5), faces=('front', 'right', 'left'))  # knee guards
    P.pauldron(s, steel, side='right', rows=(0, 3))
    P.pauldron(s, steel, side='left', rows=(0, 3))
    P.bracers(s, steel, rows=(6, 9))
    # crimson tabard down the front, edged in gold, with a silver sword
    jacket = Part(s, 'jacket')
    for y in range(2, 12):
        for fx in range(1, 7):
            tone = gold if fx in (1, 6) or y == 2 else crimson['base']
            if y == 11:
                tone = crimson['lo']
            jacket.paint(jacket.front_x(fx), y, tone)
    for fx in (2, 3, 4, 5):
        jacket.paint(jacket.front_x(fx), 4, gold)  # crossguard
    jacket.paint(jacket.front_x(3), 3, gold)
    jacket.paint(jacket.front_x(4), 3, shade(gold, -0.12))
    for y in range(5, 10):
        jacket.paint(jacket.front_x(3), y, steel['hi'])
        jacket.paint(jacket.front_x(4), y, steel['lo'])
    for leg, cols in (('rpants', (1, 2, 3)), ('lpants', (0, 1, 2))):
        p = Part(s, leg)
        for y in range(0, 4):
            for fx in cols:
                p.paint(p.front_x(fx), y, crimson['lo'] if y == 3 else crimson['base'])
    Q.cape(s, crimson, trim=gold)
    P.hair(s, hair, 'short', fringe='none')
    head = Part(s, 'head')
    for x in (head.d - 2, head.d - 1, head.d + head.w, head.d + head.w + 1):
        for y in (2, 3):
            head.paint(x, y, grey)  # greying temples
    for col, row in ((1, 0), (5, 0), (6, 1)):
        head.paint(head.front_x(col), row, grey)  # silver threads in the blond
    P.face(s, skin, eyes=hex_rgb('#5a8ac0'), brows=hair['lo'], mouth='neutral', lip=hex_rgb('#9a6a58'))
    for col in (2, 5):
        head.paint(head.front_x(col), 5, shade(skin['base'], -0.05))  # tired eyes
    P.beard(s, hair, 'full')
    for col, row in ((3, 7), (4, 7), (1, 6), (6, 6)):
        head.paint(head.front_x(col), row, grey)  # grey creeping into the beard
    return s


def samuel():
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


def casper():
    """Mystic. Quiet rune-mage: black hair tied back with a silver streak, long dark-teal coat stitched with glowing runes."""
    s = Skin(slim=False)
    skin, hair = Ramp('#b07a52'), Ramp('#1c1618')
    coat = Ramp('#1f4f58')
    glow = hex_rgb('#5ae0d0')
    silver = hex_rgb('#c8ccd4')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#e8e0cc'), arms_to=9, neck='v', skin=skin)
    P.trousers(s, Ramp('#2e2c30'))
    P.boots(s, Ramp('#4a3426'), top_row=8, cuff=Ramp('#5e4430'))
    W.open_coat(s, coat, rows=(0, 11), open_cols=(3, 4), sleeves_to=9, cuff=silver, tails=6, edge=silver)
    jacket = Part(s, 'jacket')
    for y in (3, 6, 9):
        jacket.paint(jacket.front_x(2), y, glow)  # runes stitched down the coat edges
        jacket.paint(jacket.front_x(5), y + 1, glow)
    for bx, y in ((3, 4), (4, 4), (2, 5), (5, 5), (2, 6), (5, 6), (3, 7), (4, 7)):
        jacket.paint(jacket.back_x(bx), y, shade(glow, -0.15))  # a rune ring on the back
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.paint(p.front_x(1), 8, glow)
    P.hood_down(s, coat)
    P.belt(s, Ramp('#4a3020'), buckle=silver, row=8, layer='body')
    P.emblem(s, 'body', [(3, 1), (4, 1)], hex_rgb('#3a2a20'))  # pendant cord
    P.emblem(s, 'body', [(3, 2)], glow)
    P.hair(s, hair, 'tied', fringe='side')
    head = Part(s, 'head')
    for row in range(0, 3):
        head.paint(head.front_x(6), row, silver)  # a single silver streak
    P.face(s, skin, eyes=hex_rgb('#e8b040'), brows=hair['base'], mouth='neutral', lip=hex_rgb('#6e4232'))
    P.beard(s, hair, 'stubble', skin=skin)
    return s


CAST = [
    ('bram', bram), ('freya', W.freya), ('hazel', W.hazel), ('emmett', emmett), ('cass', W.cass), ('lucas', lucas),
    ('daisy', W.daisy), ('conoby', conoby), ('samuel', samuel), ('rosie', W.rosie), ('luna', W.luna), ('casper', casper),
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
