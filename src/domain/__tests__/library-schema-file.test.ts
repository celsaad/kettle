import { z } from 'zod';

import { rawLibrarySchema } from '@/domain/schema';

// No node types in this project's tsconfig, as `conformance.test.ts` declares them too.
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): {
  readFileSync(path: string, encoding: 'utf8'): string;
  writeFileSync(path: string, data: string): void;
};

const { readFileSync, writeFileSync } = require('node:fs');

/**
 * `src/content/library-schema.json` is the JSON Schema the assistant brief carries, generated from the
 * validator so it can't drift from what the importer accepts. RN builds it at runtime; native reads
 * this file, since Kotlin can't run zod's `toJSONSchema`. This is what holds the two to one schema.
 *
 * Compared parsed rather than byte for byte, because `pnpm run format` lays the file out its own way
 * after `pnpm run schema:update` writes it.
 */
const path = `${__dirname}/../../content/library-schema.json`;
const generated = z.toJSONSchema(rawLibrarySchema, { io: 'input' });

it('matches the schema the validator generates', () => {
  if (process.env.UPDATE_LIBRARY_SCHEMA === '1') writeFileSync(path, `${JSON.stringify(generated, null, 2)}\n`);

  // A failure here after a change to `schema.ts` means: run `pnpm run schema:update`, then `format`.
  expect(JSON.parse(readFileSync(path, 'utf8'))).toEqual(JSON.parse(JSON.stringify(generated)));
});
