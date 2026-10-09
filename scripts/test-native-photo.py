#!/usr/bin/env python3
"""Photo gallery transaction and rotation checks on the existing rooted test AVD.
Requires debug APK and Pillow; generates/removes only its own small photo fixture.
Run with ANDROID_SERIAL when more than one emulator is connected.
"""
import importlib.util
import json
import io
import subprocess
import re
import tempfile
from pathlib import Path
from PIL import Image

spec=importlib.util.spec_from_file_location('appearance',Path(__file__).with_name('test-native-appearance.py'))
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
n=a.n
REMOTE='/sdcard/DCIM/Obadh-theme-regression.jpg'
DATA='/data/user_de/0/org.unmukto.obadh/files/keyboard_photos/'
PREFS='/data/user/0/org.unmukto.obadh/shared_prefs/obadh_prefs.xml'

def records():
    raw=n.shell('cat',DATA+'themes.json')
    return json.loads(raw) if raw.strip() else []

def active():
    import xml.etree.ElementTree as ET
    root=ET.fromstring(n.shell('cat',PREFS))
    return {x.get('name'):x.text or x.get('value') for x in root if x.get('name','').startswith('keyboard_')}

def pick_fixture():
    a.choose('Create keyboard theme with my image')
    node=next(x for x in n.screen().iter('node') if x.get('content-desc','').startswith('Photo taken'))
    x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')))
    a.touch.tap(((x+r)//2,(y+b)//2));n.screen()

def run():
    # The fresh fixture is the most recent image in this isolated QA AVD's photo picker.
    with tempfile.TemporaryDirectory(prefix='obadh-photo-test-') as work:
        path=Path(work)/'fixture.jpg'
        Image.new('RGB',(640,480),(65,110,160)).save(path,quality=85)
        n.adb('push',str(path),REMOTE)
        n.shell('am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file://'+REMOTE)
        old_rotation=n.shell('settings','get','system','user_rotation').strip()
        old_auto_rotate=n.shell('settings','get','system','accelerometer_rotation').strip()
        created=None
        try:
            n.shell('settings','put','system','accelerometer_rotation',0)
            a.settings();before=active();old_ids={r['id'] for r in records()}
            pick_fixture();a.choose('Next')
            assert any(x.get('text')=='40%' for x in n.screen().iter('node'))
            n.shell('settings','put','system','user_rotation',1);n.screen()
            rotated=Image.open(io.BytesIO(subprocess.check_output(['adb','exec-out','screencap','-p'])))
            assert rotated.width>rotated.height,rotated.size
            assert any(x.get('text')=='Adjust Brightness' for x in n.screen().iter('node'))
            assert any(x.get('text')=='40%' for x in n.screen().iter('node'))
            n.shell('settings','put','system','user_rotation',0);n.screen()
            a.choose('Done');a.choose('Cancel')
            assert active()==before,(before,active())
            assert {r['id'] for r in records()}==old_ids,records()
            print('PASS photo decode, brightness, rotation, Done and Cancel transaction',flush=True)
            pick_fixture();a.choose('Next');a.choose('Done');a.choose('Apply')
            saved=records();created=next(r for r in saved if r['id'] not in old_ids)
            assert created['brightness']==float(.4) or abs(created['brightness']-.4)<.0001
            label='Custom photo '+str(next(i+1 for i,r in enumerate(saved) if r['id']==created['id']))

            assert active()['keyboard_photo_id']==created['id']
            for language in ('bn','en'):
                n.start(language);controls=n.inspect()['controls']
                assert controls['theme_keys']==0x4dffffff,controls
                n.type_text('ami' if language=='bn' else 'hello')
                assert n.text()==('আমি' if language=='bn' else 'hello')
                print('PASS photo theme rendering and typing',language,flush=True)
            a.settings();a.choose(label);a.choose('Edit theme');a.choose('Next')
            assert any(x.get('text')=='40%' for x in n.screen().iter('node'))
            n.shell('input','keyevent',4);n.screen();n.shell('input','keyevent',4);n.screen()
            a.choose(label);a.choose('Delete theme');a.choose('Delete')
            assert created['id'] not in {r['id'] for r in records()}
            assert active()['keyboard_theme']=='default',active()
            assert 'keyboard_photo_id' not in active(),active()
            created=None
            print('PASS photo re-edit, editor back, delete and active-theme fallback',flush=True)
        finally:
            n.shell('settings','put','system','user_rotation',old_rotation)
            n.shell('settings','put','system','accelerometer_rotation',old_auto_rotate)
            n.shell('rm','-f',REMOTE)
            if created:
                raise AssertionError('Photo QA interrupted; remove only fixture theme '+created['id'])
    print('Native photo regressions passed.',flush=True)

if __name__=='__main__':run()
