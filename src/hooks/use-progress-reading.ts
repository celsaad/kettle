import { useTranslation } from 'react-i18next';

import type { ProgressReading } from '@/domain/format';
import { toDisplayWeight } from '@/domain/units';
import { useUnitSystem } from '@/state/preferences-store';
import type { RecordKind } from '@/state/selectors/records';

/**
 * Turns a progress value into a reading for `formatProgressReading`: kilograms through the unit
 * preference, seconds and reps rounded.
 *
 * Shared by the Getting stronger row and the exercise progress screen it opens, which print the same
 * numbers side by side and must not disagree about their units. `exerciseProgress` deals in
 * kilograms, seconds and reps; the conversion belongs in the view, and this is the one place the
 * views do it.
 */
export function useProgressReading(kind: RecordKind): (value: number) => ProgressReading {
  const { t } = useTranslation();
  const unitSystem = useUnitSystem();
  // Same lookup the runner's load row uses.
  const unit = t(unitSystem === 'imperial' ? 'units.lb' : 'units.kg');

  return (value) => {
    if (kind === 'longestHold') return { kind: 'hold', holdSec: Math.round(value) };
    if (kind === 'heaviestSet') return { kind: 'weight', weight: `${toDisplayWeight(value, unitSystem)} ${unit}` };
    return { kind: 'reps', reps: Math.round(value) };
  };
}
