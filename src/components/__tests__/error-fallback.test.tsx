import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';

import { ModalErrorBoundary, RouteErrorBoundary } from '@/components/error-fallback';
import { router } from '@/test-support/expo-router';
import { renderScreen } from '@/test-support/render';

/**
 * What a route's `ErrorBoundary` export gets handed is just `{ error, retry }`, so these render the
 * two shared boundaries exactly as expo-router's `<Try>` would. What's worth pinning is that each
 * offers the escape that actually works for its kind of route — a tab can only re-render, a modal can
 * be dismissed — and that the error's own message reaches the screen, since with nothing phoning home
 * it's the only diagnostic a user can report.
 */
jest.mock('expo-router', () => require('@/test-support/expo-router'));

const error = new Error('Cannot read properties of undefined');

describe('RouteErrorBoundary', () => {
  it('shows the failure and offers a retry', async () => {
    const retry = jest.fn(() => Promise.resolve());
    await renderScreen(<RouteErrorBoundary error={error} retry={retry} />);

    expect(screen.getByText('Something went wrong')).toBeTruthy();
    expect(screen.getByText('Cannot read properties of undefined')).toBeTruthy();

    await fireEvent.press(screen.getByLabelText('Try again'));

    expect(retry).toHaveBeenCalled();
  });

  // A tab can't be dismissed — offering a close that did nothing would be worse than no control.
  it('offers nothing but the retry', async () => {
    await renderScreen(<RouteErrorBoundary error={error} retry={jest.fn(() => Promise.resolve())} />);

    expect(screen.queryByLabelText('Close')).toBeNull();
  });
});

describe('ModalErrorBoundary', () => {
  /**
   * Closing is primary here because it genuinely recovers: a render throw leaves the stores untouched,
   * so dismissing lands the user back in a working app rather than re-rendering the screen that broke.
   */
  it('dismisses the modal', async () => {
    await renderScreen(<ModalErrorBoundary error={error} retry={jest.fn(() => Promise.resolve())} />);

    await fireEvent.press(screen.getByLabelText('Close'));

    expect(router.back).toHaveBeenCalled();
  });

  it('keeps a retry as the secondary action', async () => {
    const retry = jest.fn(() => Promise.resolve());
    await renderScreen(<ModalErrorBoundary error={error} retry={retry} />);

    await fireEvent.press(screen.getByLabelText('Try again'));

    expect(retry).toHaveBeenCalled();
    expect(router.back).not.toHaveBeenCalled();
  });
});

/**
 * Driven in `pt`, the only way to tell a keyed string from a hardcoded English one — `t()` and the
 * literal render identically under an English locale. This screen appears at the worst possible
 * moment, so shipping it half-translated would be a poor place to find that out.
 */
it('translates the fallback', async () => {
  await changeLanguage('pt');
  await renderScreen(<ModalErrorBoundary error={error} retry={jest.fn(() => Promise.resolve())} />);

  expect(screen.getByText('Algo deu errado')).toBeTruthy();
  expect(screen.getByLabelText('Fechar')).toBeTruthy();
  expect(screen.getByLabelText('Tentar de novo')).toBeTruthy();
});
