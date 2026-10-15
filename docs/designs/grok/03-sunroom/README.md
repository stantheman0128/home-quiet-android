# Sunroom 日光房

## Concept

Being home is a kind of weather. The top of the phone is a window: late-afternoon sky, a geometric sun, and you as a coral dot inside the fence. The bottom is a wooden sill where the controls sit — radius, Wi-Fi, the list, the log. Onboarding is the same window before you step in; the last control is a dark key on the sill.

Rounded, warm, and pictorial. Not glassmorphism: the panels are opaque paper on wood, and the only blur is the sky itself. Coral is the single accent, used the way a temperature is colored in a weather app.

This is far from the current pure-black hairline UI, and far from the other two proposals: no plastic hardware, no poster grid.

## References

- Apple Weather on a clear afternoon: the condition is the background, the number is the sentence
- Things 3: soft rows, one cheerful accent, nothing shouting
- Arc: a space with a color of its own, here the color of a room at 4pm
- A domestic window: sky above the sash, wood below it

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| Sky top | `#C5DFF6` | Window, morning-into-afternoon |
| Horizon | `#F8E6CC` | Lower sky |
| Sill | `#E8D2B6` | Wood frame and ground |
| Paper | `#FFFBF6` | Cards and rows |
| Ink | `#1C2430` | Text, primary button |
| Slate | `#5E6A78` | Secondary on sky |
| Warm mute | `#6A6258` | Secondary on wood and paper |
| Coral | `#E25B38` | Distance, toggles on, active tab |
| Sun | `#FFF6E4` → `#FFC56A` → `#F0993C` | The home disk |

The fence is a dashed ink ring. The sun disk is home. The coral dot sits at 42/120 of the radius, above center. The radius slider is a different control: 120 sits near the low end of 50–500, so the thumb is left, not at 35%.

## Type

- **Noto Sans CJK TC**, 400 and 700. No serif — the serif belongs to Poster Quiet.
- Display sizes are the word 在家 and the number 42. Everything else is 11–16px.
- **Noto Sans Mono CJK TC** only for package names, so the list still feels like software under the weather.

## Screens

- `onboarding.html` — 到家就安靜, three promises, step 01/06, 繼續 on the sill
- `status.html` — 在家, 42 公尺, radius 120, 家裡-5G on, permissions granted
- `apps.html` — ten apps, four on: Grok Bot, LINE, Slack, Gmail
- `log.html` — six cleared notes, 10月15日 afternoon, timeline on the sill
