// Rewrites conformance/*.expected.json from the TS implementation. A wrapper rather than an inline
// `UPDATE_CONFORMANCE=1 jest` because pnpm runs scripts through cmd on Windows, which has no inline
// env-var syntax, and cross-env is a dependency for one line.
import { spawnSync } from 'node:child_process';

const result = spawnSync('pnpm', ['jest', 'src/domain/__tests__/conformance.test.ts'], {
  stdio: 'inherit',
  shell: true,
  env: { ...process.env, UPDATE_CONFORMANCE: '1' },
});
process.exit(result.status ?? 1);
