"""
Tiny pixel-art toolkit for 64x64 Minecraft skins (no third-party libraries).

Body parts are painted as "wraps": the four side faces of a box laid side by side
(right, front, left, back), so belts, hems and stripes run seamlessly around the
model. Coordinates inside a wrap: x in [0, 2*d + 2*w), y in [0, h).
Light comes from the front and above; side faces are a little darker, the back
darker still, top faces lighter.
"""
import colorsys
import struct
import zlib

SIZE = 64


# ---------------------------------------------------------------- colours

def hex_rgb(value):
    value = value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, amount):
    """Lighten (amount > 0) or darken (amount < 0) with a slight hue shift:
    shadows drift cooler and more saturated, highlights warmer - reads better than plain black/white mixing."""
    r, g, b = [c / 255.0 for c in rgb[:3]]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    l = max(0.0, min(1.0, l + amount))
    if amount < 0:
        h = (h + 0.012 * (-amount / 0.1)) % 1.0
        s = min(1.0, s * (1 + 0.15 * (-amount / 0.1)))
    else:
        h = (h - 0.008 * (amount / 0.1)) % 1.0
        s = s * (1 - 0.05 * (amount / 0.1))
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return (round(r * 255), round(g * 255), round(b * 255))


class Ramp:
    """Four-step colour ramp: hi, base, lo, deep."""

    def __init__(self, base):
        self.base = hex_rgb(base) if isinstance(base, str) else base
        self.hi = shade(self.base, 0.08)
        self.lo = shade(self.base, -0.08)
        self.deep = shade(self.base, -0.17)

    def __getitem__(self, key):
        return {'hi': self.hi, 'base': self.base, 'lo': self.lo, 'deep': self.deep}[key]


# ---------------------------------------------------------------- canvas

class Skin:
    def __init__(self, slim):
        self.slim = slim
        self.px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
        self.arm_w = 3 if slim else 4

    def set(self, x, y, rgb, alpha=255):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[y][x] = (rgb[0], rgb[1], rgb[2], alpha)

    def get(self, x, y):
        return self.px[y][x]

    def clear(self, x, y):
        self.px[y][x] = (0, 0, 0, 0)

    def save(self, path):
        raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in self.px)

        def chunk(tag, data):
            return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

        png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


# Box UV origins (u, v) and sizes (w, h, d) for the base and outer layers.
def parts(skin):
    aw = skin.arm_w
    return {
        'head': ((0, 0), (8, 8, 8)), 'hat': ((32, 0), (8, 8, 8)),
        'body': ((16, 16), (8, 12, 4)), 'jacket': ((16, 32), (8, 12, 4)),
        'rarm': ((40, 16), (aw, 12, 4)), 'rsleeve': ((40, 32), (aw, 12, 4)),
        'larm': ((32, 48), (aw, 12, 4)), 'lsleeve': ((48, 48), (aw, 12, 4)),
        'rleg': ((0, 16), (4, 12, 4)), 'rpants': ((0, 32), (4, 12, 4)),
        'lleg': ((16, 48), (4, 12, 4)), 'lpants': ((0, 48), (4, 12, 4)),
    }


