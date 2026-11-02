// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readdirSync(path: string): string[] };

const { readdirSync } = require('node:fs');

/**
 * The file names under `src/content/<dir>`, sorted. Native reads those directories as they are, while
 * RN reads only what a static import names, so the tests compare the two.
 */
export function contentFiles(dir: string): string[] {
  return readdirSync(`${__dirname}/../content/${dir}`).toSorted();
}
