/**
 * A stand-in for `expo-file-system`, for the one suite that needs `File` to be a *class*.
 *
 * `listSessions` filters the session directory with `entry instanceof File`, so a plain object stub
 * can't stand in for one — the class identity is what the filter tests. That makes this the rare
 * exception to mocking at our own boundary: `session-files.ts` *is* the boundary, and this is the
 * shape it sits on.
 *
 * A module rather than an inline factory for the same reason as `expo-router`: `jest.mock`'s factory
 * is hoisted above every declaration in the file, so a `class` declared beside it is still in its
 * temporal dead zone when the factory runs. `require()` inside the factory is what breaks the cycle:
 *
 *     jest.mock('expo-file-system', () => require('@/test-support/expo-file-system'));
 */
export class File {
  name: string;
  /** The file's contents, or the error reading it should reject with. */
  private readonly body: string | Error;

  constructor(name: string, body: string | Error) {
    this.name = name;
    this.body = body;
  }

  text(): Promise<string> {
    return this.body instanceof Error ? Promise.reject(this.body) : Promise.resolve(this.body);
  }
}

// Exported only so the module shape matches what session-files.ts imports; nothing under test
// constructs one.
// oxlint-disable-next-line typescript/no-extraneous-class -- a shape stand-in has no members by design.
export class Directory {}
