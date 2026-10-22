# Switch HOME menu: reference notes

Clean-room. Nothing here was decrypted or copied from Nintendo firmware. The `qlaunch` applet
(title 0100000000001000) is encrypted per-console, and no keys or firmware dumps were used.
Every number was measured from public 1080p HDMI captures with `tools/measure.py` and
`tools/track.py`. Footage and frames live in `re/`, which is gitignored and never committed.

| Source | Use |
|---|---|
| YouTube `m5If4INNlj4`: firmware 11.0 walkthrough, 1920x1080p60, dark theme | Layout, colours, highlight pulse |
| YouTube `rjitwSx80Ig`: older firmware, light theme, Spanish | Light palette, empty slots, title marquee |

All coordinates are in a 1920x1080 frame.

## Background and chrome

| Item | Dark | Light |
|---|---|---|
| Background | `#2A2C29` | `#EBEBEB` |
| Round-button fill | `#4D4F4C` | `#FFFFFF` + soft 1-2 px shadow |
| Status / hint text | `#FFFFFF` | `#3A3A3A` |
| Bottom rule | `#C1C3C0`, y 970-972 (3 px), x 44-1874 | `#3A3A3A`, 1-2 px |
| Title text (selected software) | `#2FB3E6` | `#1FB6AF` (older firmware teal) |
| Empty slot | n/a | fill `#EDEDED`, 2 px lighter bevel `#F5F5F5` |

## Status row

- Avatars are circles of diameter 91, pitch 108, first centre (135, 96). Each has a ~4 px darker
  ring. The online dot (teal `#12F8C4`, r≈8) sits at the avatar's upper right (+33, -32).
  A green "5" friend count sits above the first avatar.
- The clock is right-aligned. "11:27" digits run from x 1398 to 1512, with cap height ≈ 36
  (≈ 46 px font). "AM" is ≈ 60% size on the same baseline (y ≈ 113).
- The Wi-Fi glyph spans x 1595-1638, centre y 97.
- Battery: "100%" (the % in small caps) runs to 1760. The battery outline spans x 1770-1825 and
  is 28 tall: a 3 px stroke, a nub on the right, solid white fill.

## Software row

- Square tiles: 384 px with a 20 px gap (pitch 404), top y 290 (bottom 674). The first tile's
  art starts at x 162; tiles bleed off the right edge.
- Selection frame: the outer edge sits 14 px outside the art (x 146..557, y 275..691), with a
  7 px pulsing stroke, then a 5 px dark gap (`#081B29` dark, white in light), then the art.
  Corners have ~2 px radius.
- Title: a cyan software-type badge (40x46, with a white notch chevron at the bottom) at x 146,
  y 216-262, then the title at x 208. Font ≈ 50 px, baseline ≈ 258. Long titles marquee-scroll
  (light-theme capture: "...: Breath of the Wild    The Legend").
- Light theme draws empty slots up to the 12 visible-software limit. Dark theme on 11.0 shows a
  full row, so there are no empty slots in that capture.
- After the last software, the console puts a round "All Software" button. Not in these
  captures; built from the documented behaviour.

## Highlight pulse (tile frame and button ring share it)

Sampled at a fixed ring pixel at 60 fps (`track.py` / ad-hoc probe):

- Period **1.00 s**.
- Dark ↔ light: `#338CAF` ↔ `#9AF1EF`. It holds ~0.15-0.2 s at each extreme and ramps over
  ~0.35 s. Modelled as `smoothstep` applied to a cosine.
- Light theme: teal `#2DE4D6`, pulsing toward `#D3FEFF`.

## Round buttons (firmware 11 order)

Diameter 128, centre y 818, centres x 474, 635, 797, 959, 1121, 1283, 1445 (pitch 161.8).

| Button | Dark icon | Light icon | Android mapping |
|---|---|---|---|
| Online (solid red `#CF0514` disc) | white | white | Google Play Games, if installed; else hidden |
| News | `#E75A40` | `#FF3953` | Notification shade |
| Shop | `#F4AD17` | `#F59F14` | Play Store |
| Album | `#3498EA` | `#1677FA` | Gallery (`CATEGORY_APP_GALLERY`) |
| Controllers | `#D8DAD7` | `#737575` | Bluetooth settings |
| System Settings | `#D8DAD7` | `#737575` | Android Settings |
| Sleep Mode | `#D8DAD7` | `#737575` | Power options sheet |

- Selected ring: radius 78 (outer), stroke 8, a 5 px gap to the 64 px disc. Same pulse colours.
- The selected label is centred under the button, ≈ 36 px font, text box y 905-943, same colour
  as the title.
- The "new" dot (teal `#12F8C4`, r 6) sits at the button's upper right (+57, -56); it's on News
  in the capture.
- Icons (≈ 64 px box) were redrawn as original vector glyphs, not traced from Nintendo artwork.
  The Online button uses a neutral globe glyph instead of Nintendo's logo.

## Hint bar

- Controller glyph at x 104-165, centre y 1028. The player-LED squares above it (4 × 8 px,
  first lit `#7FD321`) are at y 993.
- Hints are right-aligned, ending at x 1826. Each is a filled 38 px circle glyph (+ or A) then
  the label (≈ 40 px font), 30 px between the circle and its label and 60 px between hints.
- Software selected: "+ Options", "A Start". Button selected: "A OK".

## Mobile adaptations (deliberate deviations)

- Layout is computed in a virtual space of `1920 × (H/s)` at scale `s = min(H/1080, W/1240)`.
  Landscape phones get the exact frame plus extra width (the row bleeds further). In portrait
  the 730 px row+buttons block sits 62% of the way down the spare height, which puts it in
  thumb reach. Status and hints pin to the edges.
- Portrait has no console equivalent. The empty top half gets a large thin clock and date,
  left-aligned with the first tile, and the status-row clock is dropped so there's only one.
- Tap a tile to select it; tap the selected tile to launch it. Long-press, or the + hint, opens
  Options. Swipe the row to scroll.
- 12 recent/first apps fill the row, then All Software, as on console (12-icon limit, 10.0+).
- Sounds are original, synthesized at runtime (tick, select, bump). They respect the system
  "Touch sounds" setting.
