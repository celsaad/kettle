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

  // Regression: the character class used to be ASCII-only, so an accent was punctuation. Every
  // accented name came out hyphenated and truncated — in Portuguese, one of the two languages the
  // app ships in.
  it('strips Latin diacritics rather than hyphenating them', () => {
    expect(slugify('Flexão')).toBe('flexao');
    expect(slugify('Élévation du Genou')).toBe('elevation-du-genou');
  });

  // Regression: these used to slugify to '', which callers surfaced as "could not derive an id" —
  // the app simply could not name an exercise in most of the world's scripts.
  it('keeps a name written in a non-Latin script', () => {
    expect(slugify('Приседания')).toBe('приседания'); // Cyrillic: "squats"
    expect(slugify('腕立て伏せ')).toBe('腕立て伏せ'); // Japanese: "push-ups"
    expect(slugify('Планка 60 сек')).toBe('планка-60-сек');
  });

  /**
   * Devanagari and Thai vowel signs are combining marks, but they carry a syllable rather than an
   * accent — so the diacritic strip is limited to U+0300–U+036F and marks are kept in the character
   * class. Stripping `\p{M}` wholesale would give 'यग', and excluding it from the keep-class 'य-ग';
   * both are a different word.
   */
  it('keeps combining marks that are letters rather than accents', () => {
    expect(slugify('योग')).toBe('योग'); // Hindi: "yoga"
    expect(slugify('ท่าแพลงก์')).toBe('ท่าแพลงก์'); // Thai: "plank"
  });

  // Ids are the join key across the library, so the same name must give the same id however the
  // device's keyboard or IME chose to encode it — the two forms below look identical on screen.
  it('gives one id whether an accent was typed composed or decomposed', () => {
    const composed = 'Flexão'.normalize('NFC');
    const decomposed = 'Flexão'.normalize('NFD');
    expect(composed).not.toBe(decomposed);
    expect(slugify(decomposed)).toBe(slugify(composed));
  });

  // Still the one case with nothing to derive an id from; callers keep their "could not derive an
  // id" error for it, and workout-editor/program-editor pin that they show it.
  //
  // The emoji are the sharp edge: an emoji carries U+FE0F, which is a combining *mark*, so a rule
  // that kept marks unconditionally would hand back an id of invisible characters — non-empty, so
  // past the guard, and unaddressable ever after.
  it('yields an empty string for a name with no letters or digits at all', () => {
    expect(slugify('---')).toBe('');
    expect(slugify('🔥🔥')).toBe('');
    expect(slugify('🏋️🏋️')).toBe('');
  });

  it('drops an emoji from a name that has real words too', () => {
    expect(slugify('Push 🏋️ day')).toBe('push-day');
  });
});
