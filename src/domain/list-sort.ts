/**
 * Ordering for the three library lists. Pure, and deliberately non-destructive: every function here
 * returns a new array (or the input untouched) and nothing writes back to the library. `exercises.yaml`
 * is a file the user hand-edits and shares, so the order they wrote is data, not a stale default to be
 * overwritten the first time someone taps "A–Z".
 */
import type { ListSort } from '@/domain/preferences';
import type { Session } from '@/domain/types';

/** The shape all three lists have in common, and all this module needs from them. */
export type SortableItem = { id: string; name: string };

/**
 * When each id was last trained, as the ISO `startedAt` of its most recent session.
 *
 * Takes the max rather than the first hit even though `listSessions()` hands these over newest-first:
 * one comparison per entry is nothing next to depending on an ordering that lives in another module
 * and would fail silently here if it ever changed.
 */
function lastTrainedBy(sessions: Session[], idsOf: (session: Session) => (string | null)[]): Map<string, string> {
  const latest = new Map<string, string>();
  for (const session of sessions) {
    for (const id of idsOf(session)) {
      if (!id) continue;
      const known = latest.get(id);
      if (!known || known < session.startedAt) latest.set(id, session.startedAt);
    }
  }
  return latest;
}

export function lastTrainedByWorkout(sessions: Session[]): Map<string, string> {
  return lastTrainedBy(sessions, (session) => [session.workout]);
}

export function lastTrainedByProgram(sessions: Session[]): Map<string, string> {
  return lastTrainedBy(sessions, (session) => [session.program]);
}

/**
 * Every exercise that appears in a session, circuit members included — `entries` is already flat, so
 * a circuit's members are entries in their own right and need no special case here.
 */
export function lastTrainedByExercise(sessions: Session[]): Map<string, string> {
  return lastTrainedBy(sessions, (session) => session.entries.map((entry) => entry.exercise));
}

/**
 * Orders one list. `lastTrained` is only read for `recent`, so the caller can skip building it
 * otherwise.
 *
 * Two properties worth keeping:
 *
 * - **`custom` returns the same array**, not a copy, so a screen that never sorts re-renders exactly
 *   as it did before this existed.
 * - **Never-trained items keep their file order at the bottom of `recent`**, rather than being
 *   scattered or hidden. `Array.prototype.sort` is stable, so returning 0 for two untrained items is
 *   what preserves that; it isn't an accident that can be "tidied" into a name comparison.
 */
export function sortForList<T extends SortableItem>(items: T[], sort: ListSort, lastTrained: Map<string, string>): T[] {
  if (sort === 'custom') return items;

  if (sort === 'name') {
    // `localeCompare` with no explicit locale, deliberately: these are the user's own names in
    // whatever language they wrote them, and the device locale is the only sensible collation for
    // that. A plain `<` would sort "Épaules" after "Zumba" and lowercase after uppercase.
    // Sorts a copy — the spread is the copy oxlint can't see through (decision log: no `toSorted`).
    // oxlint-disable-next-line unicorn/no-array-sort
    return [...items].sort((a, b) => a.name.localeCompare(b.name));
  }

  // oxlint-disable-next-line unicorn/no-array-sort
  return [...items].sort((a, b) => {
    const left = lastTrained.get(a.id);
    const right = lastTrained.get(b.id);
    if (!left && !right) return 0;
    if (!left) return 1;
    if (!right) return -1;
    return right.localeCompare(left);
  });
}
