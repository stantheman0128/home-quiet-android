# Poster Quiet 海報安靜

## Concept

The screen is a printed poster, not a control panel. A red spine runs the full height with the product name set vertically, like a book that has been turned to face you. The state is a single word at display size — 在家 — and the distance is a red serif numeral that touches the margin. The bottom third is solid ink: coordinates, the radius stepper, permissions, and the index.

No cards, no pills, no switches. A setting is a square that is either filled red or empty. Navigation is four words. The current one is red on black.

This is the opposite of the current black luxury minimalism: gallery white, a printer’s red, and a Chinese serif loud enough to be the interface.

## References

- Josef Müller-Brockmann and the Swiss poster: one grid, one accent, type doing the work
- 聶永真 Nie Yongzhen: Taiwanese book covers where a character is the image
- El Lissitzky and constructivist print: black on red, red on black, no in-between
- Massimo Vignelli: the timetable as a composition, which is what the log is

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| Paper | `#F4F4F1` | Gallery ground |
| Ink | `#0C0C0C` | Text, rules, footer, empty squares |
| Signal | `#FF2B1A` | Spine, the 42, filled squares, active tab |
| Mute | `#5C5852` | Secondary sentences |
| Faint | `#8A867E` | Package names, reasons |

Red on paper is reserved for large type and squares (the small-text contrast is not good enough). Red on the black footer is the active tab, where the contrast holds. Rules are 1.5–3px, structural, never a hairline.

## Type

- **Noto Serif CJK TC, 700** — display: 在家, 42, 到家就安靜, times, coordinates
- **Noto Sans CJK TC, 700 / 400** — names, body, navigation
- **Noto Sans Mono CJK TC** — folio, step numbers, package names
- Tracking on display serif is slightly negative. Body Chinese is not tracked.

## Screens

- `onboarding.html` — step 01/06, two-line title, black bar 繼續
- `status.html` — 在家, 42 公尺 inside a 120 公尺 fence, stepper, 家裡-5G
- `apps.html` — 04/10, ten apps, square toggles; on: Grok Bot, LINE, Slack, Gmail
- `log.html` — six cleared notes, 10月15日, times in red serif
