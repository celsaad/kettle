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

/**
 * This was the one editor in the app that ran no validation at all, so `sets: 0` was two taps from
 * the program file. It matters more now than it did: `applyExerciseOverride` re-validates the merged
 * config and silently keeps the base exercise when it fails, so an unvalidated patch would save,
 * display, and quietly do nothing on the week it claims to change.
 */
describe('config validation on confirm', () => {
  it('refuses to emit an override the schema would reject, and says why', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.changeText(fieldShowing('4'), '0');
    await fireEvent.press(screen.getByText('Add override'));

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText('Sets must be at least 1.')).toBeTruthy();
  });

  it('refuses a count past the ceiling too', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.changeText(fieldShowing('4'), '501');
    await fireEvent.press(screen.getByText('Add override'));

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText('Sets can be at most 500.')).toBeTruthy();
  });

  it('clears the message once the field is edited again, and then saves', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('Pull-ups'));
    await fireEvent.changeText(fieldShowing('4'), '0');
    await fireEvent.press(screen.getByText('Add override'));
    await fireEvent.changeText(fieldShowing('0'), '5');

    expect(screen.queryByText('Sets must be at least 1.')).toBeNull();

    await fireEvent.press(screen.getByText('Add override'));
    expect(onChange).toHaveBeenCalledWith([{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } }]);
  });
});

describe('the circuit branch is gated too', () => {
  it('refuses a negative rest rather than saving one the merge will drop', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('finisher'));
    await fireEvent.changeText(fieldShowing('15'), '-5');
    await fireEvent.press(screen.getByText('Add override'));

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByText('Rest/exercise (sec) must be at least 0.')).toBeTruthy();
  });

  it('still saves a circuit patch the schema accepts', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('finisher'));
    await fireEvent.changeText(fieldShowing('15'), '0');
    await fireEvent.press(screen.getByText('Add override'));

    expect(onChange).toHaveBeenCalledWith([
      { kind: 'block', blockId: 'finisher', config: { rest_between_exercises_sec: 0 } },
    ]);
  });
});

/**
 * The overrides list stays mounted behind the edit panel, so a row tap can swap the target out from
 * under a message that named the old one — leaving "Sets must be at least 1." above a circuit's
 * fields, or above a different exercise entirely.
 */
it('drops a stale error when the panel moves to another target', async () => {
  const overrides: ProgramOverride[] = [
    { kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } },
    { kind: 'exercise', exerciseId: 'dips', config: { sets: 3 } },
  ];
  const { rendered } = mount(overrides);
  await rendered;

  await fireEvent.press(screen.getByText('Pull-ups: sets → 5'));
  await fireEvent.changeText(fieldShowing('5'), '0');
  await fireEvent.press(screen.getByText('Save override'));
  expect(screen.getByText('Sets must be at least 1.')).toBeTruthy();

  // Via Cancel, because the rows are inert while the panel is open — see the stale-index tests below.
  await fireEvent.press(screen.getByText('Cancel'));
  await fireEvent.press(screen.getByText('Dips: sets → 3'));

  expect(screen.queryByText('Sets must be at least 1.')).toBeNull();
});

/**
 * An override the schema refuses is dropped at merge time, and the editor has to be the way *out* of
 * one rather than the thing that destroys it.
 *
 * Reachable without doing anything wrong: `programOverrideSchema` types `config` as a free record, so
 * a file written before the merge was validated still imports and lands here.
 */
describe('an override the merge now refuses', () => {
  const refused: ProgramOverride[] = [{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 0 } }];

  it('is listed as not applied rather than as though it ran', async () => {
    const { rendered } = mount(refused);
    await rendered;

    expect(screen.getByText('Pull-ups: sets → 0')).toBeTruthy();
    expect(screen.getByText("not applied — this value isn't allowed")).toBeTruthy();
  });

  /**
   * The destructive one. `applyExerciseOverride` returns the base untouched when it refuses, so
   * seeding from it showed the *pre-override* numbers — and confirming then diffed base against base
   * and emitted `config: {}`, erasing the patch and leaving an empty row.
   */
  it('opens showing what was authored, not the base it fell back to', async () => {
    const { rendered } = mount(refused);
    await rendered;

    await fireEvent.press(screen.getByText('Pull-ups: sets → 0'));

    // 0 is what the user wrote; 4 is the base this would have silently reverted to.
    expect(screen.getByDisplayValue('0')).toBeTruthy();
  });

  it('can be corrected and saved, instead of being emptied', async () => {
    const { onChange, rendered } = mount(refused);
    await rendered;

    await fireEvent.press(screen.getByText('Pull-ups: sets → 0'));
    await fireEvent.changeText(screen.getByDisplayValue('0'), '6');
    await fireEvent.press(screen.getByText('Save override'));

    expect(onChange).toHaveBeenCalledWith([{ kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 6 } }]);
  });
});

