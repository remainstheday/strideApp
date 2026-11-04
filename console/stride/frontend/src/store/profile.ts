import { create } from 'zustand';
import { bridge } from '../bridge/stride';
import { cleanProfile, type Profile } from '../lib/profile';
import { applyProfileTheme } from '../theme/remote';
import { loadSettings } from './settings';

/* The profile a desktop last sent (docs/REMOTE.md). Read once at boot,
   before the first render, and again whenever Kotlin pushes a new one
   through window.applyProfile. */

interface ProfileStore {
  profile: Profile;
  apply(raw: unknown): void;
}

export const useProfile = create<ProfileStore>()((set) => ({
  profile: {},
  apply(raw) {
    const profile = cleanProfile(raw);
    applyProfileTheme(profile);
    set({ profile });
  },
}));

/** The stored profile, from an APK new enough to have one. */
export function loadProfile(): void {
  let raw: unknown = {};
  try {
    raw = JSON.parse(bridge().profile?.() || '{}');
  } catch {
    raw = {};
  }
  useProfile.getState().apply(raw);
}

/** Kotlin's entry point for a profile that has just arrived. Its settings
 *  are already in the console's store, so they are read again too. */
export function installProfileEntryPoint(): void {
  window.applyProfile = (p) => {
    useProfile.getState().apply(p);
    loadSettings();
  };
}
