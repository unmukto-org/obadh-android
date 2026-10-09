#!/usr/bin/env python3
"""Prepare the pinned, isolated GPL HeliBoard prototype without a second SDK/history."""
import pathlib
import argparse
import json
import shutil
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--revision", help="Candidate commit SHA to verify before updating upstream.json")
parser.add_argument("--destination", type=pathlib.Path, help="Separate checkout for an upstream update trial")
options = parser.parse_args()
REVISION = options.revision or json.loads((ROOT / "integration/heliboard/upstream.json").read_text())["revision"]
if len(REVISION) != 40 or any(c not in "0123456789abcdef" for c in REVISION):
    raise ValueError("Use a full immutable Git commit SHA")
HOST = options.destination.resolve() if options.destination else ROOT / ".upstream/heliboard"
MARKER = HOST / ".obadh-upstream-revision"


def replace(path, before, after):
    text = path.read_text()
    if after in text:
        return
    if before in text:
        path.write_text(text.replace(before, after))
    elif after not in text:
        raise RuntimeError(f"Upstream integration point changed: {path}")


def main():
    if HOST.exists() and (not MARKER.exists() or MARKER.read_text().strip() != REVISION):
        raise RuntimeError("Existing upstream checkout has a different or unknown revision; move it aside first")
    if not HOST.exists():
        staged = HOST.with_name("heliboard-download")
        staged.mkdir(parents=True, exist_ok=False)
        try:
            url = f"https://codeload.github.com/HeliBorg/HeliBoard/tar.gz/{REVISION}"
            with urllib.request.urlopen(url, timeout=60) as response:
                with tarfile.open(fileobj=response, mode="r|gz") as archive:
                    for entry in archive:
                        parts = pathlib.PurePosixPath(entry.name).parts[1:]
                        if not parts or not entry.isfile() or ".." in parts:
                            continue
                        # The prototype needs English only. Do not download/install other language assets.
                        if "dicts" in parts and parts[-1] != "main_en-US.dict":
                            continue
                        destination = staged.joinpath(*parts)
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(archive.extractfile(entry).read())
            staged.rename(HOST)
            MARKER.write_text(REVISION + "\n")
        except BaseException:
            shutil.rmtree(staged, ignore_errors=True)
            raise

    main_dir = HOST / "app/src/main"
    # A tiny pristine source cache makes preparation deterministic even when an owned hook changes.
    # No Git history or duplicate project/build tree. Expected-string checks still fail on upstream drift.
    patched_sources = [
        "event/CombinerChain.kt", "latin/WordComposer.java", "latin/Suggest.kt",
        "latin/inputlogic/InputLogic.java", "latin/LatinIME.java", "latin/SuggestedWords.java",
        "latin/ClipboardHistoryManager.kt", "latin/database/ClipboardDao.kt",
        "keyboard/emoji/EmojiSearchActivity.kt", "latin/settings/SettingsValues.java",
        "latin/utils/InputTypeUtils.java", "keyboard/KeyboardId.kt", "latin/utils/PopupKeysUtils.kt",
        "latin/dictionary/DictionaryFactory.kt", "keyboard/KeyboardActionListenerImpl.kt",
        "latin/SystemBroadcastReceiver.java", "latin/settings/Defaults.kt",
        "keyboard/KeyboardLayoutSet.kt", "keyboard/internal/keyboard_parser/EmojiParser.kt",
        "latin/suggestions/SuggestionStripLayoutHelper.java",
        "latin/personalization/PersonalizationHelper.java", "latin/dictionary/ExpandableBinaryDictionary.java",
    ]
    def restore_source(relative):
        source = main_dir / "java/helium314/keyboard" / relative
        pristine = HOST / ".obadh-pristine" / relative
        if not pristine.exists():
            pristine.parent.mkdir(parents=True, exist_ok=True)
            url = f"https://raw.githubusercontent.com/HeliBorg/HeliBoard/{REVISION}/app/src/main/java/helium314/keyboard/{relative}"
            pristine.write_bytes(urllib.request.urlopen(url, timeout=30).read())
        shutil.copy2(pristine, source)
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        list(executor.map(restore_source, patched_sources))
    for dictionary in (main_dir / "assets/dicts").glob("*.dict"):
        if dictionary.name != "main_en-US.dict":
            dictionary.unlink()
    # Only English/Bangla interface translations. No downloaded dictionaries for other languages.
    for resources in (main_dir / "res").glob("values-*"):
        qualifier = resources.name.removeprefix("values-").split("-")[0]
        if qualifier.startswith("b+"):
            qualifier = qualifier.split("+")[1]
        if len(qualifier) in (2, 3) and qualifier.isalpha() and qualifier not in ("bn", "en"):
            shutil.rmtree(resources)
    for layout in (main_dir / "assets/layouts/main").iterdir():
        if layout.name != "qwerty.txt":
            layout.unlink()
    for locale in (main_dir / "assets/locale_key_texts").iterdir():
        if locale.stem not in ("en", "en-US", "bn", "bn-BD") and not locale.stem.startswith("more_popups_"):
            locale.unlink()
    # These are common Latin popup tables, not language packs.
    for common in ("more_popups_main.txt", "more_popups_more.txt", "more_popups_all.txt"):
        target = main_dir / "assets/locale_key_texts" / common
        if not target.exists():
            url = f"https://raw.githubusercontent.com/HeliBorg/HeliBoard/{REVISION}/app/src/main/assets/locale_key_texts/{common}"
            target.write_bytes(urllib.request.urlopen(url, timeout=30).read())
    khipro = main_dir / "assets/layouts/functional/functional_keys_khipro.json"
    khipro.unlink(missing_ok=True)
    (main_dir / "assets/bn-khipro.mim").unlink(missing_ok=True)

    for source in (ROOT / "integration/heliboard/src").rglob("*.kt"):
        destination = main_dir / "java" / source.relative_to(ROOT / "integration/heliboard/src")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
    for source in (ROOT / "integration/heliboard/tests").rglob("*.kt"):
        destination = HOST / "app/src/test/java" / source.relative_to(ROOT / "integration/heliboard/tests")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
    debug = HOST / "app/src/debug/java/helium314/keyboard/event"
    debug.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "integration/heliboard/debug/ObadhProbeActivity.kt", debug / "ObadhProbeActivity.kt")
    (HOST / "app/src/debug/AndroidManifest.xml").write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application>'
        '<activity android:name="helium314.keyboard.event.ObadhProbeActivity" android:exported="true" '
        'android:theme="@android:style/Theme.Material.Light.NoActionBar" />'
        '</application></manifest>')
    bridge = main_dir / "java/org/unmukto/obadh/engine/ObadhNative.kt"
    bridge.parent.mkdir(parents=True, exist_ok=True)
    for source in (ROOT / "engine/src/main/kotlin/org/unmukto/obadh/engine").glob("*.kt"):
        shutil.copy2(source, bridge.with_name(source.name))
    native = ROOT / "app/src/main/jniLibs/arm64-v8a/libobadh_jni.so"
    if not native.exists():
        raise RuntimeError("Build Obadh's native bridge first: scripts/build-rust-android.sh")
    destination = main_dir / "jniLibs/arm64-v8a/libobadh_jni.so"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(native, destination)

    # The shipped host supports only Obadh Bangla and Latin. Do not retain other
    # language engines just because their source is present in the upstream archive.
    chain = main_dir / "java/helium314/keyboard/event/CombinerChain.kt"
    original = ('        if (combiningSpec == "hangul")\n'
                '            mCombiners.add(HangulCombiner())\n'
                '        else if (combiningSpec == "bn_khipro")\n'
                '            mCombiners.add(BnKhiproCombiner())\n    }')
    intermediate = original.replace('        else if (combiningSpec == "bn_khipro")',
        '        else if (combiningSpec == "bn_obadh")\n            mCombiners.add(ObadhCombiner())\n'
        '        else if (combiningSpec == "bn_khipro")')
    minimal = ('        if (combiningSpec == "bn_obadh")\n'
               '            mCombiners.add(ObadhCombiner())\n    }')
    if intermediate in chain.read_text():
        replace(chain, intermediate, minimal)
    else:
        replace(chain, original, minimal)
    replace(main_dir / "java/helium314/keyboard/latin/dictionary/DictionaryFactory.kt",
            '        if (readOnlyBinaryDictionary.isValidDictionary) {\n'
            '            if (locale.language == "ko") {\n'
            '                // Use KoreanDictionary for Korean locale\n'
            '                return KoreanDictionary(readOnlyBinaryDictionary)\n'
            '            }\n            return readOnlyBinaryDictionary',
            '        if (readOnlyBinaryDictionary.isValidDictionary) {\n'
            '            return readOnlyBinaryDictionary')
    hardware = main_dir / "java/helium314/keyboard/keyboard/KeyboardActionListenerImpl.kt"
    replace(hardware,
            '        val event: Event\n'
            '        if (settings.current.mLocale.language == "ko") { // todo: this does not appear to be the right place\n'
            '            val subtype = keyboardSwitcher.keyboard?.mId?.subtype ?: RichInputMethodManager.getInstance().currentSubtype\n'
            '            event = HangulEventDecoder.decodeHardwareKeyEvent(subtype, keyEvent) {\n'
            '                getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)\n'
            '            }\n'
            '        } else {\n'
            '            event = getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)\n'
            '        }',
            '        val event = getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)')
    hardware.write_text(hardware.read_text().replace('import helium314.keyboard.event.HangulEventDecoder\n', ''))
    for unused in ("event/BnKhiproCombiner.kt", "event/HangulCombiner.kt",
                   "event/HangulEventDecoder.kt", "latin/dictionary/KoreanDictionary.java"):
        (main_dir / "java/helium314/keyboard" / unused).unlink(missing_ok=True)
    import runpy
    runpy.run_path(str(ROOT / "integration/heliboard/patches.py"))["apply"](main_dir, replace)

    # Standalone candidate builds must not compile tests for removed language engines.
    for test in (HOST / "app/src/test").rglob("*.kt"):
        if any(name in test.read_text() for name in ("HangulCombiner", "KhiproEngine", "KoreanDictionary")):
            test.unlink()
    replace(main_dir / "java/helium314/keyboard/latin/LatinIME.java",
            '        loadSettings();\n        mClipboardHistoryManager.onCreate();',
            '        helium314.keyboard.event.ObadhCombiner.warmUp();\n        loadSettings();\n        mClipboardHistoryManager.onCreate();')
    replace(main_dir / "java/helium314/keyboard/latin/SystemBroadcastReceiver.java",
            '    public static void toggleAppIcon(final Context context) {',
            '    public static void toggleAppIcon(final Context context) {\n'
            '        // Obadh always exposes its own settings launcher, including Android 8/9.\n'
            '        if (helium314.keyboard.latin.BuildConfig.APPLICATION_ID.startsWith("org.unmukto.obadh")) return;')
    replace(main_dir / "java/helium314/keyboard/latin/inputlogic/InputLogic.java",
            '&& "bn_khipro".equals(mWordComposer.getCombiningSpec())',
            '&& ("bn_khipro".equals(mWordComposer.getCombiningSpec()) || "bn_obadh".equals(mWordComposer.getCombiningSpec()))')
    defaults = main_dir / "java/helium314/keyboard/latin/settings/Defaults.kt"
    import re
    default_text = defaults.read_text()
    default_text, count = re.subn(r'    const val PREF_ADDITIONAL_SUBTYPES = .*?(?=    const val PREF_ENABLE_SPLIT_KEYBOARD)',
                                 '    const val PREF_ADDITIONAL_SUBTYPES = ""\n', default_text, flags=re.S)
    if count != 1:
        raise RuntimeError("Upstream additional-subtype defaults changed")
    defaults.write_text(default_text)
    replace(defaults, 'const val PREF_GESTURE_INPUT = true', 'const val PREF_GESTURE_INPUT = false')
    replace(main_dir / "java/helium314/keyboard/latin/LatinIME.java",
            'intent.setClass(LatinIME.this, SettingsActivity2.class);',
            'intent.setClassName(LatinIME.this, "org.unmukto.obadh.app.MainActivity");')
    replace(defaults, 'KeyboardActionListener.SwipeAction.MOVE_CURSOR.name',
            'KeyboardActionListener.SwipeAction.SWITCH_LANGUAGE.name')
    replace(defaults, 'const val PREF_ENABLED_SUBTYPES = ""',
            'const val PREF_ENABLED_SUBTYPES = "bn-BD${Separators.SET}CombiningRules=bn_obadh,KeyboardLayoutSet=MAIN:qwerty${Separators.SETS}en-US${Separators.SET}SupportTouchPositionCorrection,TrySuppressingImeSwitcher"')
    replace(main_dir / "res/xml/method.xml", 'CombiningRules=bn_khipro', 'CombiningRules=bn_obadh')
    replace(main_dir / "res/xml/method.xml", 'MAIN:qwerty|FUNCTIONAL:functional_keys_khipro', 'MAIN:qwerty')
    replace(main_dir / "res/values/strings.xml", '%s (Khipro)</string>', '%s (Obadh)</string>')
    # US English plus Obadh Bangla only; upstream's other layouts remain source-side for easy updates.
    import re
    method = main_dir / "res/xml/method.xml"
    text = method.read_text()
    text = re.sub(r"[ \t]*<subtype\b.*?/>", lambda match: match.group(0) if
                  'android:languageTag="en-US"' in match.group(0) or
                  'CombiningRules=bn_obadh' in match.group(0) else "", text, flags=re.S)
    method.write_text(text)
    import xml.etree.ElementTree as ET
    languages = [subtype.get("{http://schemas.android.com/apk/res/android}languageTag")
                 for subtype in ET.parse(method).getroot()]
    if sorted(languages) != ["bn-BD", "en-US"]:
        raise RuntimeError(f"Unexpected prototype languages: {languages}")

    gradle = HOST / "app/build.gradle.kts"
    replace(gradle, 'applicationId = "helium314.keyboard"', 'applicationId = "org.unmukto.obadh.prototype"')
    replace(gradle, 'minSdk = 21', 'minSdk = 26')
    replace(gradle, 'listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")', 'listOf("arm64-v8a")')
    replace(gradle, 'ndkVersion = "28.0.13004108"', 'ndkVersion = "27.0.12077973"')
    # Local testing only; publisher signing credentials are never generated or embedded.
    replace(gradle, '        release {\n            isMinifyEnabled = true',
            '        release {\n            if (project.hasProperty("debugSign")) signingConfig = signingConfigs.getByName("debug")\n            isMinifyEnabled = true')
    replace(gradle, 'HeliBoard_${defaultConfig.versionName}', 'ObadhPrototype_${defaultConfig.versionName}')
    # Keep upstream attribution on About, replace user-facing branding everywhere else.
    for strings in (main_dir / "res").glob("values*/strings*.xml"):
        strings.write_text(strings.read_text().replace("HeliBoard", "Obadh"))
    name = main_dir / "res/values/donottranslate.xml"
    name.write_text(name.read_text().replace("HeliBoard", "Obadh").replace("Obadh prototype", "Obadh"))
    for variant in ("debug", "debugNoMinify"):
        for xml in (HOST / f"app/src/{variant}/res").rglob("*.xml"):
            xml.write_text(xml.read_text().replace("HeliBoard", "Obadh"))
    # Attribution remains on About only. No upstream support/donation prompts elsewhere.
    for screen in (main_dir / "java/helium314/keyboard/settings").rglob("*.kt"):
        if screen.name != "AboutScreen.kt":
            screen.write_text(screen.read_text().replace('"HeliBoard', '"Obadh'))
    (main_dir / "res/values/obadh_attribution.xml").write_text(
        '<resources><string name="obadh_attribution">Bangla powered by Obadh Engine. '
        'Android keyboard foundation: HeliBoard / AOSP. GPLv3; upstream notices preserved.</string></resources>')
    replace(main_dir / "java/helium314/keyboard/settings/screens/AboutScreen.kt",
            'R.string.english_ime_name, R.string.app_slogan',
            'R.string.english_ime_name, R.string.obadh_attribution')
    for relative in ("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml",
                     "drawable/ic_launcher_foreground.xml", "drawable-nodpi/brand_icon.png",
                     "values/ic_launcher_background.xml"):
        target = main_dir / "res" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / "app/src/main/res" / relative, target)
    # CI resolves the SDK through ANDROID_HOME and has no local.properties.
    local_properties = ROOT / "local.properties"
    if local_properties.exists():
        shutil.copy2(local_properties, HOST / "local.properties")
    print(f"Prepared {HOST} at {REVISION}; ARM64 only, English dictionary only")
    print(f"Build: ./gradlew -p {HOST} --no-daemon --max-workers=4 :app:assembleDebug")


if __name__ == "__main__":
    main()
