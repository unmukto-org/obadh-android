#!/usr/bin/env python3
"""Real-touch Shift and Caps Lock checks for Obadh's phonetic QWERTY layout."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('touch', Path(__file__).with_name('test-native-touch.py'))
touch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(touch)
n = touch.n


def run():
    n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
    n.start('bn')
    keys = touch.positions([-11, ord('t'), ord('a'), 32])
    touch.tap(keys[-11])
    # The visible shifted key and the emitted code point must agree.
    touch.positions([ord('T')])
    touch.tap(keys[ord('t')])
    touch.tap(keys[ord('a')])
    assert n.text() == 'টা', n.inspect()
    touch.tap(keys[32])
    touch.tap(keys[ord('t')])
    assert n.text() == 'টা ত', n.inspect()
    print('PASS Bangla Shift applies one phonetic capital, then releases', flush=True)

    n.start('bn')
    keys = touch.positions([-11, ord('t'), ord('r'), 32])
    touch.tap(keys[-11]); touch.tap(keys[-11])
    touch.tap(keys[ord('t')]); touch.tap(keys[32]); touch.tap(keys[ord('r')])
    assert n.text() == 'ট ড়', n.inspect()
    touch.tap(keys[32]); touch.tap(keys[-11]); touch.tap(keys[ord('t')])
    assert n.text() == 'ট ড় ত', n.inspect()
    print('PASS Bangla Caps Lock holds phonetic capitals and unlocks', flush=True)

    n.start('en')
    keys = touch.positions([-11, ord('t'), ord('r')])
    touch.tap(keys[-11]); touch.tap(keys[ord('t')]); touch.tap(keys[ord('r')])
    assert n.text() == 'Tr', n.inspect()
    print('PASS English one-shot Shift remains unchanged', flush=True)

    n.start('bn', field='password')
    keys = touch.positions([-11, ord('t')])
    touch.tap(keys[-11]); touch.tap(keys[ord('t')])
    assert n.text() == 'T', n.inspect()
    print('PASS protected fields keep literal shifted Latin keys', flush=True)

    n.start('bn')


if __name__ == '__main__':
    run()
