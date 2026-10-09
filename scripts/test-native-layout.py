#!/usr/bin/env python3
"""Verify explicit native layout choices supersede toolbar overrides in both languages.
Uses the existing debug QA AVD; restores viewport and chooses Automatic afterward.
"""
import importlib.util
import re
from pathlib import Path

spec = importlib.util.spec_from_file_location('toolbar', Path(__file__).with_name('test-native-toolbar.py'))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
a, n = t.a, t.n


def automatic():
    n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity', '-f', '0x10008000', '--es', 'screen', 'Preferences')
    n.screen()
    a.choose('Tablet layout')
    a.choose('Automatic')


def run():
    size = re.search(r'Override size: (\d+x\d+)', n.shell('wm', 'size'))
    density = re.search(r'Override density: (\d+)', n.shell('wm', 'density'))
    rotation = n.shell('settings', 'get', 'system', 'user_rotation').strip()
    auto_rotate = n.shell('settings', 'get', 'system', 'accelerometer_rotation').strip()
    try:
        n.shell('settings', 'put', 'system', 'accelerometer_rotation', 0)
        n.shell('settings', 'put', 'system', 'user_rotation', 0)
        n.shell('wm', 'size', '1800x2560')
        n.shell('wm', 'density', 320)
        automatic()
        for language in ('bn', 'en'):
            t.start(language)
            assert n.inspect()['controls']['split'], n.inspect()
            t.tap('Keyboard tools')
            t.tap('Split')
            assert not n.inspect()['controls']['split'], n.inspect()
            print('PASS manual split override', language, flush=True)
            automatic()
            t.start(language)
            assert n.inspect()['controls']['split'], n.inspect()
            print('PASS explicit Automatic restores tablet geometry', language, flush=True)
    finally:
        n.shell('wm', 'size', size.group(1) if size else 'reset')
        n.shell('wm', 'density', density.group(1) if density else 'reset')
        n.shell('settings', 'put', 'system', 'user_rotation', rotation)
        n.shell('settings', 'put', 'system', 'accelerometer_rotation', auto_rotate)
        n.command('configure')
    print('Native layout regressions passed.', flush=True)


if __name__ == '__main__':
    run()
