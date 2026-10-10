#!/usr/bin/env python3
"""Opt-in QKSMS local draft test on an emulator; no message is transmitted.
Install QKSMS and a privately configured Obadh debug APK before running.
"""
import importlib.util,time,os
from pathlib import Path
assert os.environ.get("ANDROID_SERIAL", "").startswith("emulator-"), "Select the existing test emulator with ANDROID_SERIAL"
spec=importlib.util.spec_from_file_location('media',Path(__file__).with_name('test-native-media.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);n=m.n
PACKAGE='com.moez.QKSMS'
try:
 n.command('configure',**{'native.always_incognito_mode':False,'native.next_word_prediction':False,'native.suggest_clipboard_content':False})
 n.shell('am','start','-n',n.PACKAGE+'/.app.MainActivity')
 n.shell('am','start','-a','android.intent.action.SENDTO','-d','smsto:5556','-p',PACKAGE)
 detach=[x for x in m.nodes() if x.get('resource-id')==PACKAGE+':id/detach']
 while detach:
  x0,y0,x1,y1=m.bounds(detach[0]);n.shell('input','tap',(x0+x1)//2,(y0+y1)//2)
  detach=[x for x in m.nodes() if x.get('resource-id')==PACKAGE+':id/detach']
 field=next(x for x in m.nodes() if x.get('resource-id')==PACKAGE+':id/message')
 x0,y0,x1,y1=m.bounds(field);n.shell('input','tap',(x0+x1)//2,(y0+y1)//2);n.screen()
 original=n.text()
 if not original:n.type_text('Obadh media test ');original=n.text()
 for count,kind in enumerate(('Stickers','GIFs'),1):
  controls=m.t.controls()
  if kind not in controls:m.t.tap('Keyboard tools')
  m.t.tap(kind)
  row=m.wait_rows(kind)[0];print('SELECT',kind,flush=True)
  n.adb('logcat','-c');x0,y0,x1,y1=m.bounds(row);n.shell('input','tap',(x0+x1)//2,(y0+y1)//2)
  deadline=time.monotonic()+45
  while time.monotonic()<deadline:
   nodes=m.nodes()
   images=[x for x in nodes if x.get('resource-id')==PACKAGE+':id/thumbnail']
   if len(images)==count:break
   time.sleep(.2)
  print('IMAGES',len(images),flush=True)
  assert len(images)==count,('Attachment not added',kind,len(images),count)
  assert n.text()==original,n.inspect()
  print('PASS real SMS draft '+kind,flush=True)
finally:n.command('configure',**{'native.next_word_prediction':True,'native.suggest_clipboard_content':True})
