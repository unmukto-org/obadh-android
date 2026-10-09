#!/usr/bin/env python3
"""Opt-in live KLIPY checks on the existing emulator; requires a privately configured debug APK.
Calls the real provider only for explicit searches/sends. Never logs credentials or source URLs.
"""
import importlib.util
import json
import re
import subprocess
import time
from pathlib import Path

spec=importlib.util.spec_from_file_location('toolbar',Path(__file__).with_name('test-native-toolbar.py'))
t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t)
n=t.n

def nodes():return list(n.screen().iter('node'))
def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))
def tap(label):
    node=next(x for x in nodes() if x.get('text')==label or x.get('content-desc')==label)
    x0,y0,x1,y1=bounds(node);n.shell('input','tap',(x0+x1)//2,(y0+y1)//2)

def rows(kind):return [x for x in nodes() if f', {kind}' in x.get('content-desc','') and x.get('enabled')=='true']
def wait_rows(kind):
    deadline=time.monotonic()+40
    while time.monotonic()<deadline:
        found=rows(kind)
        if found:return found
        time.sleep(.5)
    raise AssertionError('No media rows: '+str([x.get('text') for x in nodes() if x.get('text')]))

def open_picker(kind,language='en',field='media',compose=''):
    t.start(language,field=field)
    if compose:
        n.type_text(compose);n.screen();t.tap('Keyboard tools')
    if kind not in t.controls():t.tap('Keyboard tools')
    t.tap(kind)
    if any(x.get('text')=='Enable' for x in nodes()):tap('Enable')
    assert any(x.get('content-desc')=='Powered by KLIPY' for x in nodes())
    return wait_rows(kind)

def search(kind,query,language='en'):
    tap('Search '+kind.lower() if kind=='Stickers' else 'Search GIFs')
    n.command('language',language=language);n.type_text(query)
    time.sleep(1)
    return wait_rows(kind)

def send(kind):
    candidate=wait_rows(kind)[0].get('content-desc')
    n.adb('logcat','-c');tap(candidate)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        output=n.adb('logcat','-d','-v','raw','-s','ObadhMediaReceipt:I','*:S')
        receipts=[json.loads(x) for x in output.splitlines() if x.startswith('{')]
        if receipts:
            receipt=receipts[-1];assert receipt['flags']==1 and receipt['bytes']>0,receipt
            assert receipt['mime'] in ('image/gif','image/webp'),receipt
            assert receipt['uri'].startswith('content://'+n.PACKAGE+'.media/media/'),receipt
            return receipt
        time.sleep(.2)
    raise AssertionError('Animation not received: '+output)

def cache_info():
    output=n.shell('run-as',n.PACKAGE,'sh','-c','find cache/klipy-thumbnails -type f -exec wc -c {} \\;')
    sizes=[int(x.split()[0]) for x in output.splitlines() if x.split() and x.split()[0].isdigit()]
    assert len(sizes)<=64 and sum(sizes)<=6*1024*1024,sizes
    return len(sizes),sum(sizes)

def network(enabled):
    n.shell('svc','wifi','enable' if enabled else 'disable');n.shell('svc','data','enable' if enabled else 'disable')
    time.sleep(1)

def run():
    network(True)
    open_picker('GIFs',field='gif_only',compose='hello')
    search('GIFs','bangla')
    receipt=send('GIFs');assert receipt['mime']=='image/gif',receipt
    assert n.text()=='hello',n.inspect();n.type_text(' world ');n.expect('hello world ','GIF preserves composing English and resumes typing')
    # A different UID has no grant to read a private delivery URI.
    blocked=subprocess.run(['adb','shell','content','read','--uri',receipt['uri']],capture_output=True,text=True)
    assert 'Permission Denial' in blocked.stderr+blocked.stdout,blocked.stderr
    print('PASS scoped GIF rich-content receipt and external UID denial',flush=True)

    open_picker('Stickers',language='bn',field='webp_only',compose='ami')
    search('Stickers','bangla')
    receipt=send('Stickers');assert receipt['mime']=='image/webp',receipt
    assert n.text()=='আমি',n.inspect();n.command('language',language='bn');n.type_text(' ami ')
    n.expect('আমি আমি ','WebP sticker preserves Bangla composition and resumes typing')
    print('PASS explicit WebP recipient and distinct sticker destination',flush=True)

    open_picker('GIFs');tap('Recent');recent=wait_rows('GIFs');assert recent
    assert any('Originals download when sent' in x.get('text','') for x in nodes())
    assert cache_info()[0]>0
    network(False)
    # Force a fresh activity/process load of recent references while offline.
    n.shell('am','force-stop',n.PACKAGE);n.shell('ime','set',n.PACKAGE+'/.keyboard.ObadhInputMethodService')
    t.start('en',field='media');t.tap('GIFs');tap('Recent');wait_rows('GIFs');time.sleep(1)
    assert cache_info()[0]>0
    tap(wait_rows('GIFs')[0].get('content-desc'));time.sleep(2)
    assert any(x.get('text')=='Retry' for x in nodes())
    tap('Back to keyboard');assert n.text()==''
    print('PASS persisted bounded thumbnail recents and offline send retry without text leakage',flush=True)

    network(True)
    open_picker('GIFs');tap('Recent');send('GIFs')
    print('PASS recent reference resolves fresh provider metadata and sends again',flush=True)

    open_picker('Stickers');search('Stickers','hello')
    field=next(x for x in nodes() if x.get('content-desc')=='Search stickers');query=field.get('text')
    n.shell('settings','put','system','accelerometer_rotation','0');n.shell('settings','put','system','user_rotation','1');time.sleep(1)
    assert next(x for x in nodes() if x.get('content-desc')=='Search stickers').get('text')==query
    tap('Back to keyboard');assert n.text()==''
    print('PASS search rotation, retained query and safe Back',flush=True)
    n.shell('settings','put','system','user_rotation','0');time.sleep(1)

    open_picker('GIFs',field='reject_media');n.adb('logcat','-c');tap(wait_rows('GIFs')[0].get('content-desc'));time.sleep(3)
    assert 'rejected' in n.adb('logcat','-d','-s','ObadhMediaReceipt:I','-v','raw')
    assert n.text()==''
    assert "couldn't accept the animation" in n.shell('dumpsys','notification')
    print('PASS rejected insertion feedback; recipient text unchanged',flush=True)

    for field in ('text','sticker','password'):
        t.start('en',field=field);controls=t.controls()
        assert not controls['Stickers']['enabled'] and not controls['GIFs']['enabled'],controls
    t.start('en',field='media');n.command('configure',**{'native.always_incognito_mode':True});n.screen()
    assert not t.controls()['GIFs']['enabled'] and not t.controls()['Stickers']['enabled']
    n.command('configure')
    print('PASS unsupported, password and incognito media guards',flush=True)
    print('Native online media checks passed.',flush=True)

if __name__=='__main__':
    try:run()
    finally:
        network(True);n.shell('settings','put','system','user_rotation','0');n.command('configure')
