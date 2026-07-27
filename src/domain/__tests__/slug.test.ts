import { slugify } from '@/domain/slug';

describe('slugify', () => {
  it('lowercases the name', () => {
    expect(slugify('Pull-Ups')).toBe('pull-ups');
  });

  it('collapses spaces and punctuation into single hyphens', () => {
    expect(slugify('Barbell  Row (Pendlay)')).toBe('barbell-row-pendlay');
    expect(slugify("Farmer's Carry")).toBe('farmer-s-carry');
  });

  it('trims leading and trailing hyphens', () => {
    expect(slugify('  -- Overhead Press --  ')).toBe('overhead-press');
  });

  it('keeps digits', () => {
    expect(slugify('Tabata 20/10')).toBe('tabata-20-10');
  });

  /**
   * Known limitation, deliberately not fixed here (see the module-level comment in slug.ts): the
   * character class is ASCII-only, so a name with no ASCII letters or digits collapses to an empty
   * string. Callers surface this as "could not derive an id" rather than silently producing a bad id,
   * so this pins the current, intentional behaviour rather than the desired one.
   */
  it('yields an empty string for a name made only of non-Latin characters', () => {
    expect(slugify('Приседания')).toBe(''); // Cyrillic: "squats"
    expect(slugify('腕立て伏せ')).toBe(''); // Japanese: "push-ups"
  });
});
