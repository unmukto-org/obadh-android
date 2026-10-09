#!/usr/bin/env python3
"""Actual native picker/search touches and framework commitContent, debug fixture only."""
import importlib.util,json,re,time
from pathlib import Path
spec=importlib.util.spec_from_file_location('toolbar',Path(__file__).with_name('test-native-toolbar.py'))
t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t);n=t.n

def receipt():
    output=n.adb('logcat','-d','-v','raw','-s','ObadhStickerReceipt:I','*:S')
    return [json.loads(x) for x in output.splitlines() if x.startswith('{')]

def touch_node(description):
    node=next(x for x in n.screen().iter('node') if x.get('content-desc')==description)
    x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')));t.a.touch.tap(((x+r)//2,(y+b)//2));n.screen()

def picker(language='en',field='sticker'):
    t.start(language,field=field);t.tap('Stickers');n.screen()

def run():
    n.shell('ime','set',n.PACKAGE+'/.keyboard.ObadhInputMethodService')
    for language in ('bn','en'):
        picker(language);n.adb('logcat','-c');t.tap('Fox, Blobfox, sticker')
        rows=receipt();assert rows and rows[-1]['flags']==1 and rows[-1]['bytes']>100
        assert rows[-1]['uri'].startswith('content://'+n.PACKAGE+'.stickers/stickers/')
        assert 'Search stickers' not in t.controls();assert n.text()==''
        print('PASS',language,'PNG commitContent, URI permission flag and picker dismissal',flush=True)
        t.start(language,field='sticker');n.type_text('ami' if language=='bn' else 'hello');n.screen();before=n.text()
        t.tap('Keyboard tools');t.tap('Stickers');n.adb('logcat','-c');t.tap('Fox, Blobfox, sticker')
        assert receipt() and n.text()==before,(before,n.text())
        n.type_text('a');assert n.text().startswith(before)
        print('PASS',language,'literal composition preserved across rich-content insertion and resumed typing',flush=True)
    for field in ('text','gif_only'):
        picker(field=field);cells=t.controls();assert not cells['Fox, Blobfox, sticker']['enabled']
        assert any(x.get('text')=="This app doesn't accept keyboard stickers" for x in n.screen().iter('node'))
        print('PASS unsupported editor',field,flush=True)
    picker(field='reject_sticker');n.adb('logcat','-c');t.tap('Fox, Blobfox, sticker');assert 'Search stickers' in t.controls()
    assert 'rejected' in n.adb('logcat','-d','-v','raw','-s','ObadhStickerReceipt:I','*:S')
    print('PASS editor rejection retains picker',flush=True)
    t.start('en',field='password');assert not t.controls()['Stickers']['enabled'];print('PASS password guard',flush=True)
    picker();t.tap('Search stickers');n.shell('input','text','happy');n.screen();n.adb('logcat','-c');touch_node('happy, Blobfox, sticker')
    # Returning from a transient search editor may reopen the IME asynchronously.
    for _ in range(3):
        if receipt():break
        time.sleep(.3);n.screen()
    assert receipt(),'Search did not deliver to its original recipient'
    assert n.text()=='';print('PASS local search returns image, never query text, to original editor',flush=True)
    picker();t.tap('Search stickers');n.shell('input','text','zzzznotfound');assert any('No stickers found' in x.get('text','') for x in n.screen().iter('node'))
    touch_node('Back to stickers');n.screen();assert n.text()==''
    print('PASS empty-search and Back preserve original editor',flush=True)
    n.command('configure',**{'native.always_incognito_mode': True});picker();n.command('configure',**{'native.always_incognito_mode': True});t.tap('Stickers') if 'Search stickers' not in t.controls() else None
    assert 'Recent' not in t.controls();print('PASS incognito hides recents',flush=True)
    n.command('configure');t.start('bn')
    print('Native sticker regressions passed.',flush=True)

if __name__=='__main__':
    try:run()
    finally:n.command('configure')
