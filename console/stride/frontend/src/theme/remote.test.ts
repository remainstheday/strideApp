import { afterEach, describe, expect, it } from 'vitest';
import { cleanProfile } from '../lib/profile';
import { applyProfileTheme, themeFor } from './remote';
import { colour, cssVariables, theme } from './tokens';
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
});
