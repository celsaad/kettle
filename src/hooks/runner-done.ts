/**
 * The native runner telling its stub it is done (#302), handed from the shell's message listener in
 * `safe-brownfield.ts` to `app/session-native.tsx`. A module of its own so the stub doesn't import the
 * listener, and the listener needn't know the stub is there: no stub mounted, nobody hears it.
 */
const listeners = new Set<() => void>();

/** Hears every runnerDone from now until the returned function is called. */
export function onRunnerDone(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function emitRunnerDone(): void {
  for (const listener of listeners) listener();
}
