import { describe, expect, it } from 'vitest';
import { arrangeApps, arrangeWays, cleanProfile } from './profile';

const apps = ['com.netflix.mediaclient', 'com.google.android.youtube', 'com.spotify.music', 'com.plexapp.android']
  .map((pkg) => ({ pkg, name: pkg }));

describe('a profile', () => {
  it('drops what a stylesheet must not see', () => {
    const p = cleanProfile({
      theme: { colour: { accent: 'red', ink: '#abcdef' }, font: { family: 'X;}body{' }, radius: { card: 99 }, edgeWidth: -1 },
      apps: { order: ['not a package', 'com.spotify.music', 'com.spotify.music'] },
      welcome: { ways: [{ id: 'nope' }, { id: 'routes', visible: false }, { id: 'routes' }] },
    });
    expect(p.theme).toEqual({ colour: { ink: '#ABCDEF' }, radius: { card: 40 }, edgeWidth: 0 });
    expect(p.apps).toEqual({ order: ['com.spotify.music'], hidden: [] });
    expect(p.welcome).toEqual({ ways: [{ id: 'routes', visible: false }] });
    expect(cleanProfile('nonsense')).toEqual({});
  });

  it('orders the apps, listed first, the rest as they came, hidden ones gone', () => {
    const out = arrangeApps(apps, { apps: { order: ['com.plexapp.android', 'com.spotify.music'], hidden: ['com.google.android.youtube'] } });
    expect(out.map((a) => a.pkg)).toEqual(['com.plexapp.android', 'com.spotify.music', 'com.netflix.mediaclient']);
    expect(arrangeApps(apps, {})).toEqual(apps);
  });

  it('orders the ways in and never hides them all', () => {
    expect(arrangeWays({})).toEqual(['quick', 'routes', 'workouts']);
    expect(arrangeWays({ welcome: { ways: [{ id: 'workouts', visible: true }, { id: 'quick', visible: false }] } }))
      .toEqual(['workouts', 'routes']);
    expect(arrangeWays({ welcome: { ways: [
      { id: 'quick', visible: false }, { id: 'routes', visible: false }, { id: 'workouts', visible: false },
    ] } })).toEqual(['quick', 'routes', 'workouts']);
  });
});
