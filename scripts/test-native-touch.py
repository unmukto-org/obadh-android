#!/usr/bin/env python3
"""Actual touch checks on the existing emulator; debug probe only reads layout/state.
Run after installing debug, with default full phone layout and both languages enabled.
Requires no additional emulator image or test library.
"""
import importlib.util
import json
from pathlib import Path
import re

spec = importlib.util.spec_from_file_location('native', Path(__file__).with_name('test-native-keyboard.py'))
n = importlib.util.module_from_spec(spec)
spec.loader.exec_module(n)


def positions(codes):
    n.command('coordinates', codes=','.join(map(str, codes)))
    lines = n.adb('logcat', '-d', '-s', 'ObadhProbeCoordinates:I', '-v', 'raw').splitlines()
    layout = json.loads(next(line for line in reversed(lines) if line.startswith('{')))
    x, y = layout['origin']
    result = {}
    for code, a, b in zip(layout['codes'], layout['coordinates'][::2], layout['coordinates'][1::2]):
        assert a >= 0 and b >= 0, (code, layout)
        result[code] = (a+x, b+y)
    return result


def tap(point):
    n.shell('input', 'tap', *point)


def choose(label):
    node = next(x for x in n.screen().iter('node') if x.get('text') == label)
    x0,y0,x1,y1 = map(int, re.findall(r'\d+', node.get('bounds')))
    tap(((x0+x1)//2, (y0+y1)//2))
    n.screen()


def run():
    n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
    n.start('bn')
    n.command('configure')
    keys = positions([97,109,105,32,-227])
    for char in 'ami':
        tap(keys[ord(char)])
    assert n.text() == 'আমি', n.inspect()
    tap(keys[-227])
    n.screen()
    assert n.inspect()['controls']['locale'] == 'en', n.inspect()
    assert n.text() == 'আমি', n.inspect()
    print('PASS actual Bangla key taps and globe preserve composition', flush=True)

    keys = positions([32,-227])
    x,y = keys[32]
    n.shell('input', 'swipe', x,y,x,y,800)
    assert any(x.get('text') == 'Change language' for x in n.screen().iter('node'))
    choose('বাংলা · Obadh')
    assert n.inspect()['controls']['locale'] == 'bn', n.inspect()
    print('PASS long-space native language picker', flush=True)

    keys = positions([32])
    x,y = keys[32]
    n.shell('input', 'swipe', x-160,y,x+160,y,250)
    n.screen()
    assert n.inspect()['controls']['locale'] == 'en', n.inspect()
    print('PASS space-swipe language switching', flush=True)

    # Decoder must already have been downloaded with the app's opt-in switch.
    n.start('en')
    keys = positions(list(map(ord, 'world')))
    points = [keys[ord(c)] for c in 'world']
    n.shell('input','motionevent','DOWN',*points[0])
    try:
        for a,b in zip(points,points[1:]):
            for t in (.25,.5,.75,1):
                p=(round(a[0]+(b[0]-a[0])*t),round(a[1]+(b[1]-a[1])*t))
                n.shell('input','motionevent','MOVE',*p)
    finally:
        n.shell('input','motionevent','UP',*points[-1])
    n.screen()
    word=n.text().strip().lower()
    assert word == 'world', (word,n.inspect())
    print('PASS actual English glide gesture enters world', flush=True)
    for language in ('bn', 'en'):
        for code, field, name in ((-10002, 'one_handed', 'one-handed'), (-109, 'floating', 'floating')):
            n.start(language)
            n.command('key', code=code)
            n.screen()
            assert n.inspect()['controls'][field], n.inspect()
            try:
                keys = positions([97,32])
                tap(keys[97]); tap(keys[32])
                assert n.text().strip().lower() == ('আ' if language == 'bn' else 'a'), n.inspect()
            finally:
                n.command('key', code=code)
            n.screen()
            assert not n.inspect()['controls'][field], n.inspect()
            print('PASS', language, name, 'typing and restoration', flush=True)
    n.command('configure')
    print('Native touch regressions passed.', flush=True)


if __name__ == '__main__':
    run()
