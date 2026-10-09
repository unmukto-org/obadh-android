#!/usr/bin/env python3
"""Measure installed keyboards on one emulator. Requires Pillow and NumPy, no extra AVD.
Captures are local, ignored build artifacts; Gboard code/artwork is never packaged.
Example: python3 scripts/keyboard-parity.py --keyboard gboard --output build/parity/reference
"""
import argparse
import json
import re
import shlex
import subprocess
import time
from pathlib import Path
import numpy as np
from PIL import Image

IMES = {'gboard': 'com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME',
        'obadh': 'org.unmukto.obadh/.keyboard.ObadhInputMethodService'}
PROFILES = {'phone': (1080, 2340, 440), 'compact-tablet': (1200, 1920, 320), 'tablet': (1800, 2560, 320), 'small-phone': (1080, 2160, 480), 'large-phone': (1440, 3120, 560), 'medium-tablet': (1600, 2560, 320)}


def adb(*args, binary=False):
    return subprocess.check_output(['adb', *args], text=not binary)


def shell(*args):
    return adb('shell', shlex.join(map(str, args)))


def components(mask):
    """Run-length connected components; measure solid fill without font antialiasing noise."""
    parent, boxes, previous = [], [], []
    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for y, row in enumerate(mask):
        edges = np.flatnonzero(np.diff(np.pad(row.astype(np.int8), (1, 1))))
        current = []
        for x0, x1 in zip(edges[::2], edges[1::2]):
            if x1 - x0 < 3:
                continue
            overlaps = [root(i) for a, b, i in previous if a < x1 and b > x0]
            if overlaps:
                i = overlaps[0]
                for j in overlaps[1:]:
                    if j != i:
                        parent[j] = i
            else:
                i = len(parent)
                parent.append(i)
                boxes.append([int(x0), y, int(x1), y + 1])
            b = boxes[i]
            b[0], b[1], b[2], b[3] = min(b[0], int(x0)), min(b[1], y), max(b[2], int(x1)), y + 1
            current.append((x0, x1, i))
        previous = current
    merged = {}
    for i, b in enumerate(boxes):
        r = root(i)
        if r not in merged:
            merged[r] = b.copy()
        else:
            a = merged[r]
            a[:] = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])]
    return list(merged.values())


def measure(path, density):
    pixels = np.asarray(Image.open(path).convert('RGB'))
    h, w = pixels.shape[:2]
    # Landscape keyboards may occupy more than half the display.
    start = int(h * (0.30 if w > h else 0.55))
    crop = pixels[start:]
    colors, counts = np.unique(crop.reshape(-1, 3), axis=0, return_counts=True)
    palette = colors[np.argsort(counts)[-8:][::-1]]
    keys = []
    for color in palette:
        mask = np.all(crop == color, axis=2)
        for x0, y0, x1, y1 in components(mask):
            bw, bh = x1 - x0, y1 - y0
            if w * .025 <= bw <= w * .14 and 14 <= bh <= h * .23 and bw * bh > w * h * .0008:
                keys.append({'box': [x0, y0 + start, x1, y1 + start], 'fill': color.tolist()})
    rows = []
    for key in sorted(keys, key=lambda k: (k['box'][1], k['box'][0])):
        y = key['box'][1]
        if not rows or abs(y - rows[-1]['top']) > 5:
            rows.append({'top': y, 'keys': []})
        rows[-1]['keys'].append(key)
    letter_rows = [r for r in rows if len(r['keys']) >= 7]
    if len(letter_rows) < 3:
        raise RuntimeError(f'{path}: only {len(letter_rows)} key rows; check onboarding/layout/borders before accepting capture')
    scale = density / 160
    q = letter_rows[0]['keys'][0]
    x0, y0, x1, y1 = q['box']
    inset = max(2, int(2 * scale))
    glyph = pixels[y0 + int((y1-y0)*.25):y1-inset, x0+inset:x1-inset].astype(np.int16)
    mask = np.max(np.abs(glyph - np.asarray(q['fill'])), axis=2) > 100
    parts = components(mask)
    main = max(parts, key=lambda b: (b[2]-b[0])*(b[3]-b[1])) if parts else None
    glyph_dp = [round((main[2]-main[0])/scale, 2), round((main[3]-main[1])/scale, 2)] if main else None
    gap_x = (letter_rows[0]['keys'][0]['box'][2] + letter_rows[0]['keys'][1]['box'][0])//2
    gap_y = (y0+y1)//2
    bg = pixels[gap_y, gap_x].tolist()
    ratio = np.mean(np.all(pixels == bg, axis=2), axis=1)
    panel = int(np.flatnonzero(ratio > .7)[0])
    surfaces = {'background': bg, 'keys': q['fill'], 'functional': letter_rows[2]['keys'][0]['fill']}
    return {'panel_top_dp': round(panel/scale, 2), 'keyboard_surfaces_rgb': surfaces, 'q_glyph_dp': glyph_dp,'size_px': [w, h], 'density': density, 'palette_rgb': palette.tolist(), 'rows': rows,
            'letter_rows_dp': [{'top': round(r['top']/scale, 2),
                               'height': round(float(np.median([k['box'][3]-k['box'][1] for k in r['keys']]))/scale, 2),
                               'width': round(float(np.median([k['box'][2]-k['box'][0] for k in r['keys']]))/scale, 2),
                               'count': len(r['keys'])} for r in letter_rows]}


