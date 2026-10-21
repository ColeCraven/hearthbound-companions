"""Reusable skin pieces: bodies, faces, hair, garments, gear."""
from skinlib import Part, Ramp, shade


# ---------------------------------------------------------------- body and face

def body_skin(s, skin):
    """Base layer everywhere: bare skin, with the face side lit and the rest a touch darker."""
    for name in ('head', 'body', 'rarm', 'larm', 'rleg', 'lleg'):
        p = Part(s, name)
        for y in range(p.h):
            for x in range(p.wrap_width):
                p.paint(x, y, skin['base'])
        p.top(lambda x, y: skin['base'])
        p.bottom(lambda x, y: skin['lo'])


def face(s, skin, eyes, brows, mouth='neutral', lip=None, freckles=False, scar=False, blush=False, eye_white=(236, 236, 232)):
    head = Part(s, 'head')
    f = head.front_x
    # eyes: white on the outside, iris inside, highlight-free for a clean read at distance
    for col, kind in ((1, 'white'), (2, 'iris'), (5, 'iris'), (6, 'white')):
        head.paint(f(col), 4, eye_white if kind == 'white' else eyes)
    # brows
    for col in (1, 2, 5, 6):
        head.paint(f(col), 3, brows)
    # nose and cheek shading
    head.paint(f(3), 5, skin['lo'])
    head.paint(f(4), 5, skin['lo'])
    head.paint(f(0), 5, shade(skin['base'], -0.02))
    head.paint(f(7), 5, shade(skin['base'], -0.02))
    lip = lip or skin['deep']
    if mouth == 'neutral':
        head.paint(f(3), 6, lip)
        head.paint(f(4), 6, lip)
    elif mouth == 'smile':
        # closed, upturned smile: corners lifted one row
        head.paint(f(3), 6, lip)
        head.paint(f(4), 6, lip)
        head.paint(f(2), 5, shade(lip, 0.06))
        head.paint(f(5), 5, shade(lip, 0.06))
    elif mouth == 'smirk':
        head.paint(f(3), 6, lip)
        head.paint(f(4), 6, lip)
        head.paint(f(5), 5, shade(lip, 0.05))
    elif mouth == 'stern':
        for col in (2, 3, 4, 5):
            head.paint(f(col), 6, lip)
    if freckles:
        for col, row in ((1, 5), (2, 5), (5, 5), (6, 5)):
            head.paint(f(col), row, skin['lo'])
    if blush:
        head.paint(f(1), 5, shade((214, 120, 110), 0.0))
        head.paint(f(6), 5, shade((214, 120, 110), 0.0))
    if scar:
        head.paint(f(5), 2, skin['hi'])
        head.paint(f(6), 3, skin['hi'])
        head.paint(f(6), 5, skin['hi'])
    # ears on the sides
    for x in (3, 4):
        head.paint(x, 4, skin['lo'])
        head.paint(head.d + head.w + x, 4, skin['lo'])


def beard(s, hair, style='full', skin=None):
    """'full', 'short', 'stubble' or 'goatee'. Leaves the mouth visible."""
    head = Part(s, 'head')
    f = head.front_x
    if style == 'stubble':
        colour = shade(hair['base'], 0.15) if skin is None else _mix(skin['base'], hair['base'], 0.35)
        for col in (0, 1, 2, 5, 6, 7):
            head.paint(f(col), 6, colour)
        for col in range(1, 7):
            head.paint(f(col), 7, colour)
        return
    if style == 'goatee':
        for col in (3, 4):
            head.paint(f(col), 7, hair['base'])
        head.paint(f(2), 6, hair['lo'])
        head.paint(f(5), 6, hair['lo'])
        return
    rows = (5, 6, 7) if style == 'full' else (6, 7)
    for row in rows:
        for col in range(8):
            if row == 5 and col in (2, 3, 4, 5):
                continue
            if row == 6 and col in (3, 4):
                continue  # mouth stays
            tone = hair['lo'] if row == 7 or col in (0, 7) else hair['base']
            head.paint(f(col), row, tone)
    # beard continues along the jaw on the sides
    for side_start in (0, head.d + head.w):
        for x in range(head.d):
            if (side_start == 0 and x >= 4) or (side_start != 0 and x <= 3):
                for row in rows:
                    head.paint(side_start + x, row, hair['lo'])


