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


def freya():
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


def cass():
    """Rogue. High dark ponytail, burgundy laced corset, cropped black leather jacket, thigh-high boots."""
    s = Skin(slim=True)
    skin, hair = Ramp('#e2b58f'), Ramp('#2a2430')
    P.body_skin(s, skin)
    P.shirt(s, Ramp('#3e3c46'), arms_to=9)
    Q.corset(s, Ramp('#7a2a3a'), rows=(3, 9), lace=hex_rgb('#2a2026'))
    body = Part(s, 'body')
    for x in range(body.wrap_width):
        body.paint(x, 10, hex_rgb('#b0303a'))  # crimson sash
    body.paint(body.front_x(5), 11, hex_rgb('#b0303a'))
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#2e2a34'))
    Q.fitted_boots(s, Ramp('#1e1b22'), top_row=3, buckles=hex_rgb('#b8b8c0'))
    jacket_ramp = Ramp('#2c2a32')
    jacket = Part(s, 'jacket')
    for y in range(0, 5):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if face == 'front' and 2 <= fx <= 5 and y > 0:
                continue
            jacket.paint(x, y, jacket_ramp['lo'] if y == 4 else (jacket_ramp['hi'] if face == 'front' and fx in (1, 6) else jacket_ramp['base']))
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        p.fill_rows(0, 9, jacket_ramp)
    P.gloves(s, Ramp('#3b2a22'), rows=(10, 11), fingertips_skin=skin['base'])
    for fx in (2, 3, 4, 5):
        jacket.paint(jacket.front_x(fx), 0, hex_rgb('#b0303a'))  # scarf at the throat
    Q.flowing_hair(s, hair, length=1, fringe='swept', shoulder_locks=False)
    Q.ponytail(s, hair, length=8)
    hat = Part(s, 'hat')
    hat.paint(hat.front_x(1), 2, hex_rgb('#4a3a52'))  # a sheen in the dark hair
    Q.detailed_face(s, skin, eyes=hex_rgb('#9a6a3a'), lash=hex_rgb('#1a1418'), lip=hex_rgb('#b85c6a'), blush=False)
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


def rosie():
    """Farmhand (new look). Honey-blonde waves with a flower crown, puff sleeves, sky-blue laced dress, white apron."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f8e0d0'), Ramp('#e6b45e')
    P.body_skin(s, skin)
    blouse = Ramp('#f6f0e4')
    P.shirt(s, blouse, arms_to=3, neck='round')
    Q.puff_sleeves(s, blouse, rows=(0, 3))
    dress = Ramp('#6fa8dc')
    Part(s, 'body').fill_rows(3, 11, dress)
    Q.corset(s, Ramp('#5a90c8'), rows=(4, 8), lace=WHITE)
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 8, Ramp('#efe8de'))
    P.boots(s, Ramp('#8a5a3a'), top_row=9)
    Q.skirt(s, dress, rows=8, front_panel=WHITE, trim=WHITE)
    jacket = Part(s, 'jacket')
    for y in range(9, 12):
        for fx in range(1, 7):
            jacket.paint(jacket.front_x(fx), y, WHITE if y < 11 else shade(WHITE, -0.06))
    P.emblem(s, 'rpants', [(2, 3)], hex_rgb('#f07aa0'))  # embroidered flower on the apron
    P.emblem(s, 'lpants', [(1, 3)], hex_rgb('#f07aa0'))
    Q.flowing_hair(s, hair, length=6, fringe='part')
    Q.flower_crown(s, hex_rgb('#5aa050'), [hex_rgb('#f07aa0'), WHITE, hex_rgb('#ffd75a')])
    Q.detailed_face(s, skin, eyes=hex_rgb('#5aa0b0'), lash=hex_rgb('#8a5a2a'), lip=hex_rgb('#e88a90'), freckles=True)
    return s


def luna():
    """Mystic (new look). Waist-length silver-lavender hair, midnight gown with gold stars, gold circlet."""
    s = Skin(slim=True)
    skin, hair = Ramp('#f2e0dc'), Ramp('#d9d2ec')
    P.body_skin(s, skin)
    gown = Ramp('#26306a')
    P.shirt(s, gown, arms_to=9)
    body = Part(s, 'body')
    for fx, y in ((2, 0), (5, 0), (3, 1), (4, 1)):
        body.paint(body.front_x(fx), y, GOLD)  # gold V neckline
    for x in range(body.wrap_width):
        body.paint(x, 6, GOLD)  # thin gold belt
    P.emblem(s, 'body', [(3, 2), (4, 2), (3, 4), (4, 4)], GOLD)  # amulet setting
    P.emblem(s, 'body', [(3, 3), (4, 3)], hex_rgb('#9a70e8'))      # amethyst
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        for y in range(6, 10):
            for x in range(p.wrap_width):
                p.paint(x, y, GOLD if y == 9 else gown['base'])  # flared sleeves with gold cuffs
    for leg in ('rleg', 'lleg'):
        Part(s, leg).fill_rows(0, 11, Ramp('#1e2246'))
    Q.skirt(s, gown, rows=11, trim=GOLD)
    for leg, stars in (('rpants', ((1, 3), (2, 7), (0, 9))), ('lpants', ((2, 2), (1, 6), (3, 8)))):
        P.emblem(s, leg, list(stars), hex_rgb('#e8e0ff'))
    Q.flowing_hair(s, hair, length=10, fringe='part')
    Q.circlet(s, GOLD, hex_rgb('#5ac8ff'))
    Q.detailed_face(s, skin, eyes=hex_rgb('#a070e0'), lash=hex_rgb('#3a2a5a'), lip=hex_rgb('#c87a9a'))
    return s
