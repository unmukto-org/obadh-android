#!/usr/bin/env python3
"""Native editor regression checks on the existing emulator, using debug-only probe components.
Run after :app:assembleDebug and adb install -r. No additional test dependencies or emulator images.
"""
import json
import hashlib
import shlex
import subprocess
import time
import pathlib
import re
import xml.etree.ElementTree as ET

PACKAGE = 'org.unmukto.obadh'
RECEIVER = PACKAGE + '/.debug.NativeKeyboardProbeReceiver'
ACTIVITY = PACKAGE + '/.debug.NativeKeyboardProbeActivity'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


def shell(*args):
    return adb('shell', shlex.join(str(arg) for arg in args))


def command(name, **extras):
    args = ['am', 'broadcast', '-n', RECEIVER, '--es', 'command', name]
    for key, value in extras.items():
        flag = '--ez' if isinstance(value, bool) else '--ei' if isinstance(value, int) else '--es'
        args.extend([flag, key, str(value).lower() if isinstance(value, bool) else str(value)])
    return shell(*args)


def screen():
    shell('uiautomator', 'dump', '/sdcard/obadh-probe.xml')
    return ET.fromstring(shell('cat', '/sdcard/obadh-probe.xml'))


def text():
    screen()  # Wait for editor/key delivery to become idle.
    # Accessibility normalizes newlines into spaces; assert the actual InputConnection text.
    return inspect()['editor']


def start(language='bn', field='text', initial=''):
    shell('am', 'start', '-n', ACTIVITY, '-f', '0x10008000', '--es', 'field', field, '--es', 'initial', initial)
    screen()
    shell('input', 'tap', '350', '180')
    screen()
    for _ in range(2):
        if 'mInputShown=true' in shell('dumpsys', 'input_method'):
            break
        shell('input', 'tap', '350', '180')
        screen()
    assert 'mInputShown=true' in shell('dumpsys', 'input_method'), 'Editor IME did not become visible'
    assert 'mCurMethodId=' + PACKAGE + '/.keyboard.ObadhInputMethodService' in shell('dumpsys', 'input_method'), 'Wrong IME is selected'
    command('language', language=language)


def type_text(value):
    # adb shell must receive its own quoted command: otherwise final spaces disappear.
    shell('am', 'broadcast', '-n', RECEIVER, '--es', 'command', 'type', '--es', 'text', value, '--el', 'interval', '1')


def expect(expected, label):
    actual = text()
    assert actual == expected, f'{label}: expected {expected!r}, got {actual!r}'
    print('PASS', label, flush=True)


def inspect():
    command('inspect')
    output = adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhProbeState:I', '*:S')
    return json.loads(next(line for line in reversed(output.splitlines()) if line.startswith('{')))


