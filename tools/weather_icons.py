#!/usr/bin/env python3
"""
Draws the weather icons as vector drawables in app/src/main/res/drawable, each composed of the parts of its name:
d or n (the sun or the moon), c2, c3 or c4 (a few clouds, more, overcast), r, s or rs 1-3 (rain, snow, both,
light to heavy), st (a thunderstorm) and mist; with mist_overlay (mist over another icon) and nodata.

Flat icons in a 64x48 box: a sun with short rays or a moon, white clouds with a darker one behind when overcast,
slanted rain strokes, snowflakes, a lightning bolt between them and four short lines for mist. Each icon comes twice:
ic_weather_<name> for the dark surfaces and the skies, ic_weather_light_<name> in deeper colours that read on a light
surface. The names, dots made underscores, are listed in res/values/weather_icons.xml for WeatherIcons.

    tools/weather_icons.py              draws every icon again
    tools/weather_icons.py d.c2.r1 ...  draws the named ones
"""
import math
import os
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res')
DRAWABLE = os.path.join(RES, 'drawable')
TABLE = os.path.join(RES, 'values', 'weather_icons.xml')

WIDTH = 64
HEIGHT = 48

SUN = '#FFC83D'
MOON = '#F4E3A1'
CLOUD = '#F2F5F9'
CLOUD_BEHIND = '#AEBACB'
RAIN = '#4FB3FF'
SNOW = '#FFFFFF'
MIST = '#DDE3EA'

# Deeper colours of the same parts, for the light surfaces
LIGHT = {
    CLOUD: '#C3CEDB',  # the front cloud
    CLOUD_BEHIND: '#8796AB',  # the cloud behind it
    SUN: '#F2A900',  # the sun and the lightning
    MOON: '#D9B44A',
    RAIN: '#1E88E5',
    SNOW: '#7FB2E0',
    MIST: '#97A3B2',
}

# The radius of the sun in a clear sky, and the moon's there
CLEAR_R = 12.4


def f(v):
    return ('%.2f' % v).rstrip('0').rstrip('.')


# --- geometry ---

def circle_intersections(c1, c2):
    (x1, y1, r1), (x2, y2, r2) = c1, c2
    d = math.hypot(x2 - x1, y2 - y1)
    a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(0, r1 * r1 - a * a))
    xm = x1 + a * (x2 - x1) / d
    ym = y1 + a * (y2 - y1) / d
    p1 = (xm + h * (y2 - y1) / d, ym - h * (x2 - x1) / d)
    p2 = (xm - h * (y2 - y1) / d, ym + h * (x2 - x1) / d)
    return p1, p2


def arc(c, start, end):
    """Clockwise on the screen along circle c from start to end"""
    cx, cy, r = c
    a1 = math.atan2(start[1] - cy, start[0] - cx)
    a2 = math.atan2(end[1] - cy, end[0] - cx)
    delta = (a2 - a1) % (2 * math.pi)
    large = 1 if delta > math.pi else 0
    return 'A%s %s 0 %d 1 %s %s' % (f(r), f(r), large, f(end[0]), f(end[1]))


def cloud_path(x0, yb, w):
    """A cloud of three circles on a flat bottom: from its left x0, its bottom yb, w wide and about 0.67 w high"""
    cs = [(x0 + 0.22 * w, yb - 0.22 * w, 0.22 * w),
          (x0 + 0.50 * w, yb - 0.37 * w, 0.30 * w),
          (x0 + w - 0.26 * w, yb - 0.26 * w, 0.26 * w)]
    # Where each circle meets the next one above
    pts = []
    for i in range(len(cs) - 1):
        p1, p2 = circle_intersections(cs[i], cs[i + 1])
        pts.append(p1 if p1[1] < p2[1] else p2)
    start = (cs[0][0], yb)
    end = (cs[-1][0], yb)
    d = 'M%s %s ' % (f(start[0]), f(start[1]))
    cur = start
    for i, c in enumerate(cs):
        nxt = pts[i] if i < len(pts) else end
        d += arc(c, cur, nxt) + ' '
        cur = nxt
    d += 'Z'
    return d


