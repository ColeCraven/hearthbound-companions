"""
Uploads the cast skins to MineSkin (https://mineskin.org) so Mojang signs and hosts them, then
writes config/hearthbound-skins.json for a server. With signed skins, players who don't have the
mod still see each companion's own outfit, with nothing extra to download.

    python tools/skins/mineskin_upload.py OUT.json [name ...]

Anonymous use (no API key) is rate limited, so skins are uploaded one at a time. Existing entries
in OUT.json are kept, so it can be re-run to fill in anything that failed.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
TEXTURES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hearthbound', 'textures', 'entity', 'companion')
API = 'https://api.mineskin.org'
USER_AGENT = 'HearthboundCompanions/0.1'
# Cast id -> slim (3px) arms, matching CastMember.
CAST = {
    'charlie': False, 'dasha': True, 'hazel': True, 'birintan': False, 'maddie': True, 'aj': False,
    'daisy': True, 'cole': False, 'samuel': False, 'honey': True, 'luna': True, 'luke': False,
}


def request(method, path, body=None, content_type=None):
    headers = {'User-Agent': USER_AGENT, 'Accept': 'application/json'}
    if content_type:
        headers['Content-Type'] = content_type
    req = urllib.request.Request(API + path, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode('utf-8'))
        except ValueError:
            return e.code, {}


def multipart(fields, file_name, file_bytes):
    boundary = uuid.uuid4().hex
    parts = []
    for key, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
                 f'Content-Type: image/png\r\n\r\n'.encode() + file_bytes + b'\r\n')
    parts.append(f'--{boundary}--\r\n'.encode())
    return b''.join(parts), f'multipart/form-data; boundary={boundary}'


def wait_hint(body, default=6.0):
    next_ms = (body.get('rateLimit') or {}).get('next', {}).get('relative')
    return max(default, next_ms / 1000.0 + 0.5) if isinstance(next_ms, (int, float)) else default


def upload(name, slim):
    path = os.path.join(TEXTURES, name + '.png')
    data, ctype = multipart({'variant': 'slim' if slim else 'classic', 'visibility': 'unlisted',
                             'name': ('hearthbound-' + name)[:20]}, name + '.png', open(path, 'rb').read())
    for attempt in range(6):
        status, body = request('POST', '/v2/queue', data, ctype)
        if status == 429:
            time.sleep(wait_hint(body, 15))
            continue
        if status not in (200, 202):
            raise RuntimeError(f'{name}: HTTP {status} {body.get("errors")}')
        skin = body.get('skin')
        job = (body.get('job') or {}).get('id')
        for _ in range(120):
            if skin and skin.get('texture'):
                tex = skin['texture']['data']
                return {'value': tex['value'], 'signature': tex['signature'],
                        'url': skin['texture'].get('url', {}).get('skin', '')}, body
            time.sleep(1.5)
            status, body = request('GET', f'/v2/queue/{job}')
            state = (body.get('job') or {}).get('status')
            if state == 'failed':
                raise RuntimeError(f'{name}: generation failed {body.get("errors")}')
            skin = body.get('skin') if state == 'completed' else None
        raise RuntimeError(f'{name}: timed out waiting for job {job}')
    raise RuntimeError(f'{name}: still rate limited after several tries')


def main():
    out = sys.argv[1]
    wanted = sys.argv[2:] or list(CAST)
    result = json.load(open(out, encoding='utf-8')) if os.path.exists(out) else {}
    for name in wanted:
        if name in result and result[name].get('signature'):
            print('have', name)
            continue
        entry, body = upload(name, CAST[name])
        result[name] = entry
        with open(out, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(result, f, indent=2)
        print('signed', name, entry['url'])
        time.sleep(wait_hint(body, 4))
    print('done:', sum(1 for v in result.values() if v.get('signature')), 'skins')


if __name__ == '__main__':
    main()