def run():
    shell('ime', 'set', PACKAGE + '/.keyboard.ObadhInputMethodService')
    start()
    dependency = (pathlib.Path(__file__).resolve().parent.parent / 'rust/obadh-jni/Cargo.toml').read_text()
    version = re.search(r'obadh_engine = \{ version = "=?([\d.]+)"', dependency).group(1)
    adb('logcat', '-c')
    command('engine_release_probe', version=version)
    deadline = time.monotonic() + 30
    while True:
        lines = adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhEngineReleaseProbe:I', '*:S').splitlines()
        result = next((json.loads(line) for line in reversed(lines) if line.startswith('{')), None)
        if result is not None:
            assert result.get('version') == version and result.get('abi') == 2, result
            assert result.get('emoticon_literals_preserved'), result
            print('PASS installed engine version, C ABI v2 and explicit emoticon alternatives', flush=True)
            break
        assert time.monotonic() < deadline, 'Engine release probe timed out'
        time.sleep(.25)
    command('configure', **{'obadh.auto_insert': False})
    type_text('ami ')
    expect('আমি ', 'Bangla composition and delimiter')
    assert inspect()['words'], 'No Bangla next-word suggestions'
    print('PASS Bangla next-word prediction', flush=True)

    start()
    type_text('computer ')
    expect('কম্পিউটার ', 'fast exact loanword with typo auto-insert off')
    start()
    type_text('office.')
    expect('অফিস।', 'loanword on Bangla punctuation')
    for roman, bangla in (('okay', 'ওকে'), ('amazon', 'অ্যামাজন'),
                          ('widget', 'উইজেট'), ('workflow', 'ওয়ার্কফ্লো')):
        start()
        type_text(roman + ' ')
        expect(bangla + ' ', 'engine 0.9.5 exact loanword ' + roman)
    start()
    type_text('manus ')
    expect('মানুস ', 'Bangla correction stays opt-in')
    start()
    command('configure', **{'obadh.auto_insert': True})
    type_text('manus ')
    expect('মানুষ ', 'calibrated Bangla correction')

    for lang in ('bn', 'en'):
        start(lang)
        command('configure', **{'obadh.shortcuts': '[{"t":"eml","e":"name@example.com"},{"t":"@@","e":"01700000000"}]'})
        type_text('eml ')
        expect('name@example.com ', lang + ' letter shortcut')
        start(lang)
        type_text('@@ ')
        expect('01700000000 ', lang + ' symbol shortcut')
        start(lang)
        command('configure', **{'obadh.shortcuts_enabled': False})
        type_text('eml ')
        expect('এম্ল ' if lang == 'bn' else 'eml ', lang + ' disabled shortcut')
        command('configure', **{'obadh.shortcuts_enabled': True})

        start(lang)
        command('configure', **{'obadh.pairs': True})
        type_text('(')
        expect('()', lang + ' paired bracket')
        type_text(')')
        expect('()', lang + ' step over closer')
        start(lang)
        type_text('(')
        command('key', code=-7)  # KeyCode.DELETE
        expect('', lang + ' pair deletion')
        start(lang)
        command('configure', **{'obadh.pairs': False})
        type_text('(')
        expect('(', lang + ' disabled pairing')
        command('configure', **{'obadh.pairs': True})

    start('en')
    type_text('hello ')
    expect('hello ', 'English native composing pipeline')
    start('bn', field='email')
    type_text('ami@example.com')
    expect('ami@example.com', 'Bangla-selected email remains literal English')
    start('bn', field='number')
    type_text('123')
    expect('123', 'native numeric field stays ASCII')
    start()
    type_text('123.')
    expect('১২৩।', 'Bangla digits and dari in text')
    start()
    type_text('tqq')
    command('key', code=-7)
    expect('ত', 'atomic chandrabindu deletion')
    command('key', code=-7)
    expect('', 'delete final Roman input')

    start()
    type_text('bhalObasa')
    screen()
    words = inspect()['words']
    assert inspect()['emojis'], words
    print('PASS Bangla inline emoji candidates', flush=True)
    emoji_index = inspect()['words'].index(inspect()['emojis'][0])
    picked = inspect()['emojis'][0]
    command('pick', index=emoji_index)
    expect(picked, 'emoji replaces its Bangla query')
    start('en')
    type_text('heart')
    screen()
    assert inspect()['emojis'], inspect()
    print('PASS English inline emoji candidates', flush=True)

    for language, typed, full_stop in [('bn', 'ami', 'আমি। '), ('en', 'hello', 'hello. ')]:
        start(language)
        type_text(typed + '  ')
        expect(full_stop, language + ' native double-space full stop')
        start(language)
        command('configure', **{'native.use_double_space_period': False})
        type_text(typed + '  ')
        expect(('আমি' if language == 'bn' else 'hello') + '  ', language + ' disabled double-space full stop')
        command('configure')

    for language, initial, at, inserted, expected in [
        ('bn', 'আমি তুমি', 4, 'a', 'আমি আতুমি'),
        ('bn', 'আমি তুমি', 1, 'a', 'আআমিমি তুমি'),
        ('en', 'hello world', 6, 'a', 'hello aworld'),
    ]:
        # Exact cursor edits must preserve all text outside the newly inserted composition.
        start(language, initial=initial)
        command('selection', position=at)
        screen()
        type_text(inserted)
        actual = text()
        if language == 'bn':
            assert actual[:at] == initial[:at] and actual.endswith(initial[at:]), (initial, at, actual)
        else:
            assert actual == expected, actual
        print('PASS', language, 'cursor edit preserves surrounding text at', at, flush=True)

    for language in ('bn', 'en'):
        start(language, field='search')
        command('configure', **{'obadh.return_actions': True})
        type_text('office' if language == 'bn' else 'hello')
        command('key', code=10)
        expect('অফিস' if language == 'bn' else 'hello', language + ' search action commits word')
        start(language, field='search')
        command('configure', **{'obadh.return_actions': False})
        type_text('office' if language == 'bn' else 'hello')
        command('key', code=10)
        expect(('অফিস' if language == 'bn' else 'hello') + '\n', language + ' disabled return action inserts newline')
        command('configure')

    # One native settings snapshot, applied to both active languages.
    toggles = {
        'popup_on': 'preview', 'show_hints': 'hints', 'sound_on': 'sound',
        'vibrate_on': 'vibration', 'use_double_space_period': 'double_space',
        'delete_swipe': 'delete_swipe', 'enable_clipboard_history': 'clipboard',
    }
    for language in ('bn', 'en'):
        start(language)
        for enabled in (False, True):
            command('configure', **{'native.' + key: enabled for key in toggles},
                    **{'native.horizontal_space_swipe': 'SWITCH_LANGUAGE' if enabled else 'NONE',
                       'native.vertical_space_swipe': 'TOUCHPAD_MODE' if enabled else 'NONE'})
            screen()
            state = inspect()['controls']
            assert all(state[field] == enabled for field in toggles.values()), state
            assert state['space_horizontal'] == ('SWITCH_LANGUAGE' if enabled else 'NONE'), state
            assert state['space_vertical'] == ('TOUCHPAD_MODE' if enabled else 'NONE'), state
            print('PASS', language, 'shared native controls', enabled, flush=True)
        command('configure')

    # Personal OOV predictions must reach the real strip, not merely persist to disk.
    learned_word = None
    for _ in range(8):
        start('bn')
        type_text('amar obadhnamoporikkha ')
        learned_word = text().split()[-1]
    start('bn')
    type_text('amar ')
    screen()
    assert learned_word in inspect()['words'], (learned_word, inspect())
    print('PASS personal Bangla prediction reaches native strip', flush=True)
    try:
        for key in ('always_incognito_mode', 'use_personalized_dicts'):
            command('configure', **{'native.' + key: key == 'always_incognito_mode'})
            start('bn')
            type_text('amar ')
            screen()
            assert learned_word not in inspect()['words'], (learned_word, inspect())
            print('PASS Bangla model-only prediction', key, flush=True)
            command('configure', **{'native.always_incognito_mode': False, 'native.use_personalized_dicts': True})
    finally:
        command('configure', **{'native.always_incognito_mode': False})

    # Literal selection and remaining feature regressions.
    start('bn')
    command('configure', **{'obadh.auto_insert': True})
    type_text('manus')
    screen()
    state = inspect()
    assert state['labels'][0] == '"মানুস"', state
    command('pick', index=0)
    type_text(' ')
    expect('মানুস ', 'literal candidate is selectable')
    start('bn')
    type_text('manus ')
    expect('মানুস ', 'manual spelling protection persists')
    command('configure')

    for language in ('bn', 'en'):
        start(language)
        command('configure', **{'obadh.volume_cursor': True})
        type_text('ami' if language == 'bn' else 'hello')
        command('key', code=32)
        shell('input', 'keyevent', '25')
        type_text('a')
        expect('আমিআ ' if language == 'bn' else 'helloa ', language + ' volume cursor insertion')
        command('configure')

    start('bn')
    fixture = 'Obadh integration clipboard fixture ' + str(time.time_ns())
    command('clipboard', text=fixture)
    screen()
    # Adding a clip may expire old entries; test its presence, not a monotonic count.
    assert hashlib.sha256(fixture.encode()).hexdigest() in inspect()['clip_hashes'], inspect()
    print('PASS native clipboard collection', flush=True)
    command('clipboard', text='Sensitive integration fixture', sensitive=True)
    screen()
    assert hashlib.sha256(b'Sensitive integration fixture').hexdigest() not in inspect()['clip_hashes'], inspect()
    start('bn', field='password')
    command('clipboard', text='Password integration fixture')
    screen()
    assert hashlib.sha256(b'Password integration fixture').hexdigest() not in inspect()['clip_hashes'], inspect()
    print('PASS clipboard excludes sensitive and password fields', flush=True)

    start('bn')
    type_text('ami ')
    command('language', language='en')
    type_text('hello ')
    command('language', language='bn')
    type_text('tumi ')
    expect('আমি hello তুমি ', 'language switches retain text without stale candidates')

    for query in ('heart', 'ভালোবাসা', 'bhalObasa'):
        command('emoji', query=query)
        output = adb('logcat', '-d', '-v', 'raw', '-s', 'ObadhProbeEmoji:I', '*:S')
        found = json.loads(next(line for line in reversed(output.splitlines()) if line.startswith('[')))
        assert found, query
        print('PASS emoji search', query, flush=True)

    # Restore canonical user configuration (probe overrides never change the app settings).
    command('configure')
    print('Native editor regressions passed.', flush=True)


if __name__ == '__main__':
    run()
