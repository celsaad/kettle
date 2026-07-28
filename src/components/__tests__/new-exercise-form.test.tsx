import { fireEvent, screen } from '@testing-library/react-native';

import { NewExerciseForm } from '@/components/new-exercise-form';
import { renderScreen } from '@/test-support/render';

/**
 * The quick-add form embedded in the workout editor's pickers.
 *
 * It duplicates exercise-editor.tsx's form deliberately (the caller's in-progress workout draft is
 * unpersisted local state, so navigating to the real editor would lose it) — and duplication is
 * exactly why it's worth pinning: the same validation hole and the same label bug can reappear here
 * after being fixed there.
 */
function mount() {
  const onCreate = jest.fn();
  const onCancel = jest.fn();
  return { onCreate, onCancel, rendered: renderScreen(<NewExerciseForm onCreate={onCreate} onCancel={onCancel} />) };
}

it('renders its labels as text, not as i18next keys', async () => {
  const { rendered } = mount();
  await rendered;

  // `TYPE_OPTIONS[].label` and `FieldDef.label` both hold key paths. Rendered raw they put
  // "exerciseForm.type.reps" and "exerciseForm.field.sets" on screen.
  expect(screen.getByText('Reps')).toBeTruthy();
  expect(screen.getByText(/^Sets/)).toBeTruthy();
  expect(screen.queryByText(/exerciseForm\./)).toBeNull();
});

it('runs the same config validation as the full editor', async () => {
  const { onCreate, rendered } = mount();
  await rendered;

  await fireEvent.changeText(screen.getByPlaceholderText('e.g. Front Lever'), 'Pull-ups');
  await fireEvent.changeText(screen.getAllByPlaceholderText('0')[0], '0');
  await fireEvent.press(screen.getByText('Create & add'));

  // This form writes to the store just as directly as the full editor, so skipping the check here
  // would leave the 0-sets hole open through the quick-add path.
  expect(screen.getByText('Sets must be at least 1.')).toBeTruthy();
  expect(onCreate).not.toHaveBeenCalled();
});

it('refuses a name that yields no id', async () => {
  const { onCreate, rendered } = mount();
  await rendered;

  await fireEvent.changeText(screen.getByPlaceholderText('e.g. Front Lever'), '🏋️');
  await fireEvent.press(screen.getByText('Create & add'));

  expect(onCreate).not.toHaveBeenCalled();
});

it('hands back a built exercise with numeric config', async () => {
  const { onCreate, rendered } = mount();
  await rendered;

  await fireEvent.changeText(screen.getByPlaceholderText('e.g. Front Lever'), 'Pull-ups');
  const fields = screen.getAllByPlaceholderText('0'); // sets, targetRepsMin, targetRepsMax, weight, restSec
  await fireEvent.changeText(fields[0], '4');
  await fireEvent.changeText(fields[1], '6');
  await fireEvent.changeText(fields[4], '90');
  await fireEvent.press(screen.getByText('Create & add'));

  expect(onCreate).toHaveBeenCalledWith(
    expect.objectContaining({
      id: 'pull-ups',
      name: 'Pull-ups',
      type: 'reps',
      config: expect.objectContaining({ sets: 4, targetRepsMin: 6, restSec: 90 }),
    }),
  );
});

it('switches the field set when the type changes', async () => {
  const { rendered } = mount();
  await rendered;

  await fireEvent.press(screen.getByText('HIIT'));

  // Unlike the full editor, the type stays editable here — there's no existing exercise whose config
  // shape could be invalidated, since nothing has been created yet.
  expect(screen.getByText(/^Work/)).toBeTruthy();
  expect(screen.queryByText(/^Sets/)).toBeNull();
});
