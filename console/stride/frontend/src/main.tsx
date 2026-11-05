import { createRoot } from 'react-dom/client';
import { App } from './App';
import { core } from './bridge/stride';
import { installKotlinEntryPoints, installModeWatcher, onModeChange } from './bridge/kotlin';
import { useSession } from './store/session';
import { installProfileEntryPoint, loadProfile, useProfile } from './store/profile';
import { loadSettings } from './store/settings';
import { useUi } from './store/ui';
import { initWelcome } from './store/welcome';
import { applyTokens } from './theme/tokens';
import './theme/global.css';

/* `Stride` is Kotlin's @JavascriptInterface. In a desktop browser the core
   stubs it, so this page can be designed without a treadmill. Add
   ?screen=live-workout (or warmup, paused, summary, …) to drive it with the
   core's 5 Hz demo instead of sitting on the welcome screen. */
const standalone = core().stub();

applyTokens();
/* A desktop's profile (docs/REMOTE.md) before the first frame, so the
   console never draws in one look and then another. */
loadProfile();
/* In a browser, with no console to send a profile: ?theme=carbon (or paper)
   wears that look, so every screen can be designed in it. */
const look = standalone && /[?&]theme=([a-z]+)/.exec(location.search);
if (look) useProfile.getState().apply({ theme: { base: look[1] } });
loadSettings();
initWelcome();
installKotlinEntryPoints();
installProfileEntryPoint();
installModeWatcher();
onModeChange(useSession.getState().mode);

/* Somebody may have just added themselves; the picker must not show a name
   Settings does not have. */
document.addEventListener('stride-settings-closed', () => {
  useUi.getState().peopleChanged();
  /* And km or miles take effect the moment Settings is put away. */
  loadSettings();
});

createRoot(document.getElementById('root')!).render(<App />);

if (standalone && /[?&]screen=/.test(location.search)) core().demo();
