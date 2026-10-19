import type { RepsSetLog, Session, SessionEntry, TimedHoldSetLog } from '@/domain/types';

/**
 * What counts as "not done" in the log: a set at exactly 0 reps or 0 seconds, an AMRAP with no rounds
 * and no extra reps, a cardio entry with neither a duration nor a distance.
 *
 * The runner has no skip control, so logging 0 is how an exercise gets skipped in practice. That makes
 * a zero indistinguishable from a genuinely failed attempt, and the trade was taken knowingly: a skip
 * is far more common, and showing it as a set inflates set counts, "last time" and the training
 * calendar. Exactly zero, never a small threshold — an honest 2s hold is real data.
 *
 * EMOM and HIIT are deliberately out: an EMOM minute with no reps entered was still worked, and a HIIT
 * round is only counted once it has been finished.
 */
export function isZeroSet(set: RepsSetLog | TimedHoldSetLog): boolean {
  return 'reps' in set ? set.reps === 0 : set.holdSec === 0;
}

/** A set-based entry with its zero sets taken out, or null when none are left. Same object if none went. */
function withoutZeroSets<E extends { sets: (RepsSetLog | TimedHoldSetLog)[] }>(entry: E): E | null {
  const sets = entry.sets.filter((set) => !isZeroSet(set));
  if (sets.length === 0) return null;
  return sets.length === entry.sets.length ? entry : { ...entry, sets };
}

/**
 * The entry with its zero work taken out — zero sets from a set-based entry, or the whole of an AMRAP
 * or cardio entry that did nothing — or `null` when nothing of it is left. Returns the same object
 * when there was nothing to remove, which is what keeps `withoutZeroWork` reference-stable.
 */
export function entryWithoutZeroWork(entry: SessionEntry): SessionEntry | null {
  switch (entry.type) {
    case 'reps':
    case 'timed_hold':
      return withoutZeroSets(entry);
    case 'amrap':
      return entry.roundsCompleted === 0 && !entry.extraReps ? null : entry;
    case 'cardio':
      return !entry.durationSec && !entry.distanceMeters ? null : entry;
    case 'rest':
    case 'hiit':
    case 'emom':
      return entry;
  }
}

/** The session with every zero set, and every entry left empty by that, removed. Same object if nothing was. */
export function withoutZeroWork(session: Session): Session {
  let changed = false;
  const entries = session.entries.flatMap((entry) => {
    const kept = entryWithoutZeroWork(entry);
    if (kept !== entry) changed = true;
    return kept ? [kept] : [];
  });
  return changed ? { ...session, entries } : session;
}

/**
 * Whether a session holds any work at all. A standalone rest on its own doesn't count: a workout whose
 * every exercise was skipped still logs the rest blocks between them, and that isn't a training day.
 */
export function hasLoggedWork(session: Session): boolean {
  return session.entries.some((entry) => entry.type !== 'rest');
}
