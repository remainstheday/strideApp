import type { Profile } from '../lib/profile';
import { mix } from './alpha';
import { cssVariables, theme } from './tokens';
import { BASES } from './themes';
import type { Theme } from './types';

/** The theme a profile asks for: the look it names (`theme.base`; TrailMate
 *  when it names none) with its overrides, every mix of an overridden colour
 *  worked out again. An empty profile is TrailMate. */
export function themeFor(p: Profile): Theme {
  const t = p.theme || {};
  const base = BASES[t.base || 'trailmate'] || BASES.trailmate;
  const { palette: PALETTE, shape: SHAPE } = base;
  const given = t.colour || {};
  const palette = { ...PALETTE, ...given };
  /* The hairline is the ink at 20 % made solid over the canvas: a new
     canvas or ink without a hairline of its own gets one worked out. */
  if (!given.trackLine && (given.bg0 || given.ink)) palette.trackLine = mix(palette.bg0, palette.ink, 0.2);
  return base.make(
    palette,
    {
      radius: { ...SHAPE.radius, ...(t.radius || {}) },
      edgeWidth: t.edgeWidth ?? SHAPE.edgeWidth,
      glowBlur: t.glowBlur ?? SHAPE.glowBlur,
      family: t.font?.family || SHAPE.family,
    },
  );
}

/** Copy [from] into [to] in place, object by object: canvas code holds
 *  `theme` and `theme.colour` from import time, and must see the change. */
function assignDeep(to: Record<string, unknown>, from: Record<string, unknown>): void {
  for (const k of Object.keys(from)) {
    const v = from[k];
    const cur = to[k];
    if (v && typeof v === 'object' && !Array.isArray(v) && cur && typeof cur === 'object' && !Array.isArray(cur)) {
      assignDeep(cur as Record<string, unknown>, v as Record<string, unknown>);
    } else {
      to[k] = v;
    }
  }
}

/** Put a profile's look on screen: the live theme object and the stylesheet
 *  applyTokens installed. */
export function applyProfileTheme(p: Profile, doc: Document = document): void {
  assignDeep(theme as unknown as Record<string, unknown>, themeFor(p) as unknown as Record<string, unknown>);
  const style = doc.querySelector('style[data-theme]');
  if (style) {
    style.setAttribute('data-theme', theme.id);
    style.textContent = cssVariables(theme);
  }
}