def _mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ---------------------------------------------------------------- hair

def hair(s, ramp, style, fringe='side', highlight_cols=()):
    """Hair on the head and hat layer. Styles: short, crop, long, bob, bun, braids, curly, tied."""
    head = Part(s, 'head')
    hat = Part(s, 'hat')
    f = head.front_x
    W = head.wrap_width
    head.top(lambda x, y: ramp['base'] if (x + y) % 3 else ramp['hi'])
    hat.top(lambda x, y: (ramp['hi'] if (x * 3 + y) % 5 == 0 else ramp['base']) if style in ('curly', 'long', 'bob') else None)

    def band(rows_front, rows_side, rows_back):
        for x in range(W):
            face, fx = head.face_of(x)
            rows = {'front': rows_front, 'right': rows_side, 'left': rows_side, 'back': rows_back}[face]
            for y in range(rows):
                tone = ramp['base'] if (x + y) % 4 else ramp['hi']
                if y == rows - 1:
                    tone = ramp['lo']
                head.paint(x, y, tone)

    if style in ('short', 'crop'):
        band(1 if style == 'crop' else 2, 3 if style == 'crop' else 4, 7 if style == 'short' else 6)
        if style == 'short':
            # sideburns
            head.paint(head.d - 1, 3, ramp['lo'])
            head.paint(head.d + head.w, 3, ramp['lo'])
    elif style == 'curly':
        band(2, 4, 7)
        # tight curls: small highlight clusters on the base layer, a soft rim of volume on the hat layer
        for x in range(W):
            for y in range(0, 7):
                tx, ty = head.wrap_xy(x, y)
                if s.get(tx, ty)[:3] in (ramp['base'], ramp['hi'], ramp['lo']) and (x * 2 + y * 3) % 5 == 0:
                    head.paint(x, y, ramp['hi'])
        for x in range(W):
            face, fx = hat.face_of(x)
            if face == 'front':
                if fx in (0, 7):
                    hat.paint(x, 1, ramp['lo'])
                continue
            hat.paint(x, 0, ramp['base'] if x % 3 else ramp['hi'])
            if face == 'back' or (face in ('right', 'left') and x % 2 == 0):
                hat.paint(x, 1, ramp['lo'])
    elif style == 'bob':
        band(2, 6, 7)
        for x in range(W):
            face, fx = hat.face_of(x)
            if face in ('right', 'left'):
                for y in range(2, 6):
                    hat.paint(x, y, ramp['lo'] if y == 5 else ramp['base'])
            if face == 'back':
                for y in range(0, 7):
                    hat.paint(x, y, ramp['base'] if y < 6 else ramp['lo'])
    elif style in ('long', 'braids', 'tied', 'bun'):
        band(2, 8 if style == 'long' else 5, 8)
        if style == 'long':
            # leave the ears/face edge visible at the front corners of the side faces
            for row in (3, 4, 5):
                head.paint(head.d - 1, row, ramp['deep'])
                head.paint(head.d + head.w, row, ramp['deep'])
            for x in range(W):
                face, fx = hat.face_of(x)
                if face == 'back':
                    for y in range(1, 8):
                        hat.paint(x, y, ramp['base'] if (fx + y) % 3 else ramp['lo'])
        if style == 'bun':
            # bun on the hat layer, top-back of the head
            hat.top(lambda x, y: (ramp['hi'] if x in (3, 4) else ramp['base']) if 2 <= x <= 5 and y >= 5 else None)
            for bx in (2, 3, 4, 5):
                for y in (0, 1):
                    hat.paint(hat.back_x(bx), y, ramp['base'] if y == 0 else ramp['lo'])
        if style == 'tied':
            # low knot at the back
            for bx in (3, 4):
                for y in (5, 6, 7):
                    hat.paint(hat.back_x(bx), y, ramp['lo'] if y == 7 else ramp['base'])
        if style == 'braids':
            # a braid falling down each side of the face
            for x0 in (head.d - 2, head.d + head.w + 1):
                for y in range(3, 8):
                    hat.paint(x0, y, ramp['hi'] if y % 2 else ramp['lo'])
    # fringe on the forehead
    if fringe == 'side':
        for col in range(0, 5):
            head.paint(f(col), 2, ramp['lo'])
    elif fringe == 'centre':
        for col in (0, 1, 6, 7):
            head.paint(f(col), 2, ramp['lo'])
    elif fringe == 'messy':
        for col in (0, 2, 3, 6, 7):
            head.paint(f(col), 2, ramp['lo'])
        hat.paint(hat.front_x(1), 1, ramp['base'])
        hat.paint(hat.front_x(5), 1, ramp['base'])
    for col in highlight_cols:
        for row in range(0, 4):
            head.paint(f(col), row, ramp['hi'])


