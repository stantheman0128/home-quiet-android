# Field Instrument 現場儀器

## Concept

HomeQuiet as a handheld field meter. The phone is a warm plastic instrument: screws, a green LCD, orange LEDs, mixer latches, and a thermal-paper log. “在家” is not a status sentence. It is a lamp that is on. Distance is a segment readout. Apps are channels you arm. The log is a tape the device just printed.

This direction leaves the current black, hairline, Tesla-like UI completely. Light shell, one safety orange, tactile keys with hard 3px shadows, and monospace silk-screen labels beside the Chinese.

## References

- Teenage Engineering OP-1, CM-15, and TP-7: off-white shell, orange keys, playful engineering labels
- Braun ET66 and Dieter Rams: the product is the interface; one job per control
- Airport split-flap and handheld multimeters: the number is the whole screen
- Thermal receipt printers: the log as a perforated tape, not a feed of cards

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| Shell | `#E5DDD0` | Body plastic |
| Recess | `#D7CFC1` | Modules, search groove |
| Ink | `#171614` | Primary text, black keys |
| Mute | `#6F685E` | Silk-screen secondary |
| LCD | `#C5D4AA` → `#B5C898` | Display window |
| LCD ink | `#182212` | Digits and verdict |
| Signal | `#FF4D1C` | Armed, in-range, primary key |
| Paper | `#F7F3EA` | Log tape |
| Deep key | `#C4330C` | Orange key shadow |

Orange is rare on purpose: the home lamp, armed latches, the distance dot, and the onboarding key. Everything else is plastic, ink, or LCD green.

## Type

- **Noto Sans CJK TC** — Chinese interface, 400 and 700 only
- **Noto Sans Mono CJK TC** — distances, indexes `01–03`, package names, `HQ–1`, `MODE`, `DIST`
- The distance is a 7-segment LCD: lit segments for 42, unlit segments still visible
- English is silk-screen, small, tracked out. Chinese is the product language

## Screens

- `onboarding.html` — mode `01/06`, six lamps, spec plate, orange 繼續 key
- `status.html` — 在家, 42 公尺, radius fader at 120 (between 50 and 500), Wi-Fi latch, home fix
- `apps.html` — ten channel strips, four armed: Grok Bot, LINE, Slack, Gmail
- `log.html` — six cleared notes from the afternoon of 10月15日, newest first