/**
 * Zod strips a key it doesn't recognise instead of refusing it, so a typo parses, changes nothing, and
 * used to render exactly like a working override — the PR's own thesis leaking through the strip path
 * rather than the reject path.
 */
it('marks a misspelled key as not applied, not as applied', async () => {
  // A `reps` exercise's field is `target_reps_min`; `reps` is nothing.
  const { rendered } = mount([{ kind: 'exercise', exerciseId: 'pull-ups', config: { reps: 12 } }]);
  await rendered;

  // The note sits on the key's own line — an unknown key is stripped on its own while the rest of
  // the patch still runs, so marking the whole override would be wrong the moment one is mixed in.
  expect(screen.getByText('Pull-ups: reps → 12 · not applied — there is no such setting to change')).toBeTruthy();
});

/**
 * `config: { exercises: 2 }` is a legal patch shape under `programOverrideSchema`, and spreading it
 * over the raw block replaced the member list with a number: `raw.exercises.map is not a function`,
 * thrown inside a press handler where no error boundary catches it.
 */
it('survives a structural key on a circuit override', async () => {
  const { rendered } = mount([{ kind: 'block', blockId: 'finisher', config: { exercises: 2 } }]);
  await rendered;

  const row = screen.getByText('Circuit (finisher): exercises → 2 · not applied — there is no such setting to change');
  // The row opens rather than taking the screen down with it.
  await fireEvent.press(row);
  expect(screen.getByText('Circuit (finisher)')).toBeTruthy();
});

/**
 * The panel seeds an absent rest as "0" so the input isn't blank, and `diffBlockOverride` compares
 * against `undefined` — so changing only the rounds invented two rest patches nobody asked for.
 */
it('does not invent a rest override on a circuit that declares none', async () => {
  const bare = {
    kind: 'circuit' as const,
    id: 'bare',
    rounds: 3,
    members: [{ exerciseId: 'pull-ups' }, { exerciseId: 'dips' }],
  };
  const bareWorkout = aWorkout({ id: 'bare-day', name: 'Bare day', blocks: [bare] });
  const onChange = jest.fn();
  await renderScreen(
    <ProgramOverrideEditor
      library={aLibrary({ exercises: [pullUps, dips], workouts: [bareWorkout] })}
      workout={bareWorkout}
      overrides={[]}
      onChange={onChange}
    />,
  );

  await fireEvent.press(screen.getByText('+ Add override'));
  await fireEvent.press(screen.getByText('bare'));
  await fireEvent.press(screen.getByText('+'));
  await fireEvent.press(screen.getByText('Add override'));

  expect(onChange).toHaveBeenCalledWith([{ kind: 'block', blockId: 'bare', config: { rounds: 4 } }]);
});

/**
 * The seed/diff asymmetry, which has now produced a bug in three consecutive rounds: the form seeds a
 * concrete value, `diff*Override` compares against the base's `undefined`, and "the form couldn't show
 * it" is indistinguishable from "the user didn't change it".
 *
 * An override with no keys is the shared symptom — it saves without error, renders as a blank row, and
 * does nothing.
 */
