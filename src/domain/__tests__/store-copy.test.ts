/**
 * Play's character limits, asserted against the copy that actually gets uploaded.
 *
 * These went stale silently, which is the whole reason this exists. `store/README.md` annotates each
 * block with its length in the heading — `### Full — en-US (3880)` — and after one editing pass both
 * annotations were wrong and both descriptions were **over 4000**, with nothing anywhere to say so.
 * The same pass produced a release-note block at 507 against a 500 limit. Over-limit copy is a
 * *rejected upload*, not a truncated one, so the cost lands at the worst moment.
 *
 * `AGENTS.md` says never to quote a hand-maintained count. These stay quoted because the headroom is
 * genuinely useful when editing — so the count is checked here instead of trusted, which is the same
 * bargain `format-mirrors.test.ts` strikes with the type tables.
 */
// oxlint-disable-next-line no-underscore-dangle -- node's own global; the name isn't ours to choose.
declare const __dirname: string;
declare function require(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };

const { readFileSync } = require('node:fs');

const repoRoot = `${__dirname}/../../..`;
const listing = readFileSync(`${repoRoot}/store/README.md`, 'utf8');
const changelog = readFileSync(`${repoRoot}/CHANGELOG.md`, 'utf8');

/** The text inside the fenced block that follows a heading, which is what gets pasted into Play. */
function blockUnder(heading: string): string {
  const at = listing.indexOf(heading);
  if (at < 0) throw new Error(`No heading "${heading}" in store/README.md`);
  const open = listing.indexOf('```', at);
  const close = listing.indexOf('```', open + 3);
  return listing.slice(open + 4, close).replace(/\n$/, '');
}

/** The `(3880)` a listing heading carries, which is the claim being checked rather than trusted. */
function declaredLength(heading: string): number {
  const match = /\((\d+)\)/.exec(heading);
  if (!match) throw new Error(`No declared length in "${heading}"`);
  return Number(match[1]);
}

const HEADINGS = {
  short: ['### Short — en-US', '### Short — pt-BR'],
  full: ['### Full — en-US', '### Full — pt-BR'],
};

/** The heading as written, including its count, found by prefix so the count itself isn't hardcoded. */
function headingLine(prefix: string): string {
  const line = listing.split('\n').find((candidate) => candidate.startsWith(prefix));
  if (!line) throw new Error(`No heading starting "${prefix}"`);
  return line;
}

describe('Play listing copy', () => {
  it.each(HEADINGS.short)('%s is within the 80-character short-description limit', (prefix) => {
    expect(blockUnder(prefix).length).toBeLessThanOrEqual(80);
  });

  it.each(HEADINGS.full)('%s is within the 4000-character full-description limit', (prefix) => {
    expect(blockUnder(prefix).length).toBeLessThanOrEqual(4000);
  });

  // The annotation is what an editor reads to decide whether a sentence fits, so a wrong one is worse
  // than none: it invites the edit that breaks the limit.
  it.each([...HEADINGS.short, ...HEADINGS.full])('%s declares its real length', (prefix) => {
    expect(declaredLength(headingLine(prefix))).toBe(blockUnder(prefix).length);
  });

  /**
   * pt-BR runs longer than English for the same content and has come closest to the ceiling twice —
   * once landing at 4048 and once leaving 14 characters spare, which the next edit would have eaten.
   * A floor under the headroom turns that from a recurring surprise into a failing test.
   */
  it.each(HEADINGS.full)('%s keeps room to edit, not just room to upload', (prefix) => {
    expect(4000 - blockUnder(prefix).length).toBeGreaterThanOrEqual(30);
  });
});

describe('Play release notes', () => {
  const blocks: { lang: string; index: number; text: string }[] = [];
  for (const lang of ['en-US', 'pt-BR']) {
    const pattern = new RegExp(`<${lang}>\\n([\\s\\S]*?)\\n</${lang}>`, 'g');
    let match = pattern.exec(changelog);
    let index = 0;
    while (match !== null) {
      index += 1;
      blocks.push({ lang, index, text: match[1] });
      match = pattern.exec(changelog);
    }
  }

  it('finds the tagged blocks at all, so a rename cannot make this suite vacuously pass', () => {
    expect(blocks.length).toBeGreaterThan(0);
  });

  it.each(blocks.map((block) => [`${block.lang} block ${block.index}`, block.text] as const))(
    '%s is within the 500-character limit',
    (_label, text) => {
      expect(text.length).toBeLessThanOrEqual(500);
    },
  );
});