class Part:
    """Paint helper for one box (base or outer layer)."""

    SIDE_SHADE = {'right': -0.05, 'front': 0.0, 'left': -0.05, 'back': -0.09}

    def __init__(self, skin, name):
        (self.u, self.v), (self.w, self.h, self.d) = parts(skin)[name]
        self.skin = skin
        self.name = name
        self.outer = name in ('hat', 'jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants')

    @property
    def wrap_width(self):
        return 2 * self.d + 2 * self.w

    def face_of(self, x):
        d, w = self.d, self.w
        if x < d:
            return 'right', x
        if x < d + w:
            return 'front', x - d
        if x < 2 * d + w:
            return 'left', x - d - w
        return 'back', x - 2 * d - w

    def wrap_xy(self, x, y):
        """Texture coordinates for wrap position (x, y)."""
        return self.u + x, self.v + self.d + y

    def paint(self, x, y, rgb, alpha=255, lit=True):
        """Paint a wrap pixel, applying the side shading for that face."""
        face, _ = self.face_of(x % self.wrap_width)
        colour = shade(rgb, self.SIDE_SHADE[face]) if lit else rgb
        tx, ty = self.wrap_xy(x % self.wrap_width, y)
        self.skin.set(tx, ty, colour, alpha)

    def erase(self, x, y):
        tx, ty = self.wrap_xy(x % self.wrap_width, y)
        self.skin.clear(tx, ty)

    def front_x(self, fx):
        """Wrap x for column fx of the front face (0 = viewer's left = the model's right side)."""
        return self.d + fx

    def back_x(self, bx):
        """Wrap x for column bx of the back face, as seen from behind (0 = viewer's left)."""
        return 2 * self.d + self.w + bx

    def fill_rows(self, y0, y1, ramp, alpha=255, shading=True):
        """Fill rows y0..y1 (inclusive) all the way round, with soft vertical shading and darker hems."""
        for y in range(y0, y1 + 1):
            for x in range(self.wrap_width):
                tone = ramp['base']
                if shading and self.h >= 8:
                    if y == y0 and y0 > 0:
                        tone = ramp['lo']
                    elif y == self.h - 1:
                        tone = ramp['lo']
                face, fx = self.face_of(x)
                if shading and face == 'front' and (fx == 0 or fx == self.w - 1) and self.w >= 4:
                    tone = shade(tone, -0.03)
                self.paint(x, y, tone, alpha)

    def top(self, rgb_fn, alpha=255):
        """Paint the top face; rgb_fn(x, y) -> colour or None."""
        for y in range(self.d):
            for x in range(self.w):
                c = rgb_fn(x, y)
                if c is not None:
                    self.skin.set(self.u + self.d + x, self.v + y, shade(c, 0.06), alpha)

    def bottom(self, rgb_fn, alpha=255):
        for y in range(self.d):
            for x in range(self.w):
                c = rgb_fn(x, y)
                if c is not None:
                    self.skin.set(self.u + self.d + self.w + x, self.v + y, shade(c, -0.1), alpha)


# ---------------------------------------------------------------- preview

def render_preview(skin, scale=6, show_outer=True):
    """Front and back views side by side (flat orthographic), for quick review."""
    W, H = 16 + 4 + 16, 32
    img = [[(0, 0, 0, 0)] * W for _ in range(H)]

    def blit_face(part_name, face, ox, oy, mirror=False):
        p = Part(skin, part_name)
        start = {'right': 0, 'front': p.d, 'left': p.d + p.w, 'back': 2 * p.d + p.w}[face]
        width = p.w if face in ('front', 'back') else p.d
        for y in range(p.h):
            for x in range(width):
                tx, ty = p.wrap_xy(start + x, y)
                c = skin.get(tx, ty)
                if c[3] > 0:
                    dx = ox + (width - 1 - x if mirror else x)
                    img[oy + y][dx] = c

    aw = skin.arm_w
    for layer in (('head', 'body', 'rarm', 'larm', 'rleg', 'lleg'),
                  ('hat', 'jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants')) if show_outer else (
            ('head', 'body', 'rarm', 'larm', 'rleg', 'lleg'),):
        head, body, rarm, larm, rleg, lleg = layer
        # front view: model's right side on the viewer's left
        blit_face(head, 'front', 4, 0)
        blit_face(body, 'front', 4, 8)
        blit_face(rarm, 'front', 4 - aw, 8)
        blit_face(larm, 'front', 12, 8)
        blit_face(rleg, 'front', 4, 20)
        blit_face(lleg, 'front', 8, 20)
        # back view
        bx = 20
        blit_face(head, 'back', bx + 4, 0)
        blit_face(body, 'back', bx + 4, 8)
        blit_face(larm, 'back', bx + 4 - aw, 8)
        blit_face(rarm, 'back', bx + 12, 8)
        blit_face(lleg, 'back', bx + 4, 20)
        blit_face(rleg, 'back', bx + 8, 20)
    return img, W, H


def save_rgba(img, w, h, path, scale=8, background=(48, 52, 60)):
    rows = []
    for y in range(h * scale):
        row = bytearray(b'\x00')
        for x in range(w * scale):
            p = img[y // scale][x // scale]
            if p[3] == 0:
                row += bytes(background) + b'\xff'
            else:
                row += bytes(p[:3]) + b'\xff'
        rows.append(bytes(row))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w * scale, h * scale, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)
