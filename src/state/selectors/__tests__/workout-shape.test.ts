import type { Exercise, Workout } from '@/domain/types';
import { blockChips } from '@/state/selectors/workout-shape';

describe('blockChips', () => {
  const exercises: Exercise[] = [
    { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, restSec: 90 } },
    // Deliberately not named "Rest": the styling used to key off this literal name.
    { id: 'breather', name: 'Pausa', type: 'rest', config: { durationSec: 90 } },
  ];

  const workout: Workout = {
    id: 'w',
    name: 'W',
    blocks: [
      { kind: 'exercise', exerciseId: 'pullups' },
      { kind: 'exercise', exerciseId: 'breather' },
      { kind: 'circuit', rounds: 2, members: [{ exerciseId: 'pullups' }, { exerciseId: 'breather' }] },
    ],
  };

  it('flags rest chips by exercise type, not by their display name', () => {
    expect(blockChips(workout, exercises)).toEqual([
      { name: 'Pull-ups', isRest: false },
      { name: 'Pausa', isRest: true },
      { name: 'Pull-ups', isRest: false },
      { name: 'Pausa', isRest: true },
    ]);
  });

  it('falls back to the id and treats an unknown exercise as non-rest', () => {
    const orphan: Workout = { id: 'o', name: 'O', blocks: [{ kind: 'exercise', exerciseId: 'ghost' }] };
    expect(blockChips(orphan, exercises)).toEqual([{ name: 'ghost', isRest: false }]);
  });
});
