#!/usr/bin/env python3
"""Catch stale rendered themes after actual settings changes on the existing phone AVD.
Requires installed debug APK, normal phone viewport, Pillow (already used by parity).
Leaves Default / Follow system / key borders on after the test.
"""
import importlib.util
import io
import subprocess
from pathlib import Path
from PIL import Image

spec = importlib.util.spec_from_file_location('touch', Path(__file__).with_name('test-native-touch.py'))
touch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(touch)
n = touch.n


def settings():
    n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity', '-f', '0x10008000', '--es', 'screen', 'Appearance')
    n.screen()


def choose(title, option):
    settings()
    touch.choose(title)
    touch.choose(option)


def colors(language, expected=None):
    n.start(language)
    keys = touch.positions([ord('q'), ord('w')])
    a,b = keys[ord('q')],keys[ord('w')]
    controls = n.inspect()['controls']
    image = Image.open(io.BytesIO(subprocess.check_output(['adb','exec-out','screencap','-p']))).convert('RGB')
    background = image.getpixel(((a[0]+b[0])//2,a[1]))
    wanted = controls['theme_background']
    rgb = ((wanted>>16)&255,(wanted>>8)&255,wanted&255)
    assert background == rgb, ('stale renderer',language,background,rgb,controls)
    fill = image.getpixel((a[0]-25,a[1]-10))
    key = controls['theme_keys'] if controls['borders'] else wanted
    key_rgb = ((key>>16)&255,(key>>8)&255,key&255)
    assert fill == key_rgb, ('stale key fill',language,fill,key_rgb,controls)
    if expected is not None:
        assert background == expected,(background,expected)
    return controls


def run():
    n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
    try:
        choose('Color palette','Default')
        for mode,rgb in [('Light',(240,244,249)),('Dark',(30,31,32)),('Light',(240,244,249))]:
            choose('Appearance',mode)
            for language in ('bn','en'):
                colors(language,rgb)
                print('PASS actual',mode,language,'render after settings change',flush=True)
        choose('Color palette','System colors')
        for mode in ('Dark','Light'):
            choose('Appearance',mode)
            for language in ('bn','en'):
                colors(language)
                print('PASS wallpaper palette',mode,language,'render',flush=True)
        # Border change must affect the actual fill, not only the preview preference.
        settings();touch.choose('Key borders')
        for language in ('bn','en'):
            controls=colors(language)
            assert not controls['borders'],controls
            print('PASS',language,'borderless rendered theme',flush=True)
    finally:
        settings()
        # Read canonical border state from the actual IME before restoring it.
        if not n.inspect()['controls']['borders']:
            touch.choose('Key borders')
        choose('Color palette','Default')
        choose('Appearance','Follow system')
    print('Native appearance regressions passed.',flush=True)


if __name__=='__main__':
    run()
