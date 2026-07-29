import { fireEvent, screen } from '@testing-library/react-native';
// Named import rather than the default: `i18next.changeLanguage(...)` trips
// `import/no-named-as-default-member`, and that accepted-warning pile is meant to stop growing.
import { changeLanguage } from 'i18next';

import { ModalHeader } from '@/components/modal-header';
import { renderScreen } from '@/test-support/render';

/**
 * The close button is the only way out of a modal that isn't the OS back gesture, and it's icon-only
 * — a bare `×` with nothing for a screen reader to fall back on — so its label is the whole control
 * as far as assistive tech is concerned. This component backs every modal in the app, which is why
 * one hardcoded string here was worth its own test.
 */
it('closes when pressed', async () => {
  const onClose = jest.fn();
  await renderScreen(<ModalHeader onClose={onClose} />);

  await fireEvent.press(screen.getByLabelText('Close'));

  expect(onClose).toHaveBeenCalled();
});

/**
 * Driven in `pt` deliberately. An English-locale assertion cannot catch a hardcoded English label —
 * `t('common.close')` and the literal `"Close"` render identically — which is exactly how this one
 * survived: every modal announced "Close" to a Portuguese screen-reader user.
 */
it('translates the close label', async () => {
  await changeLanguage('pt');
  await renderScreen(<ModalHeader onClose={jest.fn()} />);

  expect(screen.getByLabelText('Fechar')).toBeTruthy();
});
