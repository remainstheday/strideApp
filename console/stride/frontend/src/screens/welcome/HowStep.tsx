import { useScheduleFeed } from '../../hooks/useScheduleFeed';
import { cx } from '../../lib/cx';
import { arrangeWays, type WayId } from '../../lib/profile';
import { useProfile } from '../../store/profile';
import { openWorkout, useWelcome } from '../../store/welcome';
import { ChoiceCard } from '../../components/molecules';
import { PersonSchedule, useHasSchedule } from './PersonSchedule';
import styles from './Welcome.module.css';

/** Step 2 — how to run (spec 038, S-001): Manual, Routes or Workouts,
 *  stacked down the left; and on the right what TrailMate has planned for
 *  today and tomorrow, a workout there being the quickest way in. Nothing
 *  here moves the belt. */
export function HowStep() {
  const { runner, chooseManual, chooseRoutes, chooseWorkouts } = useWelcome();
  useScheduleFeed();
  const planned = useHasSchedule(runner);
  /* Which ways in, and in what order, as a desktop set them (docs/REMOTE.md).
     Never none: arrangeWays offers all three rather than no way to run. */
  const ways = arrangeWays(useProfile((s) => s.profile));
  const card = (id: WayId) => {
    switch (id) {
      case 'quick':
        return <ChoiceCard key={id} row icon="bolt" name="Quick start" hint="free, or held to a heart rate" onTap={chooseManual} />;
      case 'routes':
        return <ChoiceCard key={id} row icon="route" name="Routes" hint="your starred routes and races" onTap={chooseRoutes} />;
      case 'workouts':
        return <ChoiceCard key={id} row icon="calendar" name="Workouts" hint="your plan from TrailMate" onTap={chooseWorkouts} />;
    }
  };
  return (
    <>
      <div className={cx(styles.how, planned && styles.withPlan)}>
        <div className={styles.ways}>
          {ways.map((id) => card(id))}
        </div>
        {planned && (
          <div className={styles.plan}>
            <PersonSchedule name={runner} onPick={(w) => openWorkout(runner, w.id)} />
          </div>
        )}
      </div>
    </>
  );
}
