import type { Theme } from '../types';
import { makeMinimal, type MinimalSpec } from './minimal';
import type { Palette, Shape } from './trailmate';

/* Paper: the same console in daylight.

   A warm off-white page, white cards with soft corners, near-black figures at
   a regular weight. For a bright room, where a dark screen is a mirror. One
   colour to touch (a cobalt that is plainly not text), and the same three
   meanings as everywhere: green running, orange effort, red stop, each
   stepped dark enough to read on white. Built by makeMinimal
   (themes/minimal.ts). */

export const PALETTE: Palette = {
  bg0: '#F5F4F0',
  bg1: '#FFFFFF',
  ink: '#141413',
  inkDim: '#4A4A47',
  inkFaint: '#6E6E6A', // 4.7:1 on the canvas
  accent: '#2F5BEA',
  accentWarm: '#C9501C',
  good: '#15803D',
  alert: '#D4372C',
  trackLine: '#D9D7D0', // the hairline (ink at 14 %) made solid over the canvas
};

export const SHAPE: Shape = {
  radius: { control: 14, chip: 18, card: 16, choice: 20, floating: 16 },
  edgeWidth: 1,
  glowBlur: 0,
  family: '"Roboto", "Helvetica", "Arial", sans-serif',
};

const SPEC: MinimalSpec = {
  id: 'paper',
  scheme: 'light',
  font: {
    weightNumeral: 400,
    weightHeading: 600,
    weightLabel: 500,
    weightBody: 400,
    trackingLabel: '0.08em',
    trackingNumeral: '-0.02em',
  },
  /* TrailMate's workout hues, each stepped down until it holds on white as a
     bar or a word (3:1 or better on #FFFFFF). */
  workout: {
    easy: '#2B8F5F', tempo: '#B5477E', intervals: '#C8413A', hills: '#8C56A8',
    longRun: '#2D78B8', recovery: '#3B8562', threshold: '#B0652B', speed: '#A3322C',
  },
  /* The dataviz reference's first three light slots. Checked as a set against
     #F5F4F0, all pairs: colour-blind separation 9.2, normal-vision 24.0. The
     orange and aqua sit under 3:1 on this ground, which the chart answers the
     way the reference asks: every series is named and valued beside its line. */
  chart: { speed: '#2A78D6', hr: '#EB6834', incline: '#1BAF7A' },
};

export function makePaper(c: Palette = PALETTE, shape: Shape = SHAPE): Theme {
  return makeMinimal(SPEC, c, shape);
}

export const paper: Theme = makePaper();
