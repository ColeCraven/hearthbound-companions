"""
The Hearthbound cast: twelve original adventurers, two per personality.
Run `python tools/skins/cast.py` to (re)generate the skin textures and previews.

Every skin is original pixel art drawn by this script; nothing is traced or copied.
"""
import os
import sys

from skinlib import Part, Ramp, Skin, render_preview, save_rgba, shade, hex_rgb
import pieces as P
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
