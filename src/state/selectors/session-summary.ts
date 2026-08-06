/**
 * Reducing one logged session to the numbers and names every downstream reader shares: how many sets
 * it was worth, how long it ran, what to call it, and what each entry actually put up. The stat
 * selectors and the list views both build on these, which is why they live apart from both — the same
 * count feeding History's tiles and Today's must have exactly one definition.
 */
import type { EntryResult } from '@/domain/format';
import type { Library, Session, SessionEntry } from '@/domain/types';

/**
 * How many sets one logged entry is worth. Interval work has no `sets` array, and counting each such
 * entry as a flat 1 — which is what this did — made a 20-minute EMOM and a single 30-second hold the
 * same one "set". Every stat tile in History and Today reads this, so the whole volume story
 * under-reported for anyone whose training is mostly intervals.
 *
 * The comparable unit is one interval actually performed: a HIIT or AMRAP round, an EMOM minute —
 * the same numbers `sessionEntryResult` already reports per entry. `cardio` stays at 1 (one
 * continuous effort, not a set count) and `rest` at 0.
 */
function entrySetCount(entry: SessionEntry): number {
  switch (entry.type) {
    case 'timed_hold':
    case 'reps':
      return entry.sets.length;
    case 'hiit':
    case 'amrap':
      return entry.roundsCompleted;
    case 'emom':
      return entry.minutes.length;
    case 'cardio':
      return 1;
    case 'rest':
      return 0;
  }
}

export function sessionSetCount(session: Session): number {
  return session.entries.reduce((count, entry) => count + entrySetCount(entry), 0);
}

export function sessionDurationMinutes(session: Session): number {
  if (!session.endedAt) return 0;
  const ms = new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime();
  return Math.max(0, Math.round(ms / 60000));
}

/**
 * The workout behind a session, or `null` when it was started ad-hoc — the stand-in label for that
 * case is `formatSessionName`'s to translate, not this layer's to assemble. Falls back to the raw id
 * for a workout that has since been deleted from the library, which is user data either way.
 */
export function workoutNameFor(session: Session, library: Library): string | null {
  if (!session.workout) return null;
  return library.workouts.find((workout) => workout.id === session.workout)?.name ?? session.workout;
}

/**
 * Structured, not a sentence — `formatEntryResult` in domain/format.ts renders it. Collapses seven
 * entry types onto six shapes: hiit and amrap both reduce to rounds, so nothing downstream has to
 * know which produced them.
 */
export function sessionEntryResult(entry: SessionEntry): EntryResult {
  switch (entry.type) {
    case 'timed_hold':
      return { kind: 'holds', holdSecs: entry.sets.map((set) => set.holdSec) };
    case 'reps':
      return { kind: 'reps', reps: entry.sets.map((set) => set.reps) };
    case 'hiit':
      return { kind: 'rounds', rounds: entry.roundsCompleted };
    case 'emom': {
      const totalReps = entry.minutes.reduce((sum, minute) => sum + (minute.reps ?? 0), 0);
      return { kind: 'intervals', intervals: entry.minutes.length, totalReps: totalReps || undefined };
    }
    case 'amrap':
      return { kind: 'rounds', rounds: entry.roundsCompleted, extraReps: entry.extraReps };
    case 'cardio':
      return { kind: 'cardio', durationSec: entry.durationSec, distanceMeters: entry.distanceMeters };
    case 'rest':
      return { kind: 'rest', restTakenSec: entry.restTakenSec };
  }
}

/**
 * A single comparable number per logged entry, for the volume chart — same discriminated switch shape
 * as `sessionEntryResult`, just numeric instead of a display descriptor. `rest` is unreachable here
 * since `exerciseHistory` already filters those out before this is called.
 */
export function entryVolume(entry: SessionEntry): number {
  switch (entry.type) {
    case 'timed_hold':
      return entry.sets.reduce((sum, set) => sum + set.holdSec, 0);
    case 'reps': {
      const hasWeight = entry.sets.some((set) => set.weightKg !== undefined);
      return entry.sets.reduce((sum, set) => sum + (hasWeight ? set.reps * (set.weightKg ?? 0) : set.reps), 0);
    }
    case 'hiit':
      return entry.roundsCompleted;
    case 'emom':
      return entry.minutes.reduce((sum, minute) => sum + (minute.reps ?? 0), 0);
    case 'amrap':
      return entry.roundsCompleted;
    case 'cardio':
      return entry.distanceMeters ?? entry.durationSec ?? 0;
    case 'rest':
      return 0;
  }
}
