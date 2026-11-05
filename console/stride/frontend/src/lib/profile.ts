/* A console profile, sent from a desktop over adb (docs/REMOTE.md) and
   handed to the page by Kotlin, already cleaned (RemoteProfile.kt). Cleaned
   again here: the page writes these values into a stylesheet, and an older or
   hand-edited profile must not be able to break the screen. */

import type { Palette } from '../theme/themes/trailmate';
import type { ThemeId } from '../theme/types';

export type WayId = 'quick' | 'routes' | 'workouts';
export const WAYS: readonly WayId[] = ['quick', 'routes', 'workouts'];

export interface Profile {
  name?: string;
  theme?: {
    /** Which look the rest is laid over: TrailMate when absent. */
    base?: ThemeId;
    colour?: Partial<Palette>;
    radius?: Partial<Record<'control' | 'chip' | 'card' | 'choice' | 'floating', number>>;
    font?: { family?: string };
    edgeWidth?: number;
    glowBlur?: number;
  };
  apps?: { order?: string[]; hidden?: string[] };
  welcome?: { ways?: { id: WayId; visible: boolean }[] };
}

const HEX = /^#[0-9a-fA-F]{6}$/;
const PACKAGE = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;
const COLOURS: (keyof Palette)[] = ['bg0', 'bg1', 'ink', 'inkDim', 'inkFaint', 'accent', 'accentWarm', 'good', 'alert', 'trackLine'];
const RADII = ['control', 'chip', 'card', 'choice', 'floating'] as const;
/** The looks a profile may name. Spelled out, not read from the themes: this
 *  file is the gate, and must not grow because a theme file did. */
const BASES: readonly ThemeId[] = ['trailmate', 'carbon', 'paper'];

const isObj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);

function num(v: unknown, min: number, max: number): number | undefined {
  const n = typeof v === 'number' ? v : typeof v === 'string' ? parseFloat(v) : NaN;
  return isFinite(n) ? Math.min(max, Math.max(min, n)) : undefined;
}

function pkgs(v: unknown): string[] | undefined {
  if (!Array.isArray(v)) return undefined;
  const out: string[] = [];
  for (const p of v) if (typeof p === 'string' && PACKAGE.test(p) && out.indexOf(p) < 0 && out.length < 100) out.push(p);
  return out;
}

/** Anything to a Profile; the worst input gives `{}`. */
export function cleanProfile(raw: unknown): Profile {
  if (!isObj(raw)) return {};
  const p: Profile = {};
  if (typeof raw.name === 'string') p.name = raw.name.slice(0, 60);
  const t = raw.theme;
  if (isObj(t)) {
    const theme: NonNullable<Profile['theme']> = {};
    // TrailMate is what an absent base means, so it is never written down.
    if (typeof t.base === 'string' && t.base !== 'trailmate' && BASES.indexOf(t.base as ThemeId) >= 0) theme.base = t.base as ThemeId;
    if (isObj(t.colour)) {
      const colour: Partial<Palette> = {};
      for (const k of COLOURS) {
        const v = t.colour[k];
        if (typeof v === 'string' && HEX.test(v)) colour[k] = v.toUpperCase();
      }
      theme.colour = colour;
    }
    if (isObj(t.radius)) {
      const radius: NonNullable<NonNullable<Profile['theme']>['radius']> = {};
      for (const k of RADII) {
        const v = num(t.radius[k], 0, 40);
        if (v !== undefined) radius[k] = v;
      }
      theme.radius = radius;
    }
    if (isObj(t.font) && typeof t.font.family === 'string' && t.font.family.trim()
      && !/[;{}<>\\\u0000-\u001f]/.test(t.font.family)) {
      theme.font = { family: t.font.family.trim().slice(0, 120) };
    }
    const edge = num(t.edgeWidth, 0, 4);
    if (edge !== undefined) theme.edgeWidth = edge;
    const blur = num(t.glowBlur, 0, 24);
    if (blur !== undefined) theme.glowBlur = blur;
    p.theme = theme;
  }
  if (isObj(raw.apps)) p.apps = { order: pkgs(raw.apps.order) || [], hidden: pkgs(raw.apps.hidden) || [] };
  if (isObj(raw.welcome) && Array.isArray(raw.welcome.ways)) {
    const ways: { id: WayId; visible: boolean }[] = [];
    for (const w of raw.welcome.ways) {
      if (isObj(w) && WAYS.indexOf(w.id as WayId) >= 0 && !ways.some((x) => x.id === w.id)) {
        ways.push({ id: w.id as WayId, visible: w.visible !== false });
      }
    }
    p.welcome = { ways };
  }
  return p;
}

/** The apps under APPS as the profile has them: hidden ones gone, listed
 *  ones first in its order, the rest after in the order they came. */
export function arrangeApps<T extends { pkg: string }>(apps: T[], p: Profile): T[] {
  const hidden = p.apps?.hidden || [];
  const order = p.apps?.order || [];
  const shown = apps.filter((a) => hidden.indexOf(a.pkg) < 0);
  const rank = (a: T) => {
    const i = order.indexOf(a.pkg);
    return i < 0 ? order.length : i;
  };
  // Array.prototype.sort is stable from Chromium 70, so ties keep their order.
  return shown.slice().sort((a, b) => rank(a) - rank(b));
}

/** The welcome screen's ways in, in the profile's order, hidden ones out.
 *  Never none: a console with every way hidden would have no way to start a
 *  run, so then it offers all of them. */
export function arrangeWays(p: Profile): WayId[] {
  const listed = p.welcome?.ways || [];
  const seen = listed.map((w) => w.id);
  const all = listed.concat(WAYS.filter((id) => seen.indexOf(id) < 0).map((id) => ({ id, visible: true })));
  const shown = all.filter((w) => w.visible).map((w) => w.id);
  return shown.length ? shown : WAYS.slice();
}
