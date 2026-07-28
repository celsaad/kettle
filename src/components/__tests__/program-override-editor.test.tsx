import { fireEvent, screen } from '@testing-library/react-native';

import { ProgramOverrideEditor } from '@/components/program-override-editor';
import type { ProgramOverride } from '@/domain/types';
import { aLibrary, anExercise, aWorkout } from '@/test-support/library';
import { renderScreen } from '@/test-support/render';

/**
 * The add/edit flow's local state, and the conversion it hides.
 *
 * The conversion is the reason this component is worth testing at all: an override's `config` is a
 * partial, *snake_case* patch, while the form it presents works in ordinary camelCase domain values
 * like every other exercise form. `diffExerciseOverride` bridges the two on confirm, so the assertions
 * below are on the emitted patch — the thing that ends up in the user's YAML — rather than on which
 * inputs were filled.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const pullUps = anExercise({ id: 'pull-ups', name: 'Pull-ups' }); // reps: 4 sets, 6 min, 90s rest
const dips = anExercise({ id: 'dips', name: 'Dips' });

const circuit = {
  kind: 'circuit' as const,
  id: 'finisher',
  rounds: 3,
  restBetweenExercisesSec: 15,
  restBetweenRoundsSec: 60,
  members: [{ exerciseId: 'pull-ups' }, { exerciseId: 'dips' }],
};

const workout = aWorkout({
  id: 'push-day',
  name: 'Push day',
  blocks: [{ kind: 'exercise', exerciseId: 'pull-ups' }, circuit],
});
const library = aLibrary({ exercises: [pullUps, dips], workouts: [workout] });

/**
 * Config inputs carry no placeholder or label, so they're addressed by the value they were seeded
 * with — which is the point of the seeding, and unambiguous across these fixtures. (RNTL 14 dropped
 * `screen.UNSAFE_getAllByType`, so positional lookup isn't available anyway.)
 */
const fieldShowing = (value: string) => screen.getByDisplayValue(value);

function mount(overrides: ProgramOverride[] = []) {
  const onChange = jest.fn();
  return {
    onChange,
    rendered: renderScreen(
      <ProgramOverrideEditor library={library} workout={workout} overrides={overrides} onChange={onChange} />,
    ),
  };
}

it('says there is nothing to override when the week has no workout selected', async () => {
  const onChange = jest.fn();
  await renderScreen(<ProgramOverrideEditor library={library} workout={undefined} overrides={[]} onChange={onChange} />);

  // No workout means no eligible targets, and the button is disabled rather than opening an empty
  // picker. The hint is deliberately absent here — with no workout chosen yet there is nothing to
  // explain, only something not yet done.
  expect(screen.queryByText('This workout has nothing to override yet.')).toBeNull();
});

it('explains itself when the chosen workout has nothing overridable', async () => {
  const onChange = jest.fn();
  const empty = aWorkout({ id: 'empty', name: 'Empty', blocks: [] });
  await renderScreen(<ProgramOverrideEditor library={library} workout={empty} overrides={[]} onChange={onChange} />);

  expect(screen.getByText('This workout has nothing to override yet.')).toBeTruthy();
});

it('offers only the exercises and id-tagged circuits the workout actually contains', async () => {
  const { rendered } = mount();
  await rendered;

  await fireEvent.press(screen.getByText('+ Add override'));

  expect(screen.getByText('Pull-ups')).toBeTruthy();
  // Dips is in the library and in the circuit, so it's eligible through circuit membership.
  expect(screen.getByText('Dips')).toBeTruthy();
  // The circuit is offered by its id, which is what an override has to target. An untagged circuit
  // can't be addressed by an override at all, which is why `workoutIdTaggedCircuits` filters on `id`.
  expect(screen.getByText('finisher')).toBeTruthy();
});

