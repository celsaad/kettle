// Rewrites src/content/library-schema.json from schema.ts, for the native assistant brief. A wrapper
// for the same reason as update-conformance.mjs: pnpm runs scripts through cmd on Windows, which has
// no inline env-var syntax.
import { spawnSync } from 'node:child_process';

const result = spawnSync('pnpm', ['jest', 'src/domain/__tests__/library-schema-file.test.ts'], {
  stdio: 'inherit',
  shell: true,
  env: { ...process.env, UPDATE_LIBRARY_SCHEMA: '1' },
});
process.exit(result.status ?? 1);
