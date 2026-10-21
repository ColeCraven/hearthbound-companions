"""Finer pieces: faces with lashes and blush, long flowing hair, braids, ponytails,
crowns and circlets, corsets, puff sleeves, capes, skirts and tall boots."""
from skinlib import Part, shade


def _blend(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def detailed_face(s, skin, eyes, lash, lip, blush=True, freckles=False, shine=(250, 250, 250)):
    """Tall eyes with lashes and a catch-light, soft blush, small rosy lips, a slimmer jaw."""
    head = Part(s, 'head')
    f = head.front_x
    iris_lo = shade(eyes, -0.12)
    # left eye (viewer's left): lash over the white, darker iris top over a lit iris
    head.paint(f(1), 3, lash)
    head.paint(f(2), 3, iris_lo)
    head.paint(f(1), 4, shine)
    head.paint(f(2), 4, eyes)
    # right eye, mirrored
    head.paint(f(5), 3, iris_lo)
    head.paint(f(6), 3, lash)
    head.paint(f(5), 4, eyes)
    head.paint(f(6), 4, shine)
    # a whisper of a nose
    head.paint(f(3), 5, shade(skin['base'], -0.025))
    head.paint(f(4), 5, shade(skin['base'], -0.04))
    if blush:
        pink = _blend(skin['base'], (232, 120, 130), 0.38)
        head.paint(f(1), 5, pink)
        head.paint(f(6), 5, pink)
    if freckles:
        head.paint(f(2), 5, shade(skin['base'], -0.07))
        head.paint(f(5), 5, shade(skin['base'], -0.07))
    head.paint(f(3), 6, lip)
    head.paint(f(4), 6, shade(lip, 0.08))
    # soft jaw contour
    for col in (0, 7):
        head.paint(f(col), 6, shade(skin['base'], -0.03))
        head.paint(f(col), 7, shade(skin['base'], -0.06))


def flowing_hair(s, ramp, length=8, locks=True, fringe='part', shoulder_locks=True):
    """Long hair that frames the face and falls down the back (length = body-back rows covered)."""
    head = Part(s, 'head')
    hat = Part(s, 'hat')
    jacket = Part(s, 'jacket')
    f = head.front_x
    W = head.wrap_width

    def strand(x, y):
        if y == 1:
            return ramp['hi']  # shine band near the crown
        return ramp['lo'] if x % 3 == 0 else ramp['base']

    head.top(lambda x, y: ramp['hi'] if y < 2 else ramp['base'])
    hat.top(lambda x, y: ramp['hi'] if (x + y) % 4 == 0 else ramp['base'])
    for x in range(W):
        face, fx = head.face_of(x)
        for y in range(8):
            if face == 'front':
                if y <= 1:
                    head.paint(x, y, ramp['base'] if y == 0 else ramp['lo'])
            else:
                head.paint(x, y, strand(x, y))
    if fringe == 'part':
        for col in (0, 1, 6, 7):
            head.paint(f(col), 2, ramp['lo'])
        head.paint(f(2), 2, ramp['deep'])
        head.paint(f(5), 2, ramp['deep'])
    elif fringe == 'swept':
        for col in range(0, 5):
            head.paint(f(col), 2, ramp['lo'] if col < 4 else ramp['deep'])
        head.paint(f(0), 3, ramp['lo'])
    elif fringe == 'bangs':
        for col in range(8):
            head.paint(f(col), 2, ramp['lo'])
    for x in range(W):
        face, fx = hat.face_of(x)
        for y in range(8):
            if face == 'front':
                if y == 0 or (locks and fx in (0, 7) and y >= 1):
                    hat.paint(x, y, ramp['base'] if y < 7 else ramp['lo'])
            elif face in ('right', 'left'):
                hat.paint(x, y, strand(x + 1, y))
            else:
                hat.paint(x, y, strand(x, y))
    for y in range(0, length):
        for bx in range(8):
            if y == length - 1 and bx in (0, 7):
                continue  # rounded ends
            tone = ramp['lo'] if bx % 3 == 0 else (ramp['base'] if y < length - 1 else ramp['lo'])
            jacket.paint(jacket.back_x(bx), y, tone)
    for y in range(0, 3):
        jacket.paint(2, y, ramp['lo'])
        jacket.paint(3, y, ramp['lo'])
        jacket.paint(jacket.d + jacket.w, y, ramp['lo'])
        jacket.paint(jacket.d + jacket.w + 1, y, ramp['lo'])
    if shoulder_locks:
        for y in range(0, 4):
            jacket.paint(jacket.front_x(0), y, ramp['base'] if y < 3 else ramp['lo'])
            jacket.paint(jacket.front_x(7), y, ramp['base'] if y < 3 else ramp['lo'])


def braid_over_shoulder(s, ramp, side='right', length=7):
    """One thick braid falling forward over a shoulder."""
    jacket = Part(s, 'jacket')
    col = 1 if side == 'right' else 6
    step = 1 if side == 'right' else -1
    for y in range(0, length):
        jacket.paint(jacket.front_x(col), y, ramp['hi'] if y % 2 == 0 else ramp['lo'])
        jacket.paint(jacket.front_x(col + step), y, ramp['base'] if y % 2 == 0 else ramp['hi'])
    jacket.paint(jacket.front_x(col), length, shade(ramp['base'], -0.2))


def ponytail(s, ramp, length=6):
    """A high ponytail tied at the crown, falling down the back."""
    hat = Part(s, 'hat')
    jacket = Part(s, 'jacket')
    for y in range(0, 8):
        for bx in (3, 4):
            hat.paint(hat.back_x(bx), y, ramp['hi'] if (bx + y) % 3 == 0 else ramp['base'])
    hat.paint(hat.back_x(3), 1, shade(ramp['base'], -0.25))
    hat.paint(hat.back_x(4), 1, shade(ramp['base'], -0.25))
    for y in range(0, length):
        for bx in (3, 4):
            jacket.paint(jacket.back_x(bx), y, ramp['lo'] if (bx + y) % 2 else ramp['base'])


def circlet(s, metal, gem):
    hat = Part(s, 'hat')
    for col in range(1, 7):
        hat.paint(hat.front_x(col), 1, metal if col not in (3, 4) else gem)
    for x in range(hat.wrap_width):
        face, fx = hat.face_of(x)
        if face in ('right', 'left'):
            hat.paint(x, 1, shade(metal, -0.08))


def flower_crown(s, leaf, flowers):
    hat = Part(s, 'hat')
    for x in range(hat.wrap_width):
        colour = flowers[(x // 2) % len(flowers)] if x % 2 == 0 else leaf
        hat.paint(x, 1, colour)
        if x % 4 == 0:
            hat.paint(x, 0, leaf)


def corset(s, ramp, rows=(5, 9), lace=None, layer='body'):
    """A fitted, laced bodice band that cinches the waist."""
    p = Part(s, layer)
    for y in range(rows[0], rows[1] + 1):
        for x in range(p.wrap_width):
            face, fx = p.face_of(x)
            tone = ramp['base']
            if y == rows[0]:
                tone = ramp['hi']
            elif y == rows[1]:
                tone = ramp['lo']
            if face in ('right', 'left'):
                tone = shade(tone, -0.04)
            p.paint(x, y, tone)
        if lace is not None and rows[0] < y < rows[1]:
            p.paint(p.front_x(3 + (y % 2)), y, lace)


def puff_sleeves(s, ramp, rows=(0, 3)):
    for arm in ('rsleeve', 'lsleeve'):
        p = Part(s, arm)
        for y in range(rows[0], rows[1] + 1):
            for x in range(p.wrap_width):
                tone = ramp['hi'] if y == rows[0] else ramp['base']
                if y == rows[1]:
                    tone = ramp['lo']
                elif y != rows[0] and (x + y) % 4 == 0:
                    tone = ramp['lo']  # gathered folds
                p.paint(x, y, tone)
        p.top(lambda x, y: ramp['hi'])


def cape(s, ramp, rows=(0, 11), trim=None):
    """A cape hanging down the back, continuing over the backs of the legs."""
    jacket = Part(s, 'jacket')
    for y in range(rows[0], rows[1] + 1):
        for bx in range(8):
            tone = ramp['hi'] if y == rows[0] else ramp['base']
            if bx in (2, 5) and y > 1:
                tone = ramp['lo']
            if y == rows[1]:
                tone = trim if trim else ramp['lo']
            jacket.paint(jacket.back_x(bx), y, tone)
        jacket.paint(jacket.d + jacket.w + 3, y, ramp['lo'])
        jacket.paint(0, y, ramp['lo'])
    for leg in ('rpants', 'lpants'):
        p = Part(s, leg)
        for y in range(0, 6):
            for bx in range(4):
                tone = ramp['base'] if y < 5 else (trim if trim else ramp['lo'])
                p.paint(p.back_x(bx), y, tone)


def skirt(s, ramp, rows=7, front_panel=None, trim=None):
    """A skirt on the leg outer layer, closed all round so there's no gap between the legs."""
    for leg in ('rpants', 'lpants'):
        p = Part(s, leg)
        for y in range(rows):
            for x in range(p.wrap_width):
                tone = ramp['base'] if (x + y) % 5 else ramp['lo']
                if y == rows - 1:
                    tone = trim if trim else ramp['lo']
                p.paint(x, y, tone)
            if front_panel is not None and y < rows - 1:
                for fx in range(4):
                    if (leg == 'rpants' and fx >= 1) or (leg == 'lpants' and fx <= 2):
                        p.paint(p.front_x(fx), y, front_panel)


def fitted_boots(s, ramp, top_row=4, cuff=None, buckles=None):
    """Tall, slim boots with an optional folded cuff and buckles."""
    sole = shade(ramp['base'], -0.22)
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        for y in range(top_row, 12):
            for x in range(p.wrap_width):
                face, fx = p.face_of(x)
                tone = ramp['base']
                if y == top_row:
                    tone = ramp['hi']
                if y == 11:
                    tone = sole
                if face == 'front' and fx in (1, 2) and y in (6, 7, 9, 10):
                    tone = shade(ramp['hi'], 0.04)
                p.paint(x, y, tone)
        p.bottom(lambda x, y: sole)
        if buckles is not None:
            for y in (top_row + 2, top_row + 5):
                if y < 11:
                    p.paint(p.front_x(2), y, buckles)
        if cuff is not None:
            q = Part(s, 'rpants' if leg == 'rleg' else 'lpants')
            for x in range(q.wrap_width):
                q.paint(x, top_row, cuff['hi'] if x % 2 else cuff['base'])
                q.paint(x, top_row + 1, cuff['base'] if x % 2 else cuff['lo'])