it('labels config fields in the user language, not with their i18next keys', async () => {
  const { rendered } = mount();
  await rendered;

  await fireEvent.press(screen.getByText('+ Add override'));
  await fireEvent.press(screen.getByText('Pull-ups'));

  // `FieldDef.label` holds a key path, not display text — the same field list is rendered through
  // `t()` in exercise-editor.tsx. Rendering it raw puts "exerciseForm.field.sets" on screen.
  expect(screen.getByText(/^Sets/)).toBeTruthy();
  expect(screen.queryByText(/exerciseForm\./)).toBeNull();
});

it('emits only the changed key, in the snake_case the YAML uses', async () => {
  const { onChange, rendered } = mount();
  await rendered;

  await fireEvent.press(screen.getByText('+ Add override'));
  await fireEvent.press(screen.getByText('Pull-ups'));
  await fireEvent.changeText(fieldShowing('4'), '5');
  await fireEvent.press(screen.getByText('Add override'));

  // Not the whole config: an override is a patch, and writing every field would freeze the week
  // against later edits to the base exercise. `sets` is the only field touched, so it's the only key.
  expect(onChange).toHaveBeenCalledWith([{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } }]);
});

it('emits a block override keyed by the circuit id', async () => {
  const { onChange, rendered } = mount();
  await rendered;

  await fireEvent.press(screen.getByText('+ Add override'));
  await fireEvent.press(screen.getByText('finisher'));
  await fireEvent.press(screen.getByText('+'));
  await fireEvent.press(screen.getByText('Add override'));

  expect(onChange).toHaveBeenCalledWith([{ kind: 'block', blockId: 'finisher', config: { rounds: 4 } }]);
});

it('seeds an edit from the effective value, and replaces in place', async () => {
  const existing: ProgramOverride = { kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } };
  const { onChange, rendered } = mount([existing]);
  await rendered;

  await fireEvent.press(screen.getByText('Pull-ups: sets → 5'));

  // Seeded from base+override, not from the base alone — otherwise reopening an override shows the
  // pre-override numbers and saving without touching anything quietly discards it.
  expect(fieldShowing('5')).toBeTruthy();

  await fireEvent.changeText(fieldShowing('5'), '6');
  await fireEvent.press(screen.getByText('Save override'));

  expect(onChange).toHaveBeenCalledWith([{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 6 } }]);
});

it('adds alongside an existing override rather than replacing it', async () => {
  const existing: ProgramOverride = { kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } };
  const { onChange, rendered } = mount([existing]);
  await rendered;

  await fireEvent.press(screen.getByText('+ Add override'));
  await fireEvent.press(screen.getByText('Dips'));
  await fireEvent.changeText(fieldShowing('4'), '2');
  await fireEvent.press(screen.getByText('Add override'));

  expect(onChange).toHaveBeenCalledWith([existing, { kind: 'exercise', exerciseId: 'dips', config: { sets: 2 } }]);
});

it('removes an override', async () => {
  const existing: ProgramOverride = { kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } };
  const { onChange, rendered } = mount([existing]);
  await rendered;

  await fireEvent.press(screen.getByLabelText('Remove override'));

  expect(onChange).toHaveBeenCalledWith([]);
});

it('will not open an override whose target has gone', async () => {
  // Reachable by editing the week's workout after writing the override, or by importing a library
  // that drops the exercise. Two guards produce this outcome — the row is `disabled` when the target
  // doesn't resolve, and `startEdit` bails again on a missing base — so removing either alone changes
  // nothing observable. Both are asserted: the row's disabled state distinguishes the first guard,
  // which is otherwise invisible, and the row still needs to *look* inert rather than merely be inert.
  const dangling: ProgramOverride = { kind: 'exercise', exerciseId: 'ghost', config: { sets: 5 } };
  const { rendered } = mount([dangling]);
  await rendered;

  expect(screen.getByText('ghost: sets → 5')).toBeDisabled();

  await fireEvent.press(screen.getByText('ghost: sets → 5'));
  expect(screen.queryByText('Save override')).toBeNull();
});
