import { fireEvent, screen } from '@testing-library/react-native';
import type { ComponentProps } from 'react';
import { BackHandler } from 'react-native';
import { changeLanguage } from 'i18next';

import { SessionUpcoming } from '@/components/session-upcoming';
import type { Exercise, Workout } from '@/domain/types';
import { upcomingOutline } from '@/hooks/session-outline';
import { buildSteps, type RunnerStep } from '@/hooks/session-steps';
import { renderScreen } from '@/test-support/render';

/**
 * What the sheet says and how it closes. Which items it lists is session-outline.test.ts's to pin;
 * the fixtures here are real outlines of real `buildSteps` output, so this only adds the words.
 *
 * The layout — the rail, the scroll, the header clearance — can't be asserted here and is checked in
 * the browser (docs/verifying-in-the-browser.md).
 */

const exercises: Exercise[] = [
  { id: 'pullups', name: 'Pull-ups', type: 'reps', config: { sets: 3, targetRepsMin: 6, targetRepsMax: 10, restSec: 90 } },
  { id: 'lsit', name: 'L-Sit', type: 'timed_hold', config: { sets: 3, holdSecMin: 15, restSec: 60 } },
  { id: 'burpees', name: 'Burpees', type: 'hiit', config: { workSec: 40, restSec: 20, rounds: 3 } },
  { id: 'rest', name: 'Rest', type: 'rest', config: { durationSec: 120 } },
];

const workoutOf = (...blocks: Workout['blocks']): Workout => ({ id: 'w', name: 'W', blocks });
const single = (exerciseId: string): Workout['blocks'][number] => ({ kind: 'exercise', exerciseId });
const circuit = (rounds: number, memberIds: string[]): Workout['blocks'][number] => ({
  kind: 'circuit',
  rounds,
  members: memberIds.map((exerciseId) => ({ exerciseId })),
});

async function show(steps: RunnerStep[], index: number, props: Partial<ComponentProps<typeof SessionUpcoming>> = {}) {
  const onClose = jest.fn();
  const view = await renderScreen(
    <SessionUpcoming
      step={steps[index]}
      items={upcomingOutline(steps, index)}
      ends
      holdElapsedSec={0}
      restRemainingSec={0}
      restTargetSec={0}
      onClose={onClose}
      {...props}
    />,
  );
  return { onClose, ...view };
}

// Driven in pt because an English assertion can't tell a key from the literal it replaced.
it('lists the round you are in, the rounds after it and what follows, in the active locale', async () => {
  await changeLanguage('pt');
  const steps = buildSteps(workoutOf(circuit(3, ['pullups', 'lsit']), single('rest'), single('burpees')), exercises);
  const roundTwo = steps.findIndex((step) => step.kind === 'reps' && step.circuit?.round === 2);
  await show(steps, roundTwo);

  expect(screen.getByText('AGORA')).toBeTruthy();
  expect(screen.getByText('Série 2 de 3')).toBeTruthy();
  expect(screen.getByText('CIRCUITO · RODADA 2 DE 3')).toBeTruthy();
  expect(screen.getByText('isometria · alvo 15s')).toBeTruthy();
  expect(screen.getByText('MAIS 1 RODADA')).toBeTruthy();
  expect(screen.getByText('Descanso')).toBeTruthy();
  expect(screen.getByText('2:00')).toBeTruthy();
  expect(screen.getByText('3 rodadas · hiit · 40s')).toBeTruthy();
  expect(screen.getByText('FIM DO TREINO')).toBeTruthy();
  // User data renders verbatim: once on the NOW row and once in the round after, for Pull-ups; once
  // spelled out in this round and once in the next, for L-Sit.
  expect(screen.getAllByText('Pull-ups')).toHaveLength(2);
  expect(screen.getAllByText('L-Sit')).toHaveLength(2);
});

it('says "more" for an exercise already under way, and not for one to come', async () => {
  const steps = buildSteps(workoutOf(single('pullups'), single('lsit')), exercises);
  await show(steps, 0);

  expect(screen.getByText('2 more sets · target 6–10')).toBeTruthy();
  expect(screen.getByText('3 sets · hold · target 15s')).toBeTruthy();
});

it('counts what is left of a single-round circuit rather than saying round 1 of 1', async () => {
  const steps = buildSteps(workoutOf(circuit(1, ['pullups', 'lsit', 'burpees'])), exercises);
  await show(steps, 0);

  expect(screen.getByText('CIRCUIT · 2 LEFT')).toBeTruthy();
  expect(screen.queryByText(/ROUND 1 OF 1/)).toBeNull();
});

describe('the NOW row', () => {
  it('counts a rest down against its total', async () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    expect(steps[1].kind).toBe('rest');
    await show(steps, 1, { restRemainingSec: 48, restTargetSec: 90 });

    expect(screen.getByText('NOW · REST')).toBeTruthy();
    expect(screen.getByText('0:48')).toBeTruthy();
    expect(screen.getByText('of 1:30 remaining')).toBeTruthy();
  });

  it('counts a hold up, as the hold screen does', async () => {
    const steps = buildSteps(workoutOf(single('lsit')), exercises);
    await show(steps, 0, { holdElapsedSec: 23 });

    expect(screen.getByText('0:23')).toBeTruthy();
    expect(screen.getByText('Set 1 of 3')).toBeTruthy();
  });

  it('shows no clock on a reps set, which has none', async () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    await show(steps, 0, { holdElapsedSec: 23, restRemainingSec: 48 });

    expect(screen.getByText('NOW')).toBeTruthy();
    expect(screen.queryByText('0:23')).toBeNull();
    expect(screen.queryByText('0:48')).toBeNull();
  });
});

it('gives an ad-hoc session no end, since it parks rather than finishing', async () => {
  const steps = buildSteps(workoutOf(single('pullups')), exercises);
  await show(steps, 0, { ends: false });

  expect(screen.queryByText('END OF WORKOUT')).toBeNull();
});

describe('closing', () => {
  it('closes from the × and from the backdrop', async () => {
    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    const { onClose } = await show(steps, 0);

    await fireEvent.press(screen.getByRole('button', { name: 'Close' }));
    // Hidden from assistive tech on purpose (the × is the named way out), so a default query can't see
    // it; this reaches it the way a sighted tap does.
    await fireEvent.press(screen.getByTestId('upcoming-backdrop', { includeHiddenElements: true }));
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  /**
   * Unhandled, back falls through to the session route and leaves the runner mid-workout. The handler
   * has to return true, or Android runs the default behaviour after it anyway.
   */
  it('takes the Android back button while open, and gives it back on close', async () => {
    let backHandler: (() => boolean | null | undefined) | undefined;
    const remove = jest.fn();
    jest.spyOn(BackHandler, 'addEventListener').mockImplementation(((_: string, handler: () => boolean) => {
      backHandler = handler;
      return { remove };
    }) as never);

    const steps = buildSteps(workoutOf(single('pullups')), exercises);
    const { onClose, unmount } = await show(steps, 0);

    expect(backHandler?.()).toBe(true);
    expect(onClose).toHaveBeenCalledTimes(1);

    await unmount();
    expect(remove).toHaveBeenCalled();
  });
});
