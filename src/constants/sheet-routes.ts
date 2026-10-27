/**
 * The routes presented as modal sheets: in the native shell as its sheet (M3), everywhere else as
 * RN's own modal. `src/app/_layout.tsx` registers each one, and tells the shell whether the focused
 * route is one. The runner is presented modally too, but full screen rather than as a sheet, and it
 * stays RN's until M6, so it isn't one of these.
 */
export const SHEET_ROUTES = [
  'import',
  'exercise-editor',
  'workout-editor',
  'analytics',
  'exercise-progress',
  'program-detail',
  'program-editor',
  'program-guide',
  'session-editor',
  'settings',
  'support',
] as const;

/** Whether the route whose first segment is [segment] is one of the sheets. */
export function isSheetRoute(segment: string | undefined): boolean {
  return (SHEET_ROUTES as readonly string[]).includes(segment ?? '');
}
