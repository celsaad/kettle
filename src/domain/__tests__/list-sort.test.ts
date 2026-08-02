import {
  lastTrainedByExercise,
  lastTrainedByProgram,
  lastTrainedByWorkout,
  sortForList,
  type SortableItem,
} from '@/domain/list-sort';
import type { Session } from '@/domain/types';

/**
 * Ordering is a view concern here, and the property that matters most is the one that's easiest to
 * lose to a "tidier" comparator: `custom` is the order the user wrote in `exercises.yaml`, and no
 * sort may rewrite or discard it.
 */
const items: SortableItem[] = [
  { id: 'zercher', name: 'Zercher squat' },
  { id: 'bench', name: 'bench press' },
  { id: 'agachamento', name: 'Épaules' },
  { id: 'never', name: 'Never done' },
];

function sessionOn(startedAt: string, workout: string | null, program: string | null, exercises: string[]): Session {
  return {
    version: 1,
    id: startedAt,
    workout,
    program,
    programWeek: null,
    programDay: null,
    startedAt,
    endedAt: null,
    entries: exercises.map((exercise) => ({ exercise, type: 'rest', restTakenSec: 30 })),
  };
}

const sessions: Session[] = [
  sessionOn('2026-07-01T09:00:00.000Z', 'push', 'strength', ['bench']),
  sessionOn('2026-07-20T09:00:00.000Z', 'pull', null, ['zercher']),
  sessionOn('2026-07-10T09:00:00.000Z', 'push', 'strength', ['bench', 'agachamento']),
];

const empty = new Map<string, string>();

describe('sortForList', () => {
  it('hands back the very same array for the order the user wrote', () => {
    // Identity, not just equality: a screen that never sorts must re-render exactly as it did before
    // this module existed.
    expect(sortForList(items, 'custom', empty)).toBe(items);
  });

  it('never mutates the array it was given', () => {
    const original = [...items];
    sortForList(items, 'name', empty);
    sortForList(items, 'recent', lastTrainedByExercise(sessions));
    expect(items).toEqual(original);
  });

  // A plain `<` puts every capital ahead of every lowercase and files "Épaules" after "Zercher".
  it('sorts by name the way the reader reads, not by code point', () => {
    expect(sortForList(items, 'name', empty).map((item) => item.name)).toEqual([
      'bench press',
      'Épaules',
      'Never done',
      'Zercher squat',
    ]);
  });

  it('sorts by most recently trained first', () => {
    const sorted = sortForList(items, 'recent', lastTrainedByExercise(sessions));
    // `bench` and `agachamento` were trained in the same session, so they tie — and a tie keeps the
    // order the user wrote, which is the same stability the untrained items below depend on.
    expect(sorted.map((item) => item.id)).toEqual(['zercher', 'bench', 'agachamento', 'never']);
  });

  // The one that a stable sort is load-bearing for: untrained items sink, and keep the order the user
  // wrote among themselves rather than being shuffled or sorted by name as a consolation.
  it('keeps never-trained items last, in the order they were written', () => {
    const withTwoUntouched = [...items, { id: 'also-never', name: 'Also never done' }];
    const sorted = sortForList(withTwoUntouched, 'recent', lastTrainedByExercise(sessions));
    expect(sorted.map((item) => item.id)).toEqual(['zercher', 'bench', 'agachamento', 'never', 'also-never']);
  });

  it('leaves the order alone when nothing has been trained at all', () => {
    expect(sortForList(items, 'recent', empty).map((item) => item.id)).toEqual(items.map((item) => item.id));
  });
});

describe('last trained', () => {
  // Not "the first session that mentions it": the store hands these over newest-first, and this
  // module deliberately doesn't depend on that.
  it('takes the latest session for an id, whatever order the sessions arrive in', () => {
    expect(lastTrainedByWorkout(sessions).get('push')).toBe('2026-07-10T09:00:00.000Z');
  });

  it('ignores the sessions that carry no workout or program', () => {
    expect(lastTrainedByWorkout(sessions).get('pull')).toBe('2026-07-20T09:00:00.000Z');
    expect(lastTrainedByProgram(sessions).has('pull')).toBe(false);
    expect(lastTrainedByProgram(sessions).get('strength')).toBe('2026-07-10T09:00:00.000Z');
  });

  it('reads every exercise a session logged, not just its first', () => {
    const byExercise = lastTrainedByExercise(sessions);
    expect(byExercise.get('bench')).toBe('2026-07-10T09:00:00.000Z');
    expect(byExercise.get('agachamento')).toBe('2026-07-10T09:00:00.000Z');
  });

  it('has nothing to say about an id that was never trained', () => {
    expect(lastTrainedByExercise(sessions).has('never')).toBe(false);
  });
});
