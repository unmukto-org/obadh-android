#!/usr/bin/env python3
"""Real toolbar/comma touches; the debug receiver only reads visible control coordinates."""
import importlib.util
import json
from pathlib import Path

spec=importlib.util.spec_from_file_location('appearance',Path(__file__).with_name('test-native-appearance.py'))
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
n=a.n

def controls():
    n.command('toolbar_coordinates')
    output=n.adb('logcat','-d','-s','ObadhProbeToolbar:I','-v','raw')
    return {c['label']:c for c in json.loads(next(x for x in reversed(output.splitlines()) if x.startswith('[')))}

def tap(label):
    c=controls()[label];assert c['enabled'],c
    a.touch.tap((c['x'],c['y']));n.screen()

def start(language, field="text"):
    n.start(language,field=field)
    n.command("configure", **{"native.suggest_clipboard_content": False}) # No clipboard fixture chip in the home-layout checks.
    n.command("key",code=-201) # Reset the fixture to the native alphabet layout.
    n.screen()

def run():
    n.shell('ime','set',n.PACKAGE+'/.keyboard.ObadhInputMethodService')
    for language in ('bn','en'):
        start(language);home=controls()
        media='Stickers' in home
        expected=['Keyboard tools']+(['Stickers','GIFs'] if media else [])+['Clipboard','Settings','Theme','Voice typing']
        assert list(home)==expected,home
        if media: assert not home['Stickers']['enabled'] and not home['GIFs']['enabled'],home
        tap('Keyboard tools');grid=controls()
        assert 'Back to keyboard' in grid and 'Keyboard size' in grid and 'Next language' in grid,grid
        bottom=grid['Back to keyboard']['y']+grid['Back to keyboard']['height']/2
        assert all(grid[label]['y']-grid[label]['height']/2>=bottom for label in ['One-handed','Text editing','Floating','Keyboard size','Next language','Undo']),grid
        tap('Back to keyboard');assert 'Keyboard size' not in controls()
        print('PASS fixed toolbar, in-keyboard grid and Back',language,flush=True)
        start(language);n.type_text('am' if language=='bn' else 'hel');n.screen()
        before=n.inspect();assert before['labels'],before
        assert 'Stickers' not in controls(),controls()
        tap('Keyboard tools');assert ('Stickers' in controls())==media and 'Back to keyboard' in controls()
        tap('Back to keyboard');assert 'Stickers' not in controls(),controls()
        assert n.inspect()['labels']==before['labels'],n.inspect()
        print('PASS context-aware tools restore composing candidates',language,flush=True)
        start(language)
        tap('Keyboard tools');tap('Next language')
        assert n.inspect()['controls']['locale']==('en' if language=='bn' else 'bn')
        print('PASS tools language switching',language,flush=True)
        start(language);before=controls();tap('Keyboard tools');assert 'Emoji' not in controls();tap('Back to keyboard')
        x,y=a.touch.positions([44])[44];n.shell('input','swipe',x,y,x,y,800);n.screen()
        assert n.inspect()['controls']['emoji_panel']
        a.touch.tap(a.touch.positions([-201],surface='emoji')[-201]);n.screen()
        after=controls()
        assert not n.inspect()['controls']['emoji_panel']
        assert list(after)==list(before),(before,after)
        assert all(after[label]['width']==before[label]['width'] and after[label]['x']==before[label]['x'] for label in before),(before,after)
        tap('Keyboard tools');menu=controls();assert 'One-handed' in menu
        assert 'Emoji' not in menu,menu
        tap('Back to keyboard')
        print('PASS comma Emoji → actual ABC restores identical toolbar; no duplicate Emoji shortcut',language,flush=True)
        start(language);tap('Clipboard');assert n.inspect()['controls']['clipboard_panel']
        start(language);a.touch.tap(a.touch.positions([44])[44]);assert n.text()==',',n.inspect()
        start(language);x,y=a.touch.positions([44])[44];n.shell('input','swipe',x,y,x,y,800);n.screen()
        assert n.inspect()['controls']['emoji_panel'],n.inspect()
        print('PASS clipboard, comma tap and emoji hold',language,flush=True)
        start(language);tap('Theme')
        assert any(x.get('text')=='My themes' for x in n.screen().iter('node'))
        start(language);tap('Voice typing')
        assert 'Voice typing will be available in the next release' in n.shell('dumpsys','notification')
        print('PASS private theme destination and microphone placeholder',language,flush=True)
        start(language,field='password');private=controls()
        assert 'Keyboard tools' not in private and 'Voice typing' not in private,private
        assert not private['Clipboard']['enabled'],private
        if media: assert not private['Stickers']['enabled'] and not private['GIFs']['enabled'],private
        print('PASS sensitive-field toolbar guards',language,flush=True)
    start('bn');tap('Keyboard tools');tap('Keyboard size')
    assert any(x.get('text')=='Preferences' for x in n.screen().iter('node'))
    start('bn')
    print('PASS standard Android preferences destination',flush=True)
    print('Native toolbar regressions passed.',flush=True)

if __name__=='__main__':
    try:run()
    finally:n.command('configure',**{'native.suggest_clipboard_content':True})
