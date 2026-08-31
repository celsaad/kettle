import { fireEvent, screen } from '@testing-library/react-native';
import { changeLanguage } from 'i18next';

import ProgramGuideScreen from '@/app/program-guide';
import { router } from '@/test-support/expo-router';
import { renderScreen } from '@/test-support/render';

/**
 * The program guide is a page of reference prose with two controls on it, and only the controls are
 * worth a test — the prose is deliberately English-only (see the testing plan's out-of-scope list),
 * so there is nothing here for a `pt` run to catch except the buttons themselves.
 *
 * The import hand-off is the case that matters. The guide used to end by *describing* the route —
 * "Library tab → Import → pick your file" — to someone who had just finished writing a program and
 * had nowhere to put it.
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
