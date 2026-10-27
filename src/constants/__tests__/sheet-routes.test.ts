// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { existsSync(path: string): boolean };

import { isSheetRoute, SHEET_ROUTES } from '@/constants/sheet-routes';

const { existsSync } = require('node:fs');

/**
 * The shell presents a route as its sheet only when RN's report says `modal: true`, which comes from
 * this list. A route left off it would open full screen in the shell with no way to drag it closed;
 * the runner put on it would shrink into a sheet it was never laid out for.
 */
it('counts the modal sheets as sheets, and nothing else', () => {
  expect(isSheetRoute('settings')).toBe(true);
  expect(isSheetRoute('exercise-editor')).toBe(true);
  // The runner is a full-screen modal, RN's own until M6.
  expect(isSheetRoute('session')).toBe(false);
  // A tab's first segment is its group; the root has none at all.
  expect(isSheetRoute('(tabs)')).toBe(false);
  expect(isSheetRoute(undefined)).toBe(false);
});

// A renamed route file would otherwise leave a stale name here, and the renamed route full screen.
it('names only routes that exist', () => {
  const missing = SHEET_ROUTES.filter((name) => !existsSync(`${__dirname}/../../app/${name}.tsx`));

  expect(missing).toEqual([]);
});