def hood_up(s, cloth, face_shadow):
    """A deep hood worn up: hat layer frames the face and covers head, sides and back."""
    hat = Part(s, 'hat')
    head = Part(s, 'head')
    hat.top(lambda x, y: cloth['hi'] if y < 3 else cloth['base'])
    for x in range(hat.wrap_width):
        face, fx = hat.face_of(x)
        for y in range(8):
            if face == 'front':
                if y == 0 or fx in (0, 7) or (y == 1 and fx in (1, 6)):
                    hat.paint(x, y, cloth['lo'] if y > 0 else cloth['base'])
            else:
                # smooth cloth: a highlight near the crown, gentle folds lower down
                tone = cloth['hi'] if y == 0 else cloth['base']
                if y >= 3 and x % 4 == 0:
                    tone = cloth['lo']
                if y == 7:
                    tone = cloth['deep']
                hat.paint(x, y, tone)
    # the hood shades the forehead
    for col in range(1, 7):
        head.paint(head.front_x(col), 1, face_shadow)
        head.paint(head.front_x(col), 0, shade(face_shadow, -0.08))


def hood_down(s, cloth):
    """A hood resting on the shoulders and upper back."""
    jacket = Part(s, 'jacket')
    for x in range(jacket.wrap_width):
        face, fx = jacket.face_of(x)
        rows = {'front': 1, 'right': 2, 'left': 2, 'back': 4}[face]
        for y in range(rows):
            if face == 'front' and 2 <= fx <= 5:
                continue
            jacket.paint(x, y, cloth['deep'] if y == rows - 1 else cloth['lo'])
    hat = Part(s, 'hat')
    for bx in range(8):
        hat.paint(hat.back_x(bx), 7, cloth['lo'])


# ---------------------------------------------------------------- garments

def shirt(s, ramp, rows=(0, 11), arms_to=9, neck='round', skin=None):
    """Base-layer top: torso and sleeves down to row arms_to (hands stay skin)."""
    body = Part(s, 'body')
    body.fill_rows(rows[0], rows[1], ramp)
    if neck == 'v' and skin is not None:
        body.paint(body.front_x(3), 0, skin['lo'])
        body.paint(body.front_x(4), 0, skin['lo'])
        body.paint(body.front_x(3), 1, skin['lo'])
        body.paint(body.front_x(4), 1, skin['lo'])
    elif neck == 'open' and skin is not None:
        for row in (0, 1, 2):
            body.paint(body.front_x(3), row, skin['lo'])
            body.paint(body.front_x(4), row, skin['lo'])
    Part(s, 'body').top(lambda x, y: ramp['base'])
    for arm in ('rarm', 'larm'):
        p = Part(s, arm)
        p.fill_rows(0, arms_to, ramp)
        p.top(lambda x, y: ramp['base'])
        # small fold at the elbow
        for x in range(p.wrap_width):
            face, fx = p.face_of(x)
            if face in ('front', 'back') and fx == 0:
                p.paint(x, 5, ramp['lo'])


def rolled_sleeves(s, ramp, roll_row=4):
    """Cuffs rolled up to the elbow: forearms bare below roll_row."""
    for arm in ('rarm', 'larm'):
        p = Part(s, arm)
        for x in range(p.wrap_width):
            p.paint(x, roll_row, ramp['hi'])