describe('an override the form cannot fully represent', () => {
  it('clears a rest the circuit does declare, rather than saving an empty override', async () => {
    const { onChange, rendered } = mount();
    await rendered;

    await fireEvent.press(screen.getByText('+ Add override'));
    await fireEvent.press(screen.getByText('finisher'));
    // The circuit declares 15s. Clearing the field means "no rest", which is `0` — not "unchanged".
    await fireEvent.changeText(screen.getByDisplayValue('15'), '');
    await fireEvent.press(screen.getByText('Add override'));

    expect(onChange).toHaveBeenCalledWith([
      { kind: 'block', blockId: 'finisher', config: { rest_between_exercises_sec: 0 } },
    ]);
  });

  it('keeps a key the form cannot show instead of erasing it on save', async () => {
    // `reps` is not a field of a `reps` exercise, so the form has nowhere to put it — and dropping it
    // silently on save is the same destruction the invalidValue path was fixed for, down the path the
    // "not applied" label now invites the user onto.
    const typo: ProgramOverride[] = [{ kind: 'exercise', exerciseId: 'pull-ups', config: { reps: 12 } }];
    const { onChange, rendered } = mount(typo);
    await rendered;

    await fireEvent.press(screen.getByText(/Pull-ups: reps → 12/));
    await fireEvent.changeText(fieldShowing('4'), '5');
    await fireEvent.press(screen.getByText('Save override'));

    expect(onChange).toHaveBeenCalledWith([{ kind: 'exercise', exerciseId: 'pull-ups', config: { reps: 12, sets: 5 } }]);
  });

  it('removes an override that ends up with nothing in it rather than saving a blank row', async () => {
    const typo: ProgramOverride[] = [{ kind: 'exercise', exerciseId: 'dips', config: { sets: 5 } }];
    const { onChange, rendered } = mount(typo);
    await rendered;

    await fireEvent.press(screen.getByText('Dips: sets → 5'));
    // Back to the base value, so there is no longer an override to store.
    await fireEvent.changeText(fieldShowing('5'), '4');
    await fireEvent.press(screen.getByText('Save override'));

    expect(onChange).toHaveBeenCalledWith([]);
  });
});

/**
 * **Stale index across an interaction that mutates the list.** The rows stay mounted above the open
 * panel and `removeOverride` doesn't close it, so `editingIndex` can outlive the override it points
 * at. `unrepresentable()` then dereferenced `overrides[editingIndex].config` and threw out of a press
 * handler — `program-editor.tsx` wraps this in a `ModalErrorBoundary`, so the whole program editor is
 * replaced by the fallback and the unsaved draft goes with it.
 *
 * Three of the last four review rounds found a stale-state crash in a press handler in this
 * component. These pin the *class*, not the instance.
 */
describe('the list changing while the panel is open', () => {
  const two: ProgramOverride[] = [
    { kind: 'exercise', exerciseId: 'pull-ups', config: { sets: 5 } },
    { kind: 'exercise', exerciseId: 'dips', config: { sets: 3 } },
  ];

  it('will not let a row be removed while one is being edited', async () => {
    const { onChange, rendered } = mount(two);
    await rendered;

    await fireEvent.press(screen.getByText('Pull-ups: sets → 5'));
    const remove = screen.getAllByLabelText('Remove override')[0];
    expect(remove.props.accessibilityState?.disabled).toBe(true);

    await fireEvent.press(remove);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('will not let another row be opened while one is being edited', async () => {
    const { rendered } = mount(two);
    await rendered;

    await fireEvent.press(screen.getByText('Pull-ups: sets → 5'));
    expect(screen.getByText('Dips: sets → 3').parent?.props.accessibilityState?.disabled).toBe(true);
  });

  /**
   * The crash itself, reached the way the parent can still reach it: `overrides` is a prop, so the
   * program editor re-rendering with a shorter list leaves `editingIndex` pointing past the end. The
   * guards above stop the *user* getting here; this stops the component from caring either way.
   */
  it('does not throw when the list shrinks underneath the open panel', async () => {
    const { rerender } = await renderScreen(
      <ProgramOverrideEditor library={library} workout={workout} overrides={two} onChange={jest.fn()} />,
    );

    await fireEvent.press(screen.getByText('Dips: sets → 3'));
    await rerender(<ProgramOverrideEditor library={library} workout={workout} overrides={[]} onChange={jest.fn()} />);

    // Threw `Cannot read properties of undefined (reading 'config')` out of the press handler, which
    // program-editor.tsx's ModalErrorBoundary turns into the whole editor being replaced, draft gone.
    await fireEvent.press(screen.getByText('Save override'));
    expect(screen.queryByText('Save override')).toBeNull();
  });

  it('saves against the override it opened, not whatever now sits at that index', async () => {
    const { onChange, rendered } = mount(two);
    await rendered;

    await fireEvent.press(screen.getByText('Dips: sets → 3'));
    await fireEvent.changeText(fieldShowing('3'), '6');
    await fireEvent.press(screen.getByText('Save override'));

    expect(onChange).toHaveBeenCalledWith([two[0], { kind: 'exercise', exerciseId: 'dips', config: { sets: 6 } }]);
  });
});
