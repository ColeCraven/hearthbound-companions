"""The six women of the cast: softer faces, long hair, fitted and elegant outfits."""
from skinlib import Part, Ramp, Skin, shade, hex_rgb
import pieces as P
import details as Q

GOLD = hex_rgb('#e2b84c')
SILVER = hex_rgb('#d8dde4')
WHITE = (246, 242, 236)


def open_coat(s, ramp, rows=(0, 10), open_cols=(2, 3, 4, 5), sleeves_to=8, cuff=None, tails=5, edge=None):
    """A fitted coat open over the chest, with sleeves and tails behind the legs."""
    jacket = Part(s, 'jacket')
    for y in range(rows[0], rows[1] + 1):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if face == 'front' and fx in open_cols and y > 0:
                continue
            tone = ramp['base']
            if face == 'front' and (fx == min(open_cols) - 1 or fx == max(open_cols) + 1):
                tone = edge if edge else ramp['hi']
            if y == rows[1]:
                tone = ramp['lo']
            jacket.paint(x, y, tone)
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.fill_rows(0, sleeves_to, ramp)
        p.top(lambda x, y: ramp['base'])
        if cuff is not None:
            for x in range(p.wrap_width):
                p.paint(x, sleeves_to, cuff)
    for leg in ('rpants', 'lpants'):
        p = Part(s, leg)
        for y in range(tails):
            for x in range(p.wrap_width):
                face, fx = p.face_of(x)
                outer = (leg == 'rpants' and face == 'right') or (leg == 'lpants' and face == 'left')
                if face == 'back' or outer:
                    p.paint(x, y, ramp['lo'] if y == tails - 1 else ramp['base'])


def dasha():
    """Sellsword. Golden-haired shieldmaiden: teal tunic with silver trim, laced leather corset, fur mantle."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f4dccb'), Ramp('#ecc87a')
    P.body_skin(s, skin)
    tunic = Ramp('#2f6a86')
    P.shirt(s, tunic, arms_to=9, neck='v', skin=skin)
    body = Part(s, 'body')
    for x in range(body.wrap_width):
        body.paint(x, 11, SILVER)  # embroidered hem
    Q.corset(s, Ramp('#7a5236'), rows=(5, 9), lace=hex_rgb('#e0d0a8'))
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#5a5560'))
    Q.fitted_boots(s, Ramp('#6b4a30'), top_row=5, cuff=Ramp('#ebe5da'))
    Q.skirt(s, tunic, rows=4, trim=SILVER)
    P.fur_collar(s, Ramp('#ebe5da'))
    jacket = Part(s, 'jacket')
    for fx in (2, 3, 4, 5):
        for y in (0, 1):
            jacket.erase(jacket.front_x(fx), y)  # mantle rests on the shoulders, leaving the neckline open
    P.bracers(s, Ramp('#7a5236'), rows=(6, 9))
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.paint(p.front_x(1), 7, SILVER)  # silver studs
    Q.flowing_hair(s, hair, length=7, fringe='part')
    # two thin braids framing the face
    hat = Part(s, 'hat')
    for col in (0, 7):
        for y in range(2, 8):
            hat.paint(hat.front_x(col), y, hair['hi'] if y % 2 else hair['lo'])
    Q.detailed_face(s, skin, eyes=hex_rgb('#5f9fd8'), lash=hex_rgb('#5a3e2a'), lip=hex_rgb('#d98888'))
    return s


def hazel():
    """Naturalist. Auburn-haired ranger: sage coat over a cream blouse and laced corset, a white flower in her hair."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f6dcc8'), Ramp('#b35a30')
    P.body_skin(s, skin)
    blouse = Ramp('#efe7d4')
    P.shirt(s, blouse, arms_to=9, neck='v', skin=skin)
    Q.corset(s, Ramp('#7a5236'), rows=(6, 9), lace=hex_rgb('#e8dcc0'))
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#9a8060'))
    Q.fitted_boots(s, Ramp('#5a3d26'), top_row=6, cuff=Ramp('#7a5636'))
    open_coat(s, Ramp('#6f8f5a'), rows=(0, 10), sleeves_to=8, cuff=hex_rgb('#56703f'), tails=5)
    P.strap(s, Ramp('#6b4a2e'), direction='down_right', buckle=GOLD)
    Q.flowing_hair(s, hair, length=8, fringe='swept')
    hat = Part(s, 'hat')
    left = hat.d + hat.w
    for x, y, c in ((left + 1, 2, WHITE), (left + 2, 2, WHITE), (left + 1, 3, WHITE), (left + 2, 3, hex_rgb('#f2d060'))):
        hat.paint(x, y, c)  # white flower above the left ear
    Q.detailed_face(s, skin, eyes=hex_rgb('#5aa060'), lash=hex_rgb('#5a2e1c'), lip=hex_rgb('#d98078'), freckles=True)
    return s


