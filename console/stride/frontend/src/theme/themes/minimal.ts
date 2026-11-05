import { alpha, mix } from '../alpha';
import type { Theme, ThemeId } from '../types';
import type { Palette, Shape } from './trailmate';

/* The two minimal looks, Carbon (dark) and Paper (light), are built here.

   Their language is the same and deliberately spare: one flat canvas with no
   lift behind the hero, surfaces told apart by a hairline rather than by
   tone, one accent for everything that can be touched, and colour kept for
   what it means (running, effort, stop). No glow and no cast shadow on
   anything that sits on the page; only what floats over it (a menu) has one.

   What differs between the two is in their own files: the palette, the
   shape, the weight of the type, and the colours that have to be picked
   against a ground rather than worked out from it (workout types, the live
   chart's series). Everything that is a mix of the palette is worked out
   here, so a profile's colours carry through the same way they do in
   TrailMate (themes/trailmate.ts, docs/REMOTE.md). */

export interface MinimalSpec {
  id: ThemeId;
  /** Which way the ground runs: light ink on dark, or dark ink on light. */
  scheme: 'dark' | 'light';
  font: Pick<Theme['font'], 'weightNumeral' | 'weightHeading' | 'weightLabel' | 'weightBody' | 'trackingLabel' | 'trackingNumeral'>;
  workout: Theme['workout'];
  chart: Theme['chart'];
}

/** `#rrggbb` as the bare "r,g,b" the Settings skin mixes from. */
const rgb = (hex: string) => {
  const n = parseInt(hex.slice(1), 16);
  return `${(n >> 16) & 255},${(n >> 8) & 255},${n & 255}`;
};

export function makeMinimal(spec: MinimalSpec, c: Palette, shape: Shape): Theme {
  const dark = spec.scheme === 'dark';
  const surface = c.bg1;
  const glow = shape.glowBlur > 0;
  return {
    id: spec.id,
    colour: { ...c },
    role: {
      fill: surface,
      fillCard: surface,
      fillQuiet: 'transparent',
      fillFloating: alpha(surface, 0.98),
      /* A press is answered in the accent, on either ground: it is the one
         moment the page speaks back, and a grey would be missed mid-stride. */
      pressed: alpha(c.accent, dark ? 0.22 : 0.16),
      pressedCard: alpha(c.accent, dark ? 0.16 : 0.12),
      selected: alpha(c.accent, dark ? 0.16 : 0.12),
      active: alpha(c.good, dark ? 0.16 : 0.12),
      edge: alpha(c.ink, dark ? 0.16 : 0.14),
      edgeQuiet: alpha(c.ink, 0.08),
      scrim: alpha(c.bg0, 0.96),
      pulse: alpha(c.accent, 0.45),
      glow: glow ? alpha(c.accent, 0.45) : 'transparent',
      // Flat: no lift behind the hero.
      backdropInner: 'transparent',
      backdropMid: 'transparent',
      /* On the page nothing casts a shadow in the dark; on paper a card is
         white on off-white, and a breath of shadow is what lifts it. */
      shadowRaised: dark ? 'none' : `0 1px 2px ${alpha(c.ink, 0.06)}, 0 6px 16px ${alpha(c.ink, 0.05)}`,
      shadowFloating: dark
        ? '0 18px 48px rgba(0,0,0,0.6)'
        : `0 18px 48px ${alpha(c.ink, 0.16)}, 0 2px 6px ${alpha(c.ink, 0.08)}`,
      /* Over a film both are as TrailMate has them, whatever the ground:
         the picture underneath is the ground there, not the theme's. */
      fillOverApp: alpha('#000000', 0.55),
      inkOverApp: '#FFFFFF',
      /* Asleep is a dark room on either ground: a paper-white sleep screen
         would light it. */
      sleepBg: '#000',
      sleepClock: '#333333',
      sleepHint: '#1F1F1F',
    },
    font: { family: shape.family, ...spec.font },
    workout: { ...spec.workout },
    radius: { ...shape.radius },
    edgeWidth: shape.edgeWidth,
    settings: {
      bg: c.bg0,
      'bg-rgb': rgb(c.bg0),
      ink: c.ink,
      'ink-dim': c.inkDim,
      'ink-faint': c.inkFaint,
      accent: c.accent,
      'accent-rgb': rgb(c.accent),
      line: c.trackLine,
      'line-rgb': rgb(c.trackLine),
      'surface-rgb': rgb(surface),
      alert: c.alert,
      'alert-rgb': rgb(c.alert),
      // A warning's words: the alert pulled toward the ink, so it reads as text.
      'alert-ink': mix(c.alert, c.ink, dark ? 0.3 : 0.2),
      good: c.good,
      'good-rgb': rgb(c.good),
      warm: c.accentWarm,
      'warm-rgb': rgb(c.accentWarm),
      'kb-bg': mix(c.bg0, c.ink, 0.06),
      'backdrop-inner': 'transparent',
      'backdrop-mid': 'transparent',
      glow: glow ? `0 0 ${shape.glowBlur}px ${alpha(c.accent, 0.5)}` : 'none',
      font: shape.family,
      'w-light': String(spec.font.weightNumeral),
      'w-body': String(spec.font.weightBody),
      radius: `${shape.radius.control}px`,
      tracking: spec.font.trackingLabel,
      edge: `${shape.edgeWidth}px`,
    },
    glowBlur: shape.glowBlur,
    chart: { ...spec.chart },
  };
}
