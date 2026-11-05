import { afterEach, describe, expect, it } from 'vitest';
import { cleanProfile } from '../lib/profile';
import { applyProfileTheme, themeFor } from './remote';
import { colour, cssVariables, theme } from './tokens';
import { BASES, THEME_IDS } from './themes';
import { carbon } from './themes/carbon';
import { paper } from './themes/paper';
import { trailmate } from './themes/trailmate';

const DEFAULT_CSS = cssVariables(trailmate);

describe('a profile theme', () => {
  afterEach(() => applyProfileTheme({}));

  it('is TrailMate when the profile says nothing', () => {
    expect(themeFor({})).toEqual(trailmate);
    expect(cssVariables(themeFor({}))).toBe(DEFAULT_CSS);
  });

  it('carries a new accent into everything mixed from it', () => {
    const t = themeFor({ theme: { colour: { accent: '#FF0000' } } });
    expect(t.colour.accent).toBe('#FF0000');
    expect(t.role.selected).toBe('rgba(255,0,0,0.2)');
    expect(t.role.pulse).toBe('rgba(255,0,0,0.5)');
    expect(t.settings.accent).toBe('#FF0000');
    expect(t.settings['accent-rgb']).toBe('255,0,0');
    // and nothing that is not made from it
    expect(t.role.edge).toBe(trailmate.role.edge);
    expect(t.colour.good).toBe(trailmate.colour.good);
  });

  it('carries the surface, ink and shape into theirs', () => {
    const t = themeFor({ theme: { colour: { bg1: '#102030', ink: '#FFFFFF' }, radius: { control: 14 }, edgeWidth: 0, glowBlur: 6 } });
    expect(t.role.fill).toBe('#102030');
    expect(t.settings['surface-rgb']).toBe('16,32,48');
    expect(t.role.edge).toBe('rgba(255,255,255,0.2)');
    expect(t.settings.radius).toBe('14px');
    expect(t.radius.card).toBe(8);
    expect(t.settings.edge).toBe('0px');
    expect(t.role.glow).not.toBe('transparent');
  });

  it('keeps nothing teal on a graphite console', () => {
    const t = themeFor({ theme: { colour: { bg0: '#0E0F11', bg1: '#1A1C1F', accent: '#E0A040' } } });
    const css = cssVariables(t);
    // TrailMate's ground, accent and in-between teals, as hex and as r,g,b.
    for (const teal of ['#001E20', '#08373A', '#6DA491', '#0B4A4C', '#062F31', '#0E3134', '#0A2628', '#002C2F', '#324A48',
      '0,30,32', '8,55,58', '109,164,145', '11,74,76', '0,44,47', '50,74,72']) {
      expect(css).not.toContain(teal);
    }
    expect(t.role.sleepClock).toMatch(/^#[0-9A-F]{6}$/);
    expect(t.colour.trackLine).not.toBe(trailmate.colour.trackLine);
  });

  it('keeps a hairline given on purpose', () => {
    expect(themeFor({ theme: { colour: { bg0: '#000000', trackLine: '#222222' } } }).colour.trackLine).toBe('#222222');
  });

  it('changes the live theme in place and rewrites the stylesheet', () => {
    const style = document.createElement('style');
    style.setAttribute('data-theme', 'trailmate');
    document.head.appendChild(style);
    const before = theme.colour;
    applyProfileTheme(cleanProfile({ theme: { colour: { accent: '#123456' } } }));
    expect(theme.colour).toBe(before);
    expect(colour.accent).toBe('#123456');
    expect(style.textContent).toContain('--colour-accent:#123456');
    applyProfileTheme({});
    expect(colour.accent).toBe('#6DA491');
    expect(style.textContent).toBe(DEFAULT_CSS);
    style.remove();
  });

  it('wears the look a profile names, and TrailMate for one it does not know', () => {
    expect(themeFor({ theme: { base: 'carbon' } })).toEqual(carbon);
    expect(themeFor({ theme: { base: 'paper' } })).toEqual(paper);
    expect(themeFor(cleanProfile({ theme: { base: 'neon' } }))).toEqual(trailmate);
    expect(cleanProfile({ theme: { base: 'trailmate' } }).theme).toEqual({});
  });

  it("lays a profile's colours over the named look, not over TrailMate", () => {
    const t = themeFor({ theme: { base: 'paper', colour: { accent: '#AA0000' }, radius: { card: 4 } } });
    expect(t.id).toBe('paper');
    expect(t.colour.bg0).toBe(paper.colour.bg0);
    expect(t.role.selected).toBe('rgba(170,0,0,0.12)');
    expect(t.radius.card).toBe(4);
    expect(t.radius.control).toBe(paper.radius.control);
    expect(t.font.weightNumeral).toBe(paper.font.weightNumeral);
  });

  it('changes look in place, and back', () => {
    const style = document.createElement('style');
    style.setAttribute('data-theme', 'trailmate');
    document.head.appendChild(style);
    applyProfileTheme(cleanProfile({ theme: { base: 'paper' } }));
    expect(theme.id).toBe('paper');
    expect(colour.bg0).toBe('#F5F4F0');
    expect(style.getAttribute('data-theme')).toBe('paper');
    expect(style.textContent).toBe(cssVariables(paper));
    applyProfileTheme({});
    expect(theme.id).toBe('trailmate');
    expect(style.textContent).toBe(DEFAULT_CSS);
    style.remove();
  });
});

/** WCAG contrast of two solid colours. */
function contrast(a: string, b: string): number {
  const lum = (hex: string) => {
    const n = parseInt(hex.slice(1), 16);
    const [r, g, bl] = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r! + 0.7152 * g! + 0.0722 * bl!;
  };
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi! + 0.05) / (lo! + 0.05);
}

describe.each(THEME_IDS)('the %s look', (id) => {
  const t = BASES[id].make(BASES[id].palette, BASES[id].shape);
  const css = cssVariables(t);

  it('is a whole theme under its own name', () => {
    expect(t.id).toBe(id);
    expect(Object.keys(t).sort()).toEqual(Object.keys(trailmate).sort());
    for (const k of ['role', 'font', 'workout', 'radius', 'settings', 'chart'] as const) {
      expect(Object.keys(t[k]).sort()).toEqual(Object.keys(trailmate[k]).sort());
    }
    // No value that would write "undefined" into the stylesheet.
    expect(css).not.toMatch(/undefined|NaN/);
  });

  it('uses solid colours wherever a canvas reads them', () => {
    for (const v of [...Object.values(t.colour), ...Object.values(t.workout), ...Object.values(t.chart)]) {
      expect(v).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });

  it('can be read: every ink on the canvas and on a card, and what colour means', () => {
    for (const ground of [t.colour.bg0, t.colour.bg1]) {
      expect(contrast(t.colour.ink, ground)).toBeGreaterThanOrEqual(7);
      expect(contrast(t.colour.inkDim, ground)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(t.colour.inkFaint, ground)).toBeGreaterThanOrEqual(4);
      // Glyphs and large text: the + and −, STOP, the running chip.
      for (const tone of [t.colour.accent, t.colour.accentWarm, t.colour.good, t.colour.alert]) {
        expect(contrast(tone, ground)).toBeGreaterThanOrEqual(3);
      }
      for (const w of Object.values(t.workout)) expect(contrast(w, ground)).toBeGreaterThanOrEqual(3);
    }
  });
});
