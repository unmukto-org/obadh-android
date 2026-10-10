#!/usr/bin/env python3
"""Real native decoder/editor/touch regression on the existing opted-in QA emulator.
No new device image, corpus or library download. Never run on a personal device.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import time

spec = importlib.util.spec_from_file_location('touch', Path(__file__).with_name('test-native-touch.py'))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
n = t.n
appearance_spec = importlib.util.spec_from_file_location('appearance', Path(__file__).with_name('test-native-appearance.py'))
appearance = importlib.util.module_from_spec(appearance_spec)
appearance_spec.loader.exec_module(appearance)


def run():
    assert os.environ.get('ANDROID_SERIAL', '').startswith('emulator-'), 'Use an explicit QA emulator serial'
    size = re.search(r'Override size: (\d+x\d+)', n.shell('wm', 'size'))
    density = re.search(r'Override density: (\d+)', n.shell('wm', 'density'))
    rotation = n.shell('settings', 'get', 'system', 'user_rotation').strip()
    auto_rotate = n.shell('settings', 'get', 'system', 'accelerometer_rotation').strip()
    try:
        n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
        n.shell('settings', 'put', 'system', 'accelerometer_rotation', 0)
        n.shell('settings', 'put', 'system', 'user_rotation', 0)
        n.start('bn')
        n.command('configure')
        assert n.inspect()['controls']['bangla_gesture'], 'Enable Swipe typing and finish its download first'
        n.adb('logcat', '-c')
        n.command('gesture_dictionary')
        deadline = time.monotonic() + 10
        while True:
            lines = n.adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhGestureProbe:I', '*:S').splitlines()
            result = next((json.loads(line) for line in reversed(lines) if line.startswith('{')), None)
            if result is not None:
                assert result['valid'], result
                for roman, word in [('ami', 'আমি'), ('tumi', 'তুমি'), ('bhalo', 'ভালো'), ('valo', 'ভালো'), ('kemon', 'কেমন')]:
                    assert result[roman]['frequency'] > 0 and word in result[roman]['targets'], result
                break
            assert time.monotonic() < deadline, 'Native gesture model probe timed out'
            time.sleep(.1)
        print('PASS generated vocabulary parsed by actual native dictionary', flush=True)

        for roman, word in [('ami', 'আমি'), ('tumi', 'তুমি'), ('bhalo', 'ভালো'), ('valo', 'ভালো'), ('kemon', 'কেমন')]:
            n.start('bn')
            t.glide(roman)
            n.expect(word, 'Bangla swipe ' + roman)
            assert all(not re.search('[A-Za-z]', x) for x in n.inspect()['words']), n.inspect()

        n.start('bn')
        for roman in ('ami', 'bhalo', 'achi'):
            t.glide(roman)
        n.command('key', code=32)
        n.expect('আমি ভালো আছি ', 'Continuous Bangla swipe spacing')
        n.type_text('ami ')
        n.expect('আমি ভালো আছি আমি ', 'Phonetic taps immediately after glide')

        n.start('bn')
        t.glide('kemon')
        n.command('key', code=46)
        n.expect('কেমন।', 'Shared Bangla punctuation after glide')
        n.start('bn')
        t.glide('ami')
        t.tap(t.positions([-7])[-7])
        n.expect('', 'Backspace removes the whole swipe word')

        n.start('bn')
        t.glide('ami')
        t.tap(t.positions([-227])[-227])
        n.screen()
        assert n.inspect()['controls']['locale'] == 'en'
        t.glide('world')
        x, y = t.positions([32])[32]
        n.shell('input', 'swipe', x - 160, y, x + 160, y, 250)
        n.screen()
        assert n.inspect()['controls']['locale'] == 'bn'
        t.glide('tumi')
        n.command('key', code=32)
        n.expect('আমি world তুমি ', 'Globe/space swipe switching preserves bilingual glide text')

        # Exercise the real app switch and its private cross-process receiver.
        n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity', '-f', '0x10008000', '--es', 'screen', 'Gestures')
        appearance.choose('Swipe typing')
        for language in ('bn', 'en'):
            n.start(language)
            assert not n.inspect()['controls']['gesture'], n.inspect()
        n.shell('am', 'start', '-n', n.PACKAGE + '/.app.MainActivity', '-f', '0x10008000', '--es', 'screen', 'Gestures')
        appearance.choose('Swipe typing')
        assert not any(x.get('text') == 'Download swipe typing?' for x in n.screen().iter('node'))
        n.start('bn')
        t.glide('ami')
        n.expect('আমি', 'Shared switch off/on reuses decoder without restart or download')

        for field in ('password', 'email'):
            n.start('bn', field=field)
            assert not n.inspect()['controls']['bangla_gesture'], n.inspect()
            t.glide('ami')
            assert not re.search('[\u0980-\u09ff]', n.text()), n.inspect()
            print('PASS no Bangla glide conversion in ' + field, flush=True)
        n.start('bn', field='number')
        assert not n.inspect()['controls']['bangla_gesture'], n.inspect()
        print('PASS numeric field guard', flush=True)

        n.start('bn')
        n.command('configure', **{'native.always_incognito_mode': True})
        t.glide('tumi')
        n.expect('তুমি', 'Incognito retains model-only Bangla glide')
        assert n.inspect()['controls']['incognito'], n.inspect()
        n.command('configure')

        for code, field in [(-10002, 'one_handed'), (-109, 'floating')]:
            n.start('bn')
            n.command('key', code=code)
            try:
                n.screen()
                assert n.inspect()['controls'][field], n.inspect()
                t.glide('ami')
                n.expect('আমি', 'Bangla glide in ' + field)
            finally:
                n.command('key', code=code)
                n.screen()

        for tablet in (False, True):
            if tablet:
                n.shell('wm', 'size', '1800x2560')
                n.shell('wm', 'density', 320)
            for direction in (0, 1):
                n.shell('settings', 'put', 'system', 'user_rotation', direction)
                time.sleep(.5)
                n.start('bn')
                t.glide('tumi')
                n.expect('তুমি', ('Tablet' if tablet else 'Phone') + (' landscape' if direction else ' portrait') + ' glide')
        n.shell('wm', 'size', size.group(1) if size else 'reset')
        n.shell('wm', 'density', density.group(1) if density else 'reset')
        n.shell('settings', 'put', 'system', 'user_rotation', 0)
        n.start('en')
        t.glide('world')
        n.expect('world', 'English native decoder remains intact')

        timings = n.adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhGestureTiming:I', '*:S')
        rows = re.findall(r'decode_map_us=(\d+) thread=(.+)', timings)
        assert rows and all(thread != 'main' for _, thread in rows), timings
        samples = sorted(int(value) for value, _ in rows)
        print(f'Bangla decode + mapping: n={len(samples)} p50={samples[len(samples)//2]}us '
              f'p95={samples[min(len(samples)-1,len(samples)*95//100)]}us max={samples[-1]}us; all off main thread', flush=True)

        # Damage only the derived cache in the explicitly selected QA emulator.
        # Cold reopening must verify and repair it from the bundled signed asset.
        cached = n.shell('run-as', n.PACKAGE, 'ls', 'files/ObadhGesture').splitlines()
        files = [x for x in cached if re.fullmatch(r'[0-9a-f]{16}\.dict', x)]
        assert len(files) == 1, cached
        n.shell('am', 'force-stop', n.PACKAGE)
        n.shell('run-as', n.PACKAGE, 'sh', '-c', 'printf bad > files/ObadhGesture/' + files[0])
        # Android may choose a fallback IME when its selected service is force-stopped.
        n.shell('ime', 'set', n.PACKAGE + '/.keyboard.ObadhInputMethodService')
        n.start('bn')
        t.glide('ami')
        n.expect('আমি', 'Cold startup repairs corrupt derived cache')
        metadata = json.loads((Path(__file__).resolve().parent.parent / 'app/src/main/assets/ObadhGesture/metadata.json').read_text())
        actual = n.shell('run-as', n.PACKAGE, 'sha256sum', 'files/ObadhGesture/' + files[0]).split()[0]
        assert actual == metadata['sha256'], actual
    finally:
        n.shell('wm', 'size', size.group(1) if size else 'reset')
        n.shell('wm', 'density', density.group(1) if density else 'reset')
        n.shell('settings', 'put', 'system', 'user_rotation', rotation)
        n.shell('settings', 'put', 'system', 'accelerometer_rotation', auto_rotate)
        n.command('configure')
    print('Bangla native glide regressions passed.', flush=True)


if __name__ == '__main__':
    run()