def moon_path(cx, cy, r, cut=0.62, angle=-40):
    """A crescent: the disc less a disc moved toward its upper right"""
    a = math.radians(angle)
    ox, oy = cx + cut * r * math.cos(a), cy + cut * r * math.sin(a)
    rr = r * 0.88
    p1, p2 = circle_intersections((cx, cy, r), (ox, oy, rr))
    a1 = math.atan2(p1[1] - cy, p1[0] - cx)
    a2 = math.atan2(p2[1] - cy, p2[0] - cx)
    # The outer arc goes round the lit side, away from the cut's centre
    mid_cut = math.atan2(oy - cy, ox - cx)

    def passes(s, e):
        delta = (e - s) % (2 * math.pi)
        m = (mid_cut - s) % (2 * math.pi)
        return m < delta
    s, e = (p1, p2) if not passes(a1, a2) else (p2, p1)
    d = 'M%s %s ' % (f(s[0]), f(s[1])) + arc((cx, cy, r), s, e)
    # Back along the cut circle counter-clockwise
    b1 = math.atan2(e[1] - oy, e[0] - ox)
    b2 = math.atan2(s[1] - oy, s[0] - ox)
    delta = (b1 - b2) % (2 * math.pi)
    large = 1 if delta > math.pi else 0
    d += ' A%s %s 0 %d 0 %s %s Z' % (f(rr), f(rr), large, f(s[0]), f(s[1]))
    return d


# --- shapes: a path filled or stroked with round caps, and a group of them moved and scaled ---

def fill(d, color, alpha=None):
    return {'d': d, 'fill': color, 'alpha': alpha}


def stroke(d, color, width):
    return {'d': d, 'stroke': color, 'width': width}


def group(dx, dy, scale, body):
    return {'group': (dx, dy, scale), 'body': body}


def line(x1, y1, x2, y2):
    return 'M%s %s L%s %s ' % (f(x1), f(y1), f(x2), f(y2))


def circle(cx, cy, r):
    return 'M%s %s A%s %s 0 1 0 %s %s A%s %s 0 1 0 %s %s Z' % (
        f(cx - r), f(cy), f(r), f(r), f(cx + r), f(cy), f(r), f(r), f(cx - r), f(cy))


# --- parts ---

def sun(cx, cy, r):
    rays = ''
    for k in range(8):
        a = math.radians(k * 45)
        rays += line(cx + math.cos(a) * r * 1.35, cy + math.sin(a) * r * 1.35, cx + math.cos(a) * r * 1.7, cy + math.sin(a) * r * 1.7)
    return [stroke(rays, SUN, 2.6), fill(circle(cx, cy, r), SUN)]


def moon(cx, cy, r):
    # Alone in a clear sky a little larger, to stand as tall as the sun with its rays
    return [fill(moon_path(cx, cy, r * 1.12 if r > 12 else r), MOON)]


def cloud(x0, yb, w, behind=False):
    return [fill(cloud_path(x0, yb, w), CLOUD_BEHIND if behind else CLOUD)]


def rain(cx, cy):
    return [stroke(line(cx + 2, cy - 4.2, cx - 2, cy + 4.2), RAIN, 3)]


def snow(cx, cy, r):
    d = ''
    for k in range(3):
        a = math.radians(90 + k * 60)
        dx, dy = r * math.cos(a), r * math.sin(a)
        d += line(cx - dx, cy - dy, cx + dx, cy + dy)
    return [stroke(d, SNOW, 1.8)]


def bolt(x, y, s):
    pts = [(0.55, 0), (0.05, 0.55), (0.42, 0.55), (0.2, 1.0), (0.85, 0.38), (0.48, 0.38), (0.72, 0)]
    return [fill('M' + ' L'.join('%s %s' % (f(x + px * s), f(y + py * s)) for px, py in pts) + ' Z', SUN)]


