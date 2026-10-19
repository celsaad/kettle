import { rankByName } from '@/domain/search';

// The matching rules themselves are pinned as data in `conformance/search.yaml`, where a native port is
// held to them too. These are the two checks that can't be written as a case there.
describe('rankByName', () => {
  it('reads the name through the accessor', () => {
    const items = [{ name: 'Bench Press' }, { name: 'Back Squat' }];
    expect(rankByName(items, 'squat', (item) => item.name)).toEqual([{ name: 'Back Squat' }]);
  });

  // The same `ã` can arrive as one code point or as `a` + a combining tilde, depending on the keyboard,
  // and the two look identical — which is exactly why this isn't a YAML case.
  it('matches whether an accent was typed composed or decomposed', () => {
    const composed = 'Flexão'.normalize('NFC');
    const decomposed = 'Flexão'.normalize('NFD');
    expect(composed).not.toBe(decomposed);
    expect(rankByName([composed], decomposed, (name) => name)).toEqual([composed]);
  });
});
