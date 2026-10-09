#!/usr/bin/env python3
"""Catch stale rendered themes after actual settings changes on the existing phone AVD.
Requires installed debug APK, normal phone viewport, Pillow (already used by parity).
Leaves Default / Follow system / key borders on after the test.
"""
import importlib.util
import io
import subprocess
import re
from pathlib import Path
from PIL import Image

spec = importlib.util.spec_from_file_location('touch', Path(__file__).with_name('test-native-touch.py'))
touch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(touch)
n = touch.n


def settings():
    n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity', '-f', '0x10008000', '--es', 'screen', 'Appearance')
    n.screen()


def choose(label):
    nodes = list(n.screen().iter('node'))
    node = next((x for x in nodes if x.get('content-desc') == label), None)
    if node is None:
        node = next(x for x in nodes if x.get('text') == label)
    x0,y0,x1,y1 = map(int, re.findall(r'\d+', node.get('bounds')))
    touch.tap(((x0+x1)//2,(y0+y1)//2))
    n.screen()


def apply(label, toggle_borders=False):
    settings()
    choose(label)
    if toggle_borders:
        choose('Key borders')
    choose('Apply')


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
        for label,rgb in [('Default',(240,244,249)),('Default Dark',(30,31,32)),('Default',(240,244,249))]:
            apply(label)
            for language in ('bn','en'):
                colors(language,rgb)
                print('PASS actual',label,language,'render after Apply',flush=True)
        settings();choose('Default Dark');choose('Cancel')
        for language in ('bn','en'):
            colors(language,(240,244,249))
            print('PASS Cancel leaves active theme unchanged',language,flush=True)
        apply('Dynamic Color')
        for night in ('yes','no'):
            n.shell('cmd','uimode','night',night)
            for language in ('bn','en'):
                colors(language)
                print('PASS dynamic system night',night,language,'render',flush=True)
        apply('Default',toggle_borders=True)
        for language in ('bn','en'):
            controls=colors(language)
            assert not controls['borders'],controls
            print('PASS',language,'borderless rendered theme',flush=True)
    finally:
        n.shell('cmd','uimode','night','no')
        controls=n.inspect()['controls']
        apply('System Auto',toggle_borders=not controls['borders'])
    print('Native appearance regressions passed.',flush=True)


if __name__=='__main__':
    run()