def mist(x, y, w):
    d = ''
    for k, (dx, ww) in enumerate([(0, 1), (0.14, 0.86), (0.04, 0.82), (0.1, 0.9)]):
        d += line(x + dx * w, y + k * 8, x + (dx + ww) * w, y + k * 8)
    return [stroke(d, MIST, 3.6)]


# --- the icons ---

def parse(name):
    parts = name.split('.')
    p = {'day': 'd' in parts, 'night': 'n' in parts, 'cover': 0, 'kind': None, 'amount': 0,
         'storm': 'st' in parts, 'mist': 'mist' in parts}
    for part in parts:
        if part in ('c2', 'c3', 'c4'):
            p['cover'] = int(part[1])
        elif part[:2] == 'rs' and part[2:].isdigit():
            p['kind'], p['amount'] = 'rs', int(part[2:])
        elif part[:1] in ('r', 's') and part[1:].isdigit():
            p['kind'], p['amount'] = part[0], int(part[1:])
    # Neither the sun nor the moon: overcast
    if not p['day'] and not p['night'] and p['cover'] == 0 and not p['mist']:
        p['cover'] = 4
    return p


def icon(name):
    if name == 'nodata':
        # A faint cloud: nothing known
        return [fill(cloud_path(5, 44, 54), CLOUD_BEHIND, .55)]
    if name == 'mist_overlay':
        # Mist over another icon: a band of it along the bottom
        return [stroke(''.join(line(x1, y, x2, y) for x1, x2, y in [(6, 52, 38), (14, 58, 44.5)]), MIST, 3)]
    p = parse(name)
    # A thunderstorm in a clear sky has a cloud
    if p['storm'] and p['cover'] == 0 and (p['day'] or p['night']):
        p['cover'] = 2
    precip = p['kind'] is not None or p['storm']
    has_sky = p['day'] or p['night']
    scene = []

    def sky(cx, cy, r):
        scene.extend(sun(cx, cy, r) if p['day'] else moon(cx, cy, r * 1.15))

    if p['mist'] and p['cover'] == 0 and not has_sky:
        if p['kind']:
            scene.extend(mist(9, 4, 46))
            add_precipitation(scene, p)
        else:
            scene.extend(mist(9, 12, 46))
        return scene

    if p['cover'] == 0:
        sky(32, 24, CLEAR_R)
        return scene

    if p['cover'] == 2:
        front = (18, 46, 42)
        if has_sky:
            sky(22, 16.5, 9)
    elif p['cover'] == 3:
        front = (6, 46, 52)
        if has_sky:
            sky(19, 15.5, 8.6)
    else:
        front = (3, 46, 50)
        scene.extend(cloud(24, 33, 38, behind=True))
    scene.extend(cloud(*front))
    if not precip:
        return scene
    # The sky and the clouds smaller and higher, the precipitation under them
    body = [group(6.4, -.6, .8, scene)]
    add_precipitation(body, p)
    return body


def add_precipitation(body, p):
    # One to three strokes or flakes, light to heavy; a lightning bolt in the middle with them at its sides
    xs = {1: [32], 2: [24, 40], 3: [18, 32, 46]}
    if p['storm']:
        body.extend(bolt(24, 33, 15))
        xs = {1: [16], 2: [16, 48], 3: [12, 21, 48]}
    if not p['kind']:
        return
    for i, x in enumerate(xs[max(1, p['amount'])]):
        y = 41.8 + (i % 2) * 1.8
        if p['kind'] == 'r' or (p['kind'] == 'rs' and i % 2 == 0):
            body.extend(rain(x, y))
        else:
            body.extend(snow(x, y + 0.5, 3.9 * 1.1))