def capture(args):
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    old = {k: shell('settings', 'get', table, name).strip() for k, table, name in
           [('ime', 'secure', 'default_input_method'), ('rotation', 'system', 'user_rotation'),
            ('auto_rotate', 'system', 'accelerometer_rotation'), ('night', 'secure', 'ui_night_mode')]}
    size = re.search(r'Override size: (\d+x\d+)', shell('wm', 'size'))
    density = re.search(r'Override density: (\d+)', shell('wm', 'density'))
    results = []
    try:
        shell('settings', 'put', 'system', 'accelerometer_rotation', 0)
        shell('ime', 'set', IMES[args.keyboard])
        for profile in args.profiles:
            w, h, dpi = PROFILES[profile]
            shell('wm', 'size', f'{w}x{h}')
            shell('wm', 'density', dpi)
            for rotation in (0, 1):
                shell('settings', 'put', 'system', 'user_rotation', rotation)
                for theme in args.themes:
                    shell('cmd', 'uimode', 'night', 'yes' if theme == 'dark' else 'no')
                    shell('am', 'start', '-n', 'org.unmukto.obadh/.debug.NativeKeyboardProbeActivity')
                    time.sleep(.4)
                    shell('uiautomator', 'dump', '/sdcard/obadh-parity.xml')
                    # Real touch input, including for Gboard (never invoke Obadh's typing receiver for it).
                    shell('input', 'tap', min(w, h)//2, 260)
                    if args.keyboard == 'obadh':
                        shell('am', 'broadcast', '-n', 'org.unmukto.obadh/.debug.NativeKeyboardProbeReceiver',
                              '--es', 'command', 'language', '--es', 'language', args.language)
                    shell('uiautomator', 'dump', '/sdcard/obadh-parity.xml')
                    state = shell('dumpsys', 'input_method')
                    if 'mInputShown=true' not in state:
                        raise RuntimeError('IME not shown; cannot accept measurement')
                    stem = f'{profile}-{"landscape" if rotation else "portrait"}-{theme}-{args.language}'
                    path = out / f'{stem}.png'
                    path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
                    controls = None
                    if args.keyboard == 'obadh':
                        shell('am', 'broadcast', '-n', 'org.unmukto.obadh/.debug.NativeKeyboardProbeReceiver', '--es', 'command', 'inspect')
                        states = [line for line in adb('logcat', '-d', '-s', 'ObadhProbeState:I', '-v', 'raw').splitlines() if line.startswith('{')]
                        controls = json.loads(states[-1])['controls']
                        if controls['night'] != (32 if theme == 'dark' else 16):
                            raise RuntimeError('IME theme configuration did not settle')
                    metrics = measure(path, dpi)
                    results.append({'profile': profile, 'rotation': rotation, 'theme': theme,
                                    'keyboard': args.keyboard, 'language': args.language, 'native_controls': controls, **metrics})
                    (out / 'measurements.json').write_text(json.dumps(results, indent=2)+'\n')
                    print(stem, metrics['letter_rows_dp'], flush=True)
    finally:
        shell('wm', 'size', size.group(1) if size else 'reset')
        shell('wm', 'density', density.group(1) if density else 'reset')
        shell('settings', 'put', 'system', 'user_rotation', old['rotation'])
        shell('settings', 'put', 'system', 'accelerometer_rotation', old['auto_rotate'])
        shell('cmd', 'uimode', 'night', {'0': 'auto', '1': 'no', '2': 'yes'}.get(old['night'], 'auto'))
        shell('ime', 'set', old['ime'])
        shell('rm', '-f', '/sdcard/obadh-parity.xml')


def compare(output, reference):
    """Fail on key-box, glyph or shared-palette drift, including split layouts."""
    out, ref = Path(output), Path(reference)
    actual = json.loads((out / 'measurements.json').read_text())
    expected = json.loads((ref / 'measurements.json').read_text())
    references = {(r['profile'], r['rotation'], r['theme']): r for r in expected}
    report = []
    for row in actual:
        key = (row['profile'], row['rotation'], row['theme'])
        baseline = references[key]
        stem = f"{key[0]}-{'landscape' if key[1] else 'portrait'}-{key[2]}"
        # Capture already measured these exact images. Reuse the recorded values
        # rather than scanning millions of pixels again for every comparison.
        fields = ('q_glyph_dp', 'keyboard_surfaces_rgb', 'panel_top_dp', 'letter_rows_dp')
        a = row if all(f in row for f in fields) else measure(out / (stem + '-' + row['language'] + '.png'), row['density'])
        b = baseline if all(f in baseline for f in fields) else measure(ref / (stem + '-' + baseline['language'] + '.png'), baseline['density'])
        deltas = []
        for ra, rb in zip(a['letter_rows_dp'][:3], b['letter_rows_dp'][:3]):
            assert ra['count'] == rb['count'], f'{stem}: wrong row key count'
            delta = {name: round(abs(ra[name]-rb[name]), 2) for name in ('top', 'height', 'width')}
            assert delta['top'] <= 3 and delta['height'] <= 1.5 and delta['width'] <= 1.5, f'{stem}: {delta}'
            deltas.append(delta)
        glyph = [round(abs(x-y), 2) for x, y in zip(a['q_glyph_dp'], b['q_glyph_dp'])]
        assert max(glyph) <= 1.0, f'{stem}: glyph size differs by {glyph} dp'
        assert abs(a['panel_top_dp'] - b['panel_top_dp']) <= 3, f'{stem}: keyboard panel height differs'
        for surface, color in b['keyboard_surfaces_rgb'].items():
            delta = max(abs(x-y) for x, y in zip(color, a['keyboard_surfaces_rgb'][surface]))
            assert delta <= 6, f'{stem}: {surface} color differs by {delta}'
        report.append({'profile': key[0], 'orientation': key[1], 'theme': key[2], 'row_deltas_dp': deltas, 'glyph_delta_dp': glyph})
    (out / 'comparison.json').write_text(json.dumps(report, indent=2)+'\n')
    print(f'PASS Gboard parity across {len(report)} configurations (boxes, glyphs, shared surface colors).', flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--keyboard', choices=IMES, required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--profiles', nargs='+', choices=PROFILES, default=['phone', 'compact-tablet', 'tablet'])
    p.add_argument('--themes', nargs='+', choices=('light', 'dark'), default=['light', 'dark'])
    p.add_argument('--language', choices=('en', 'bn'), default='en')
    p.add_argument('--compare-with', help='Directory containing reference captures and measurements.json')
    args = p.parse_args()
    capture(args)
    if args.compare_with:
        compare(args.output, args.compare_with)
