import { FontFaces } from '@/constants/font-faces';
import { Fonts } from '@/constants/theme';

describe('FontFaces', () => {
  it('loads exactly the faces theme.json names, so RN draws what native bundles', () => {
    expect(Object.keys(FontFaces).toSorted()).toEqual([...new Set(Object.values(Fonts))].toSorted());
  });
});