def names():
    """Every icon the app may ask for; a weather of another name gets nodata's"""
    falls = [kind + str(amount) for kind in ('r', 's', 'rs') for amount in (1, 2, 3)]
    with_falls = lambda base: [base, base + '.st'] + [base + '.' + fall + storm for fall in falls for storm in ('', '.st')]
    out = ['nodata', 'mist', 'mist_overlay', 'd', 'd.st', 'n', 'n.st'] + with_falls('c4')
    for sky in ('d', 'n'):
        for cover in ('c2', 'c3'):
            out += with_falls(sky + '.' + cover)
    out += [kind + str(amount) + storm + '.mist' for kind in ('r', 's') for amount in (1, 2, 3) for storm in ('', '.st')]
    return sorted(out)


def resource(name, light=False):
    return ('ic_weather_light_' if light else 'ic_weather_') + name.replace('.', '_')


def shapes_xml(body, light, indent):
    out = []
    for shape in body:
        if 'group' in shape:
            dx, dy, scale = shape['group']
            out.append('%s<group\n%s    android:scaleX="%s"\n%s    android:scaleY="%s"\n%s    android:translateX="%s"\n'
                       '%s    android:translateY="%s">' % (indent, indent, f(scale), indent, f(scale), indent, f(dx), indent, f(dy)))
            out += shapes_xml(shape['body'], light, indent + '    ')
            out.append('%s</group>' % indent)
            continue
        attrs = ['android:pathData="%s"' % shape['d'].strip()]
        if 'fill' in shape:
            attrs.append('android:fillColor="%s"' % (LIGHT[shape['fill']] if light else shape['fill']))
            if shape['alpha'] is not None:
                attrs.append('android:fillAlpha="%s"' % f(shape['alpha']))
        else:
            attrs.append('android:strokeColor="%s"' % (LIGHT[shape['stroke']] if light else shape['stroke']))
            attrs.append('android:strokeWidth="%s"' % f(shape['width']))
            attrs.append('android:strokeLineCap="round"')
        out.append('%s<path\n%s' % (indent, '\n'.join('%s    %s' % (indent, a) for a in attrs)) + ' />')
    return out


def vector(body, light):
    return '\n'.join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Drawn by tools/weather_icons.py -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%ddp"' % WIDTH,
        '    android:height="%ddp"' % HEIGHT,
        '    android:viewportWidth="%d"' % WIDTH,
        '    android:viewportHeight="%d">' % HEIGHT] + shapes_xml(body, light, '    ') + ['</vector>', ''])


def table(all_names):
    items = lambda light: ''.join('        <item>@drawable/%s</item>\n' % resource(n, light) for n in all_names)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Written by tools/weather_icons.py: the weather icons by their names, for WeatherIcons -->\n'
            '<resources>\n'
            '    <string-array name="weather_icon_names" translatable="false">\n%s    </string-array>\n'
            '    <array name="weather_icons">\n%s    </array>\n'
            '    <array name="weather_icons_light">\n%s    </array>\n'
            '</resources>\n') % (''.join('        <item>%s</item>\n' % n for n in all_names), items(False), items(True))


def main(asked):
    all_names = names()
    for name in asked:
        if name not in all_names:
            sys.exit('no icon is called %s: add it to names()' % name)
    if not asked:
        # The icons of names no longer drawn go
        kept = {resource(n, light) + '.xml' for n in all_names for light in (False, True)}
        for file in os.listdir(DRAWABLE):
            if file.startswith('ic_weather_') and file not in kept:
                os.remove(os.path.join(DRAWABLE, file))
    for name in asked or all_names:
        body = icon(name)
        for light in (False, True):
            with open(os.path.join(DRAWABLE, resource(name, light) + '.xml'), 'w') as out:
                out.write(vector(body, light))
    with open(TABLE, 'w') as out:
        out.write(table(all_names))
    print('%d icons drawn' % len(asked or all_names))


if __name__ == '__main__':
    main(sys.argv[1:])