def trousers(s, ramp, to_row=8):
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        p.fill_rows(0, to_row, ramp)
        p.top(lambda x, y: ramp['base'])
        for x in range(p.wrap_width):
            face, fx = p.face_of(x)
            if face == 'front' and fx == 1:
                p.paint(x, 4, ramp['lo'])  # knee fold
    body = Part(s, 'body')
    for y in (10, 11):
        for x in range(body.wrap_width):
            body.paint(x, y, ramp['base'] if y == 10 else ramp['lo'])


def boots(s, ramp, top_row=9, cuff=None, sole=None):
    sole = sole or shade(ramp['base'], -0.2)
    for leg in ('rleg', 'lleg'):
        p = Part(s, leg)
        for y in range(top_row, 12):
            for x in range(p.wrap_width):
                tone = ramp['base']
                if y == top_row:
                    tone = ramp['hi']
                if y == 11:
                    tone = sole
                face, fx = p.face_of(x)
                if face == 'front' and y == 10 and fx in (1, 2):
                    tone = ramp['hi']  # toe shine
                p.paint(x, y, tone)
        p.bottom(lambda x, y: sole)
        if cuff is not None:
            q = Part(s, 'rpants' if leg == 'rleg' else 'lpants')
            for x in range(q.wrap_width):
                q.paint(x, top_row, cuff['base'])
                q.paint(x, top_row + 1, cuff['lo'] if (x % 2) else cuff['base'])


def belt(s, ramp, buckle, row=8, height=1, layer='body'):
    p = Part(s, layer)
    for y in range(row, row + height):
        for x in range(p.wrap_width):
            p.paint(x, y, ramp['base'] if y == row else ramp['lo'])
    p.paint(p.front_x(3), row, buckle)
    p.paint(p.front_x(4), row, shade(buckle, -0.15))
    if height > 1:
        p.paint(p.front_x(3), row + 1, shade(buckle, -0.1))
        p.paint(p.front_x(4), row + 1, shade(buckle, -0.2))


def pouch(s, ramp, col, row=9, layer='body'):
    p = Part(s, layer)
    p.paint(p.front_x(col), row, ramp['base'])
    p.paint(p.front_x(col), row + 1, ramp['lo'])


def strap(s, ramp, direction='down_right', layer='jacket', back=True, buckle=None):
    """A diagonal strap across the chest (satchel or bandolier)."""
    p = Part(s, layer)
    for y in range(0, 10):
        fx = round(y * 7 / 9)
        col = fx if direction == 'down_right' else 7 - fx
        p.paint(p.front_x(col), y, ramp['base'])
        if y in (2, 6) and buckle is not None:
            p.paint(p.front_x(col), y, buckle)
        if back:
            bcol = 7 - col
            p.paint(p.back_x(bcol), y, ramp['lo'])
    # over the shoulder on the side faces
    side_x = 1 if direction == 'down_right' else p.d + p.w + 2
    p.paint(side_x, 0, ramp['lo'])


def coat(s, ramp, rows=(0, 11), open_front=True, opening=(3, 4), sleeves_to=8, cuff=None, skirt_rows=0, trim=None):
    """An outer-layer coat or jacket, optionally open at the front, with matching sleeves."""
    jacket = Part(s, 'jacket')
    for y in range(rows[0], rows[1] + 1):
        for x in range(jacket.wrap_width):
            face, fx = jacket.face_of(x)
            if open_front and face == 'front' and fx in opening and y > 0:
                continue
            tone = ramp['base']
            if face == 'front' and (fx == opening[0] - 1 or fx == opening[1] + 1) and open_front:
                tone = trim if trim else ramp['hi']  # lapel / front edge
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
    if skirt_rows:
        for leg in ('rpants', 'lpants'):
            p = Part(s, leg)
            for y in range(skirt_rows):
                for x in range(p.wrap_width):
                    face, fx = p.face_of(x)
                    inner = (leg == 'rpants' and face == 'left') or (leg == 'lpants' and face == 'right')
                    if inner:
                        continue
                    if open_front and face == 'front' and ((leg == 'rpants' and fx == 3) or (leg == 'lpants' and fx == 0)):
                        continue
                    p.paint(x, y, ramp['lo'] if y == skirt_rows - 1 else ramp['base'])