def maddie():
    """Rogue. Treasure hunter: dark waves under a crimson bandana, cream blouse, brown leather vest, crimson sash, tall boots."""
    s = Skin(slim=True)
    skin, hair = Ramp('#e8b892'), Ramp('#3a2620')
    crimson = Ramp('#b02a36')
    leather = Ramp('#7a4a2e')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#efe6d2'), arms_to=9, neck='v', skin=skin)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#b49470'))
    Q.fitted_boots(s, Ramp('#5a3a26'), top_row=4, cuff=Ramp('#7a5236'))
    # leather vest, open over the blouse
    jacket = Part(s, 'jacket')
    for y in range(0, 9):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if face == 'front' and 2 <= fx <= 5:
                continue
            tone = leather['base']
            if face == 'front' and fx in (1, 6):
                tone = leather['hi']
            if y == 8:
                tone = leather['lo']
            jacket.paint(x, y, tone)
    for fx in (1, 6):
        jacket.paint(jacket.front_x(fx), 3, GOLD)  # brass toggles
        jacket.paint(jacket.front_x(fx), 6, GOLD)
    # crimson sash tied at the hip, tails hanging
    body = Part(s, 'body')
    for y in (8, 9):
        for x in range(body.wrap_width):
            body.paint(x, y, crimson['base'] if y == 8 else crimson['lo'])
    for y in (9, 10, 11):
        jacket.paint(jacket.front_x(6), y, crimson['base'] if y < 11 else crimson['lo'])
    # dagger at the right hip
    jacket.paint(jacket.front_x(0), 9, hex_rgb('#c8ccd2'))
    jacket.paint(jacket.front_x(0), 10, hex_rgb('#9aa0a8'))
    P.bracers(s, leather, rows=(7, 9))
    P.gloves(s, Ramp('#4a3020'), rows=(10, 11), fingertips_skin=skin['base'])
    Q.flowing_hair(s, hair, length=4, fringe='swept')
    # bandana: a band across the brow, knotted at the back with two tails
    hat = Part(s, 'hat')
    for x in range(hat.wrap_width):
        hat.paint(x, 1, crimson['base'] if x % 4 else crimson['hi'])
    for bx, y in ((5, 2), (5, 3), (6, 3), (6, 4)):
        hat.paint(hat.back_x(bx), y, crimson['lo'])
    hat.paint(hat.front_x(1), 2, hex_rgb('#5a3a30'))  # a sheen in the dark hair
    Q.detailed_face(s, skin, eyes=hex_rgb('#4a9a6a'), lash=hex_rgb('#1e1410'), lip=hex_rgb('#c4646a'))
    return s


def daisy():
    """Guardian. Paladin: silver breastplate with gold trim, white tabard, royal-blue cape, braid and gold circlet."""
    s = Skin(slim=True)
    skin, hair = Ramp('#7c4b33'), Ramp('#2e1c14')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#2f4c86'), arms_to=9)
    steel = Ramp('#c4cad2')
    P.plate(s, steel, 'body', rows=(0, 6))
    body = Part(s, 'body')
    for x in range(body.wrap_width):
        body.paint(x, 6, GOLD)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#2a3656'))
    Q.fitted_boots(s, steel, top_row=7)
    Q.cape(s, Ramp('#2a4a9a'), trim=GOLD)
    jacket = Part(s, 'jacket')
    for y in range(5, 12):
        for fx in range(1, 7):
            tone = WHITE if fx not in (1, 6) else GOLD
            if y == 11:
                tone = shade(WHITE, -0.06)
            jacket.paint(jacket.front_x(fx), y, tone)
    P.emblem(s, 'jacket', [(3, 7), (4, 7), (3, 8), (4, 8), (2, 6), (5, 6), (2, 9), (5, 9)], GOLD)  # sun disc and rays
    Q.skirt(s, Ramp('#ece8e2'), rows=6, trim=GOLD)
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        for x in range(p.wrap_width):
            p.paint(x, 0, steel['hi'])
            p.paint(x, 1, steel['base'])
    P.gloves(s, steel, rows=(10, 11))
    Q.flowing_hair(s, hair, length=3, fringe='part', shoulder_locks=False)
    Q.braid_over_shoulder(s, hair, side='right', length=7)
    Q.circlet(s, GOLD, hex_rgb('#4a8ae0'))
    Q.detailed_face(s, skin, eyes=hex_rgb('#c08a3a'), lash=hex_rgb('#1a100a'), lip=hex_rgb('#a65a52'))
    return s


