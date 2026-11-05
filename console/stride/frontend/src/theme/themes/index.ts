import type { Theme, ThemeId } from '../types';
import * as carbon from './carbon';
import * as paper from './paper';
import { makeTrailmate, PALETTE, SHAPE, type Palette, type Shape } from './trailmate';

/* The looks a console can wear. Each is a palette and a shape of its own,
   and the way to build a whole theme from any palette and shape, so what a
   profile overrides (docs/REMOTE.md) is carried through the chosen look's
   mixes rather than TrailMate's. */

export interface Base {
  id: ThemeId;
  /** As a person would name it: on the console's Settings and in the builder. */
  label: string;
  palette: Palette;
  shape: Shape;
  make(c: Palette, shape: Shape): Theme;
}

export const BASES: Record<ThemeId, Base> = {
  trailmate: { id: 'trailmate', label: 'TrailMate', palette: PALETTE, shape: SHAPE, make: makeTrailmate },
  carbon: { id: 'carbon', label: 'Carbon', palette: carbon.PALETTE, shape: carbon.SHAPE, make: carbon.makeCarbon },
  paper: { id: 'paper', label: 'Paper', palette: paper.PALETTE, shape: paper.SHAPE, make: paper.makePaper },
};

export const THEME_IDS = Object.keys(BASES) as ThemeId[];

export const isThemeId = (v: unknown): v is ThemeId => typeof v === 'string' && Object.prototype.hasOwnProperty.call(BASES, v);