def quilting(s, ramp, layer='jacket', rows=range(1, 10), every=2):
    p = Part(s, layer)
    for y in rows:
        if y % every == 0:
            for x in range(p.wrap_width):
                tx, ty = p.wrap_xy(x, y)
                if s.get(tx, ty)[3] > 0:
                    p.paint(x, y, ramp['lo'])


def chainmail(s, ramp, parts=('body', 'rarm', 'larm'), rows=(0, 9)):
    """Base-layer mail: a checker of light and dark rings."""
    for name in parts:
        p = Part(s, name)
        for y in range(rows[0], rows[1] + 1):
            for x in range(p.wrap_width):
                p.paint(x, y, ramp['hi'] if (x + y) % 2 == 0 else ramp['lo'])


def plate(s, ramp, layer, rows, faces=('front', 'right', 'left', 'back'), rivets=None):
    """Polished metal: highlight band on top, darker underside."""
    p = Part(s, layer)
    r0, r1 = rows
    for y in range(r0, r1 + 1):
        for x in range(p.wrap_width):
            face, fx = p.face_of(x)
            if face not in faces:
                continue
            tone = ramp['base']
            if y == r0:
                tone = ramp['hi']
            elif y == r1:
                tone = ramp['lo']
            if face == 'front' and fx in (1,) and y < r1:
                tone = shade(ramp['hi'], 0.06)  # specular streak
            p.paint(x, y, tone)
    if rivets:
        for (fx, y) in rivets:
            p.paint(p.front_x(fx), y, shade(ramp['base'], 0.15))


def gloves(s, ramp, rows=(10, 11), fingertips_skin=None):
    for arm in ('rarm', 'larm'):
        p = Part(s, arm)
        for y in range(rows[0], rows[1] + 1):
            for x in range(p.wrap_width):
                p.paint(x, y, ramp['base'] if y == rows[0] else ramp['lo'])
        if fingertips_skin is not None:
            for x in range(p.wrap_width):
                p.paint(x, 11, fingertips_skin)
        p.bottom(lambda x, y: ramp['lo'] if fingertips_skin is None else fingertips_skin)


def bracers(s, ramp, rows=(6, 9), layer_suffix='sleeve'):
    for arm in ('r' + layer_suffix, 'l' + layer_suffix):
        p = Part(s, arm)
        for y in range(rows[0], rows[1] + 1):
            for x in range(p.wrap_width):
                tone = ramp['base']
                if y == rows[0]:
                    tone = ramp['hi']
                if y == rows[1]:
                    tone = ramp['lo']
                p.paint(x, y, tone)


def pauldron(s, ramp, side='right', rows=(0, 2)):
    p = Part(s, 'rsleeve' if side == 'right' else 'lsleeve')
    for y in range(rows[0], rows[1] + 1):
        for x in range(p.wrap_width):
            tone = ramp['hi'] if y == rows[0] else ramp['base'] if y < rows[1] else ramp['lo']
            p.paint(x, y, tone)
    p.top(lambda x, y: ramp['hi'])
    face_front = p.front_x(1)
    p.paint(face_front, rows[0] + 1, shade(ramp['base'], 0.18))


def fur_collar(s, ramp):
    jacket = Part(s, 'jacket')
    for x in range(jacket.wrap_width):
        for y in (0, 1):
            tone = ramp['hi'] if (x + y) % 2 == 0 else ramp['base']
            if y == 1 and x % 3 == 0:
                tone = ramp['lo']
            jacket.paint(x, y, tone)
    jacket.top(lambda x, y: ramp['hi'] if (x + y) % 2 else ramp['base'])


def emblem(s, layer, pixels, colour):
    """Pixels are (front column, row) pairs."""
    p = Part(s, layer)
    for fx, y in pixels:
        p.paint(p.front_x(fx), y, colour)
