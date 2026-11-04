# Remote profiles

A desktop on the same network can set a console up over adb: its look, the
order of the apps under APPS, which ways in the welcome screen offers, and the
console's own settings. Nothing in a profile reaches the belt.

## Asking

One broadcast per request. STRIDE has to be running.

```sh
adb shell am broadcast -a dev.stride.hud.REMOTE -p dev.stride.hud --es op get
adb shell am broadcast -a dev.stride.hud.REMOTE -p dev.stride.hud --es op set --es data <base64 JSON>
```

The answer is the broadcast's result data, base64 of a JSON object:

```
Broadcast completed: result=-1, data="eyJvayI6dHJ1ZSwi…"
```

`result=0` and no data means nothing answered: STRIDE isn't running.

| op | does | answers |
|---|---|---|
| `ping` | nothing | `{ok, version, session}` |
| `get` | reads | `{ok, schema: "stride.remote.v1", version, session, profile, settings, people, apps, devices, trailmate}` |
| `set` | stores and applies a profile | `{ok, profile}` (as cleaned), or `{ok: false, error}` |
| `reset` | forgets the profile; settings stay as they are | `{ok}` |

`session` is `welcome`, `warmup`, `running`, `paused`, `cooldown` or
`summary`. `settings` is the console's own value for each key a profile may
carry, defaults filled in, as text. `apps` is `[{pkg, name}]`, asked afresh,
without icons. `devices` and `trailmate` are what the Settings screen reads;
neither carries a token.

## The profile, `stride.profile.v1`

Every field is optional. Anything else is dropped.

```json
{
  "schema": "stride.profile.v1",
  "name": "Garage",
  "theme": {
    "colour": { "accent": "#6DA491", "bg0": "#001E20" },
    "radius": { "control": 8, "card": 12 },
    "font": { "family": "\"Roboto\", sans-serif" },
    "edgeWidth": 1,
    "glowBlur": 0
  },
  "apps": { "order": ["com.netflix.mediaclient"], "hidden": ["com.android.chrome"] },
  "welcome": { "ways": [{ "id": "workouts", "visible": true }, { "id": "quick", "visible": true }, { "id": "routes", "visible": false }] },
  "settings": { "units": "mi", "clock_24": "false", "sleep_min": "10" }
}
```

| | |
|---|---|
| `theme.colour` | Any of `bg0 bg1 ink inkDim inkFaint accent accentWarm good alert trackLine`, `#rrggbb` only. Every fill, edge and ring mixed from one is worked out again (`makeTrailmate`), and so are TrailMate's in-between teals (backdrop, keyboard, sleep clock, shadows) once the canvas or surface changes, and the hairline once the canvas or ink does. |
| `theme.radius` | `control chip card choice floating`, 0–40 px. |
| `theme.font.family` | Up to 120 characters, none of `; { } < > \`. |
| `theme.edgeWidth`, `glowBlur` | 0–4 px, 0–24 px. |
| `apps` | Package names, up to 100 each. Listed apps come first in that order, the rest after as before. |
| `welcome.ways` | `quick`, `routes`, `workouts`. Unlisted ones follow, shown. |
| `settings` | Any key `Settings.set` takes except `default_runner`, written as the **console's** value (`Settings.setConsole`). A runner's own value still wins for them. |

The profile is cleaned twice: in Kotlin before it is stored
(`RemoteProfile.sanitise`, to `files/remote-profile.json`) and in the page
before it is drawn (`lib/profile.ts`). The page asks for it before its first
frame (`Stride.profile()`), and Kotlin pushes a new one with
`window.applyProfile`.

## Safety

- **Only adb can ask.** The receiver is registered with
  `android.permission.DUMP`, which the shell holds and no installed app can,
  as with `KioskReceiver`. Whoever can send it already has the console.
- **Not under a runner.** `set` is refused while a run is under way: warming
  up, running, paused, cooling down, or the belt still winding down.
- **No way to lock out a run.** Hide every way in and the welcome screen
  offers all three.
- **No belt limits.** Speed and incline ranges come from the board and are
  never in a profile.
