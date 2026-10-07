# README assets

Three visuals, all from a physical device (Xiaomi, Android 12, dark appearance), debug
build with the debug launch extras unused.

| File | Provenance |
|---|---|
| `typing.gif` | Screen recording in Google Keep, typing `Amar sOnar bangla ami tOmay bhalObasi` with taps driven by `adb shell input tap`. Full frame, status bar included. |
| `keyboard.png` | Screenshot of the same note after typing: the ribbon shows suggestions and an inline emoji. |
| `app.png` | The containing app's welcome step and settings, side by side with a transparent gutter. |

## Regenerating

```bash
adb shell screenrecord --time-limit 25 /sdcard/rec.mp4 && adb pull /sdcard/rec.mp4
ffmpeg -ss 0.5 -i rec.mp4 -vf "scale=420:-1:flags=lanczos,fps=12,\
split[s0][s1];[s0]palettegen=max_colors=128[p];[s1][p]paletteuse=dither=bayer:bayer_scale=4" \
  typing.gif

adb exec-out screencap -p > shot.png
ffmpeg -i shot.png -vf scale=520:-1 keyboard.png

ffmpeg -i welcome.png -i settings.png -filter_complex \
  "[0]scale=520:-1,format=rgba,pad=560:ih:0:0:color=0x00000000[a];\
   [1]scale=520:-1,format=rgba[b];[a][b]hstack=inputs=2" \
  -frames:v 1 -update 1 app.png
```

The welcome step shows only on a fresh install (`setup_completed` false); the settings
screenshot needs Obadh selected as the active keyboard, or a banner appears at the top.
