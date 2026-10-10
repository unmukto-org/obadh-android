#!/usr/bin/env python3
"""Live media handoff to a separate APK/UID/task, including seekable asynchronous reads.
Build/install scripts/build-media-recipient.sh and the privately configured Obadh debug APK.
Uses the existing emulator and real KLIPY selections; never sends a message to another person.
"""
import importlib.util
import json
import re
import subprocess
import time
from pathlib import Path

spec=importlib.util.spec_from_file_location('media',Path(__file__).with_name('test-native-media.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
n=m.n
HOST='org.unmukto.obadh.mediarecipient'
ACTIVITY=HOST+'/.MediaRecipientActivity'

def start(types='image/gif,image/webp',language='en',compose='',delay=400,resume_delay=0):
    # Keep Obadh settings warm in its own task: the previous same-package fixture
    # concealed the task-reuse bug seen when a real chat is in the foreground.
    n.command('configure',**{'native.always_incognito_mode':False,'native.next_word_prediction':False,'native.suggest_clipboard_content':False})
    n.shell('am','start','-n',n.PACKAGE+'/.app.MainActivity')
    n.shell('am','start','-n',ACTIVITY,'-f','0x10008000','--es','types',types,'--ei','readDelay',delay,'--ei','resumeDelay',resume_delay)
    root=m.nodes()
    field=next(x for x in root if x.get('resource-id')=='android:id/edit' and x.get('package')==HOST)
    x0,y0,x1,y1=m.bounds(field)
    for _ in range(3):
        n.shell('input','tap',(x0+x1)//2,(y0+y1)//2);n.screen()
        state=n.shell('dumpsys','input_method')
        if 'mInputShown=true' in state and 'mInputViewStarted=true' in state:break
    assert 'mInputShown=true' in state and 'mInputViewStarted=true' in state, 'Recipient IME did not become visible'
    n.command('language',language=language)
    if compose:n.type_text(compose);n.screen()
    return n.text()

def returned():
    deadline=time.monotonic()+5
    while time.monotonic()<deadline:
        state=n.shell('dumpsys','activity','activities')
        if re.search(r'topResumedActivity=ActivityRecord\{[^\n]*'+re.escape(ACTIVITY),state):return
        time.sleep(.1)
    raise AssertionError('Picker failed to restore the recipient task')

def open_picker(kind):
    if kind not in m.t.controls():m.t.tap('Keyboard tools')
    m.t.tap(kind)
    if any(x.get('text')=='Enable' for x in m.nodes()):m.tap('Enable')
    row=m.wait_rows(kind)[0]
    tasks=n.shell('dumpsys','activity','activities')
    host_task=re.search(re.escape(ACTIVITY)+r' t(\d+)',tasks)
    picker_task=re.search(re.escape(n.PACKAGE+'/.media.MediaSearchActivity')+r' t(\d+)',tasks)
    main_task=re.search(re.escape(n.PACKAGE+'/.app.MainActivity')+r' t(\d+)',tasks)
    assert host_task and picker_task and main_task, 'Expected distinct recipient, picker and Obadh settings tasks'
    assert len({host_task[1],picker_task[1],main_task[1]})==3, 'Picker reused an application task'
    return row

def send(kind,expected_mime,original):
    row=open_picker(kind)
    n.adb('logcat','-c')
    x0,y0,x1,y1=m.bounds(row);n.shell('input','tap',(x0+x1)//2,(y0+y1)//2)
    deadline=time.monotonic()+40
    while time.monotonic()<deadline:
        log=n.adb('logcat','-d','-v','raw','-s','ObadhExternalReceipt:V','*:S')
        assert 'Failed media import' not in log,log
        receipt=next((json.loads(x) for x in reversed(log.splitlines()) if x.startswith('{')),None)
        if receipt:
            assert receipt['mime']==expected_mime and receipt['bytes']>0 and receipt['flags']==1,receipt
            assert receipt['seekable'] and receipt['readOnly'],receipt
            returned();assert n.text()==original,n.inspect()
            blocked=subprocess.run(['adb','shell','content','read','--uri',receipt['uri']],capture_output=True,text=True)
            assert 'Permission Denial' in blocked.stdout+blocked.stderr,blocked.stderr
            print('PASS cross-UID '+kind+', task restoration, delayed read, seek/decode, scoped read-only grant and preserved text',flush=True)
            return receipt
        time.sleep(.1)
    raise AssertionError('No external recipient callback: '+log)

def run():
    packages=n.shell('pm','list','packages','-U')
    app=re.search(r'package:'+re.escape(n.PACKAGE)+r' uid:(\d+)',packages)
    host=re.search(r'package:'+re.escape(HOST)+r' uid:(\d+)',packages)
    assert app and host and app[1]!=host[1], 'Recipient must have an independent UID'
    original=start('image/gif',compose='hello',resume_delay=6500);send('GIFs','image/gif',original)
    original=start('image/webp',language='bn',compose='ami',delay=6500);send('Stickers','image/webp',original)
    original=start('image/gif',compose='hello')
    open_picker('Stickers');m.tap('Back to keyboard');returned();assert n.text()==original
    print('PASS cancel/back restores the external chat without touching its draft',flush=True)
    start('image/png')
    controls=m.t.controls()
    if 'GIFs' not in controls:m.t.tap('Keyboard tools');controls=m.t.controls()
    assert not controls['GIFs']['enabled'] and not controls['Stickers']['enabled'],controls
    print('PASS external unsupported recipient guard',flush=True)

if __name__=='__main__':
    try:run()
    finally:n.command('configure',**{'native.always_incognito_mode':False,'native.next_word_prediction':True,'native.suggest_clipboard_content':True})