def honey():
    """Farmhand. Honey-blonde waves and a flower crown; puff-sleeved blouse, sky-blue laced bodice, short skirt over leggings, satchel."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f8e0d0'), Ramp('#e6b45e')
    P.body_skin(s, skin)
    blouse = Ramp('#f6f0e4')
    P.shirt(s, blouse, arms_to=5, neck='round')
    P.rolled_sleeves(s, blouse, roll_row=5)
    Q.puff_sleeves(s, blouse, rows=(0, 2))
    sky = Ramp('#6fa8dc')
    Q.corset(s, sky, rows=(3, 9), lace=WHITE)
    P.belt(s, Ramp('#7a5236'), buckle=GOLD, row=9, layer='body')
    P.pouch(s, Ramp('#8a5e3c'), col=6, row=10)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#7a5a42'))
    Q.fitted_boots(s, Ramp('#8a5a3a'), top_row=6, cuff=Ramp('#d8c8a8'))
    Q.skirt(s, sky, rows=4, trim=WHITE)
    P.strap(s, Ramp('#8a5e3c'), direction='down_right', buckle=GOLD)
    P.emblem(s, 'jacket', [(6, 9)], hex_rgb('#f07aa0'))  # a flower tucked into the satchel strap
    Q.flowing_hair(s, hair, length=6, fringe='part')
    Q.flower_crown(s, hex_rgb('#5aa050'), [hex_rgb('#f07aa0'), WHITE, hex_rgb('#ffd75a')])
    Q.detailed_face(s, skin, eyes=hex_rgb('#5aa0b0'), lash=hex_rgb('#8a5a2a'), lip=hex_rgb('#e88a90'), freckles=True)
    return s


def luna():
    """Mystic. Silver-lavender hair and a gold circlet; midnight tunic over leggings, tall boots, violet star-stitched cape."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f2e0dc'), Ramp('#d9d2ec')
    P.body_skin(s, skin)
    tunic = Ramp('#26306a')
    P.shirt(s, tunic, arms_to=9)
    body = Part(s, 'body')
    for fx, y in ((2, 0), (5, 0), (3, 1), (4, 1)):
        body.paint(body.front_x(fx), y, GOLD)  # gold V neckline
    P.emblem(s, 'body', [(3, 2), (4, 2), (3, 4), (4, 4)], GOLD)  # amulet setting
    P.emblem(s, 'body', [(3, 3), (4, 3)], hex_rgb('#9a70e8'))      # amethyst
    P.belt(s, Ramp('#5a3e2c'), buckle=GOLD, row=7, layer='body')
    P.pouch(s, Ramp('#6a4a34'), col=1, row=8)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#2e2a3e'))
    Q.fitted_boots(s, Ramp('#5a4038'), top_row=5, buckles=GOLD)
    Q.skirt(s, tunic, rows=4, trim=GOLD)
    P.bracers(s, Ramp('#6a4a34'), rows=(6, 9))
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.paint(p.front_x(1), 7, GOLD)
    cape = Ramp('#4a3486')
    Q.cape(s, cape, trim=GOLD)
    jacket = Part(s, 'jacket')
    for bx, y in ((1, 9), (6, 8), (2, 6)):
        jacket.paint(jacket.back_x(bx), y, hex_rgb('#f0e6b0'))  # stitched stars
    for leg, bx, y in (('rpants', 1, 2), ('lpants', 2, 3)):
        p = Part(s, leg)
        p.paint(p.back_x(bx), y, hex_rgb('#f0e6b0'))
    Q.flowing_hair(s, hair, length=7, fringe='part')
    Q.circlet(s, GOLD, hex_rgb('#5ac8ff'))
    Q.detailed_face(s, skin, eyes=hex_rgb('#a070e0'), lash=hex_rgb('#3a2a5a'), lip=hex_rgb('#c87a9a'))
    return s
