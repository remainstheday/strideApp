# NordicTrack X24 (Gen 7)

An incline trainer with a 24-inch console. Model `NTL39225` (US) /
`NTL39225-INT`. The [X22i](nordictrack-x22i-gen7.md) this project first ran
on reports `ETNT39225-INT` from its board, the X24's own model number, so
the two look like one platform under two screens; everything in
[gen7.md](gen7.md) is expected to hold here.

Two kinds of fact are below and they are kept apart: what the console itself
answered over adb on 04/11/2026, and what NordicTrack and reviewers publish.
Nothing here was measured on the belt.

## The console, as it answers

Read with `adb shell` (getprop, `wm`, `dumpsys`, `/proc`), STRIDE running.

| | |
|---|---|
| Android | 13 (SDK 33), security patch 2025-09-05 |
| System version | `EKX1_20260630` |
| Console hardware | `EwayMediatekXenon1`, brand `iFit_Embedded`; MediaTek MT8390 (platform `mt8188`) |
| CPU | 8 cores, arm64: 6 × Cortex-A55, 2 × Cortex-A78 |
| Memory | 4 GB |
| Storage | 23 GB for apps and data, 19 GB free |
| Screen | 1920 × 1080 at 60 Hz, density 160 (one CSS pixel a screen pixel), about 92 pixels an inch |
| Touch | Multi-touch, five points or more |
| Wi-Fi | Wi-Fi 5 (802.11ac), linked at 780 Mbps on 5 GHz |
| Bluetooth | Classic and LE. Profiles enabled: A2DP, AVRCP, hands-free client, HID host and device, GATT |
| BLE peripheral | Yes: the adapter is named `STRIDE Treadmill`, which only [FTMS](../console/stride/app/src/main/java/dev/stride/hud/Ftms.kt) advertising sets |
| Sensors | Accelerometer, ambient light, proximity |
| Camera, microphone | Both declared by Android (`camera.level.full`, `microphone`). Not checked whether a camera is fitted |
| Audio out | Speaker, with HDMI, wired and USB headset routes declared |
| Motor board | USB `213c:0003`, "iFIT-LargeX": the same board as the X22i, so [FitPro 2](../protocol/FITPRO2_PROTOCOL.md) |
| ADB | Wireless debugging, paired |

## The machine, as published

| | | Source |
|---|---|---|
| Speed | 0–12 mph (US); 0–20 km/h (INT). The X22i's board reports 1.5–20 km/h | NordicTrack; fitshop.co.uk |
| Speed limit by grade | 8 mph from 16 %, 6 mph from 25 % and at full decline | TreadmillReviewGuru |
| Incline | −6 % to +40 % (measured by a reviewer: −4.9 % to 39.4 %) | NordicTrack; TreadmillReviewGuru |
| Motor | 4.25 CHP | fitshop.co.uk; TreadmillReviewGuru |
| Deck | 22 × 60 in | TreadmillReviewGuru |
| Runner weight | 400 lb / 182 kg | Owner's manual |
| Footprint, weight | 178 × 98 cm; 343 lb | Owner's manual |
| Fan | One, several speeds and an automatic mode that follows the belt | Owner's manual |
| Speakers | 2 × 2 in | fitshop.co.uk |
| Heart rate | Any Bluetooth Smart heart rate monitor. The manual mentions no pulse grips | Owner's manual |
| Headphones | Bluetooth audio out | Owner's manual |
| USB-C port | Charging only | Owner's manual |
| ANT+, FTMS, GymKit | None as sold. FTMS is STRIDE's | Owner's manual; by absence |
| Console keys | Speed and incline up and down, start, stop, fan, volume. Reviews disagree on whether there are quick keys | Owner's manual |

The owner's manual is the INT edition, part 455068 R0924A.

## What that means for STRIDE

- **Heart rate is a strap or a watch.** With no grips, the board's pulse
  field stays at zero. A watch that broadcasts heart rate pairs as a strap
  ([SYNC.md](../docs/SYNC.md)).
- **The radio can be found by other devices**, so FTMS works and a phone can
  follow the run live.
- **There is room.** Four gigabytes and an A78 pair run a streaming app under
  the run without trouble; Plex is installed beside STRIDE on this one.
- **The screen is half as wide again as the panel the page was drawn for.**
  The page fills it all the same: its screens are docked to the edges of
  whatever view they are given, and the run screen's top strip opens out past
  1500 pixels (`MetricStrip.module.css`). Type keeps its pixel sizes, so on
  this panel the figures are no bigger than on a C 1750 and there is more
  room between them. Checked in a browser at 1920 × 1080 in all three looks;
  not yet looked at on the console itself.

## How far it got

- [x] ADB: Wireless debugging pairing
- [x] STRIDE installed (0.8.0, 29/10/2026) and running
- [x] *Display over other apps* granted
- [ ] The rest of the list in [TEMPLATE.md](TEMPLATE.md): not recorded here.
      The owner runs on it; the steps were not written down.

## Not known yet

- What the board reports for its model string and its speed and incline
  ranges. STRIDE logs both when it claims the board.
- Whether the fan is wired the way the X22i's is.
- Whether a camera is fitted behind the bezel.

## Read by

The owner, with Claude Code, 04/11/2026, over adb, read-only.
