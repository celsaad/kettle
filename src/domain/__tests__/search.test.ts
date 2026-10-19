import { filterByName, rankByName } from '@/domain/search';

// Written as data rather than as `it()` blocks so a native port can load the same file and be held to
// exactly these results — see the note at the top of `search.ts`.
import cases from './search-cases.json';

const identity = (name: string) => name;

describe('rankByName', () => {
  it.each(cases)('$why', ({ query, names, expected }) => {
    expect(rankByName(names, query, identity)).toEqual(expected);
  });

  it('reads the name through the accessor', () => {
    const items = [{ name: 'Bench Press' }, { name: 'Back Squat' }];
    expect(rankByName(items, 'squat', (item) => item.name)).toEqual([{ name: 'Back Squat' }]);
  });

  // The same `ã` can arrive as one code point or as `a` + a combining tilde, depending on the keyboard.
  it('matches whether an accent was typed composed or decomposed', () => {
    expect(rankByName(['Flexão'.normalize('NFC')], 'Flexão'.normalize('NFD'), identity)).toHaveLength(1);
  });
});

describe('filterByName', () => {
  it.each(cases)('$why — same matches, original order', ({ query, names, expected }) => {
    expect(filterByName(names, query, identity)).toEqual(names.filter((name) => expected.includes(name)));
  });
});
