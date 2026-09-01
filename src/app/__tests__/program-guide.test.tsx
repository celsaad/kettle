import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import ProgramGuideScreen from '@/app/program-guide';
import { router } from '@/test-support/expo-router';
import { renderScreen } from '@/test-support/render';

/**
 * The program guide: a page of reference prose with two controls on it, and — since D5 of the
 * import-prominence plan — a locale bundle of its own. Both halves are worth a case, for unrelated
 * reasons.
 *
 * The import hand-off is the one that matters most. The guide used to end by *describing* the route
 * — "Library tab → Import → pick your file" — to someone who had just finished writing a program and
 * had nowhere to put it.
 *
 * The prose cases guard the two things about translating a reference page that a wording change
 * can't tell you it broke: the code spans, and the samples that must not follow the UI language. See
 * the `describe` below.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

it('hands off to import rather than describing where it is', async () => {
  await renderScreen(<ProgramGuideScreen />);
  await fireEvent.press(screen.getByText('Open import'));

  // `replace`, not `push`: both screens are modals, so pushing would leave the reader two deep and
  // drop them back mid-scroll in the guide on dismiss. Replacing lands them on Programs instead,
  // which is where the program they just imported has appeared.
  expect(router.replace).toHaveBeenCalledWith('/import');
  expect(router.push).not.toHaveBeenCalled();
});

it('still closes', async () => {
  await renderScreen(<ProgramGuideScreen />);
  await fireEvent.press(screen.getByText('Done'));

  expect(router.back).toHaveBeenCalled();
});

it('translates its controls', async () => {
  await changeLanguage('pt');

  await renderScreen(<ProgramGuideScreen />);

  expect(screen.getByText('Abrir importação')).toBeTruthy();
  expect(screen.getByText('Concluir')).toBeTruthy();
});

/**
 * The prose, which was 226 lines of hardcoded English until D5 of the import-prominence plan.
 *
 * Two things are worth pinning and neither is the wording. First that `<c>` — the code-span marker
 * inside every string with a YAML field name in it — is consumed by `Trans` rather than rendered as
 * text; a missing `components` prop shows the reader literal angle brackets. Second that the YAML
 * samples are *not* translated: they are the file the reader is about to write, and a localised
 * `rest_day` would be refused on import.
 */
describe('the prose', () => {
  it('renders a code span as part of its sentence, not as markup', async () => {
    await renderScreen(<ProgramGuideScreen />);

    // The composed text of the paragraph, tokens included — which is only what this reads if the
    // tags were consumed and their contents rendered in place.
    expect(screen.getByText(/A week entry with rest_day: true and no workout is a scheduled day off/)).toBeTruthy();
    expect(screen.queryByText(/<c>/)).toBeNull();
  });

  it('is translated, sample YAML aside', async () => {
    await changeLanguage('pt');

    await renderScreen(<ProgramGuideScreen />);

    expect(screen.getByText('3. Campos')).toBeTruthy();
    expect(screen.getByText(/Um programa aponta para treinos pelo id/)).toBeTruthy();
    expect(screen.queryByText(/<c>/)).toBeNull();

    // The sample stays exactly as the importer would need it: English notes and all, since it is the
    // file's own text rather than the page's.
    // Twice, and that is the assertion: once in the Portuguese sentence and once in the sample. A
    // YAML key is not a word, so it reads identically in both places in every locale.
    expect(screen.getAllByText(/rest_day: true/)).toHaveLength(2);
    expect(screen.getByText(/notes: Walk, stretch, nothing heavy\./)).toBeTruthy();
  });
});
