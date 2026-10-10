#!/usr/bin/env python3
"""Touch-only optimized APK smoke test against a separate recipient UID/task.
Requires build/release-keyboard-geometry.json captured from the same debug layout.
Smoke mode never invokes an Obadh debug receiver, sends a message or logs a provider
URL/key. The optional geometry-recording step runs before installing release.
"""
import importlib.util
import argparse
import json
import os
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('media', Path(__file__).with_name('test-native-media.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
n = m.n
HOST = 'org.unmukto.obadh.mediarecipient'
ACTIVITY = HOST + '/.MediaRecipientActivity'
GEOMETRY = Path(__file__).resolve().parent.parent / 'build/release-keyboard-geometry.json'
KEYS = {}


def tap_key(code):
    n.shell('input', 'tap', *KEYS[str(code)])
    n.screen()


def editor():
    return next(x for x in m.nodes() if x.get('package') == HOST and x.get('resource-id') == 'android:id/edit')


def start(language):
    n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity')
    n.shell('am', 'start', '-n', ACTIVITY, '-f', '0x10008000', '--es', 'types', 'image/gif,image/webp')
    x0, y0, x1, y1 = m.bounds(editor())
    n.shell('input', 'tap', (x0+x1)//2, (y0+y1)//2)
    n.screen()
    state = n.shell('dumpsys', 'input_method')
    assert 'mCurMethodId=' + n.PACKAGE + '/.keyboard.ObadhInputMethodService' in state and 'mInputShown=true' in state
    x, y = KEYS['32']
    n.shell('input', 'swipe', x, y, x, y, 800)
    prefix = 'বাংলা' if language == 'bn' else 'English'
    label = next(x.get('text') for x in m.nodes() if x.get('text', '').startswith(prefix))
    m.tap(label)
    n.screen()


def glide(roman):
    points = [KEYS[str(ord(c))] for c in roman]
    n.shell('input', 'motionevent', 'DOWN', *points[0])
    try:
        for a, b in zip(points, points[1:]):
            for fraction in (.25, .5, .75, 1):
                n.shell('input', 'motionevent', 'MOVE',
                        round(a[0]+(b[0]-a[0])*fraction), round(a[1]+(b[1]-a[1])*fraction))
    finally:
        n.shell('input', 'motionevent', 'UP', *points[-1])
    n.screen()


def expect(value):
    actual = editor().get('text')
    assert actual == value, (value, actual)


def share(kind, expected_mime, text):
    if not any(x.get('content-desc') == kind for x in m.nodes()):
        m.tap('Keyboard tools')
    m.tap(kind)
    if any(x.get('text') == 'Enable' for x in m.nodes()):
        m.tap('Enable')
    row = m.wait_rows(kind)[0]
    n.adb('logcat', '-c')
    m.tap(row.get('content-desc'))
    deadline = time.monotonic()+40
    while time.monotonic() < deadline:
        log = n.adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhExternalReceipt:V', '*:S')
        assert 'Failed media import' not in log, log
        receipt = next((json.loads(x) for x in reversed(log.splitlines()) if x.startswith('{')), None)
        if receipt:
            assert receipt['mime'] == expected_mime and receipt['seekable'] and receipt['readOnly'], receipt
            state = n.shell('dumpsys', 'activity', 'activities')
            assert re.search(r'topResumedActivity=ActivityRecord\{[^\n]*'+re.escape(ACTIVITY), state), 'Wrong foreground task'
            expect(text)
            print('PASS optimized '+kind+' cross-UID native decode, scoped read-only seekable FD, original editor/task and text', flush=True)
            return
        time.sleep(.2)
    raise AssertionError('No external recipient callback')


def run():
    global KEYS
    KEYS = json.loads(GEOMETRY.read_text())
    assert os.environ.get('ANDROID_SERIAL', '').startswith('emulator-'), 'Use the explicit QA emulator'
    packages = n.shell('pm', 'list', 'packages', '-U')
    app = re.search(r'package:'+re.escape(n.PACKAGE)+r' uid:(\d+)', packages)
    host = re.search(r'package:'+re.escape(HOST)+r' uid:(\d+)', packages)
    assert app and host and app[1] != host[1]
    n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
    start('bn')
    for roman in ('ami', 'bhalo', 'achi'):
        glide(roman)
    expect('আমি ভালো আছি')
    print('PASS optimized Bangla continuous word glide', flush=True)
    share('Stickers', 'image/webp', 'আমি ভালো আছি')
    start('en')
    glide('world')
    expect('world')
    print('PASS optimized English native glide', flush=True)
    share('GIFs', 'image/gif', 'world')
    print('Optimized keyboard/media smoke tests passed.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--record-geometry', action='store_true', help='Read the current phone key layout from installed debug before installing release')
    args = parser.parse_args()
    if args.record_geometry:
        assert os.environ.get('ANDROID_SERIAL', '').startswith('emulator-')
        n.start('bn')
        n.command('key', code=-201)
        n.screen()
        values = m.t.a.touch.positions(list(range(ord('a'), ord('z')+1)) + [32, -7, -227])
        GEOMETRY.parent.mkdir(parents=True, exist_ok=True)
        GEOMETRY.write_text(json.dumps(values)+'\n')
        print('Recorded current debug renderer coordinates for touch-only release smoke')
    else:
        run()
