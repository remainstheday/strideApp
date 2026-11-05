import type { Theme } from '../types';
import { makeMinimal, type MinimalSpec } from './minimal';
import type { Palette, Shape } from './trailmate';

/* Carbon: an instrument in a dark room.

   Near-black, off-white figures set light and large, labels small and
   tracked wide, corners all but square. One colour to touch (a lime that
   holds against black at any size), and three that mean something: green for
   a belt that is running, orange for effort, red for stop. Nothing else on
   the screen has a hue. Built by makeMinimal (themes/minimal.ts). */

export const PALETTE: Palette = {
  bg0: '#0A0A0B',
  bg1: '#151517',
  ink: '#F5F5F3',
  inkDim: '#B4B4B0',
  inkFaint: '#7C7C78', // 4.7:1 on the canvas
  accent: '#C6F24E',
  accentWarm: '#FF8A3D',
  good: '#4ADE80',
  alert: '#FF5C5C',
  trackLine: '#2A2A2D', // the hairline (ink at 14 %) made solid over the canvas
};

export const SHAPE: Shape = {
  radius: { control: 2, chip: 2, card: 2, choice: 2, floating: 4 },
  edgeWidth: 1,
  glowBlur: 0,
  // Roboto is the system face on Android, Light included, so nothing ships.
  family: '"Roboto", "Helvetica", "Arial", sans-serif',
};

const SPEC: MinimalSpec = {
  id: 'carbon',
  scheme: 'dark',
  font: {
    weightNumeral: 300,
    weightHeading: 500,
    weightLabel: 500,
    weightBody: 400,
    trackingLabel: '0.1em',
    trackingNumeral: '-0.02em',
  },
  /* TrailMate's dark chart palette: picked for a dark ground, and a workout
     keeps the colour its runner knows from the TrailMate app. */
  workout: {
    easy: '#6CC89B', tempo: '#CF7EA8', intervals: '#E27F79', hills: '#C198CF',
    longRun: '#6FB3E3', recovery: '#64B28B', threshold: '#D89464', speed: '#C55E58',
  },
  /* The dataviz reference's first three dark slots. Checked as a set against
     #0A0A0B, all pairs: lightness band, chroma, colour-blind separation 9.4,
     normal-vision 20.9, all three over 3:1. */
  chart: { speed: '#3987E5', hr: '#D95926', incline: '#199E70' },
};

export function makeCarbon(c: Palette = PALETTE, shape: Shape = SHAPE): Theme {
  return makeMinimal(SPEC, c, shape);
}

export const carbon: Theme = makeCarbon();
