import type { Exercise, Library, Program } from '@/domain/types';

/**
 * Per-language strings for the seed library, applied over the single English structural definition in
 * `seed-library.ts` on first launch.
 *
 * **Why a string table rather than one `Library` literal per language:** structure is what makes the
 * seed correct — ids wire blocks to exercises, weeks resolve sparsely, overrides repeat per week — and
 * a second literal would fork all of it to change 51 strings. Here a content edit changes structure
 * once, and `seed-library.test.ts` fails the language that wasn't updated with it.
 *
 * **Why not `en.json`/`pt.json`:** the seed is written to `exercises.yaml` and becomes the user's own
 * data at that moment. Strings in the locale bundles are re-rendered on every language change, which
 * would rename exercises the user has since edited and logged against — exactly what the
 * never-translate-user-data rule exists to prevent. These are read once, at seed time, and frozen.
 *
 * The content bar is the seed's own (see the decision log): `notes` describe the app's progression
 * model and nothing else — no form cues, no injury or diet advice — so a translation stays a
 * translation.
 */
export type SeedTranslation = {
  /**
   * Day labels are a closed set shared by every seeded program, so they map by their English label
   * rather than being repeated on all 24 week entries. Any replacement has to sort in training order:
   * `nextWeekAfter` walks a multi-day week by `day.localeCompare`, so `Dia 1`/`Dia 2`/`Dia 3` is fine
   * and weekday names would not be.
   */
  days: Record<string, string>;
  exercises: Record<string, { name: string; notes?: string }>;
  /** Workouts have no translatable text beyond their name, so the value is the name itself. */
  workouts: Record<string, string>;
  programs: Record<string, { name: string; weekNotes?: Record<string, string> }>;
};

/**
 * Program weeks are the one thing that can't be keyed by id: `ProgramWeek` has none, and a week is
 * addressed by its `(week, day)` pair — the same pair the schema requires to be unique within a
 * program. Keyed on the **English** day label, since that's what the structural definition holds.
 */
export function seedWeekKey(week: number, day: string | undefined): string {
  return `${week}|${day ?? ''}`;
}

const pt: SeedTranslation = {
  days: { 'Day 1': 'Dia 1', 'Day 2': 'Dia 2', 'Day 3': 'Dia 3' },
  exercises: {
    rest: { name: 'Descanso' },
    pushups: {
      name: 'Flexões',
      notes:
        'Pare 2 repetições antes da falha. Faça 15 em todas as séries duas vezes antes de passar para uma variação mais difícil.',
    },
    'bodyweight-squats': { name: 'Agachamento Livre' },
    'inverted-rows': {
      name: 'Remada Invertida',
      notes: 'Sob uma mesa ou uma barra baixa. Afaste mais os pés para dificultar quando dominar o topo da faixa.',
    },
    'split-squats': {
      name: 'Afundo',
      notes:
        'As repetições são por perna — registre o lado mais fraco, para que o número a superar da próxima vez seja o honesto.',
    },
    'glute-bridge': { name: 'Ponte de Glúteos' },
    plank: {
      name: 'Prancha',
      notes: 'Aumente toda a faixa em 5s quando alcançar 45s em todas as séries.',
    },
    'mountain-climbers': { name: 'Escalador' },
    'db-goblet-squat': {
      name: 'Agachamento Goblet com Halter',
      notes:
        'Nenhuma carga vem definida — escolha a sua na primeira sessão e depois adicione o menor incremento que você tiver quando alcançar 12 em todas as séries.',
    },
    'db-floor-press': {
      name: 'Supino no Chão com Halteres',
      notes: 'Feito no chão, então dispensa banco. Aumente a carga quando alcançar 12 em todas as séries.',
    },
    'db-row': { name: 'Remada Unilateral com Halter', notes: 'As repetições são por lado.' },
    'db-romanian-deadlift': { name: 'Levantamento Terra Romeno com Halteres' },
    'db-overhead-press': { name: 'Desenvolvimento com Halteres' },
    'farmers-carry': { name: 'Caminhada do Fazendeiro', notes: 'Aumente o tempo antes de aumentar a carga.' },
    burpees: { name: 'Burpees' },
    'emom-pushups': {
      name: 'EMOM de Flexões',
      notes:
        'A cada minuto, no minuto: 8 flexões e descanse o que sobrar do minuto. Reduza a meta quando parar de concluí-la dentro do minuto.',
    },
    'amrap-12-bodyweight': {
      name: 'AMRAP 12',
      notes:
        'Uma rodada são 10 flexões, 15 agachamentos livres e 10 remadas invertidas. O app cronometra o limite e conta as rodadas — os movimentos ficam aqui nas notas, já que um AMRAP não tem lista de movimentos própria.',
    },
    'easy-cardio': {
      name: 'Cardio Leve de Volta à Calma',
      notes:
        'Corrida, bicicleta, remo, caminhada — o que você tiver. Adicione uma distância à configuração se quiser acompanhá-la também.',
    },
  },
  workouts: {
    'foundations-push': 'Fundamentos · Empurrar',
    'foundations-legs': 'Fundamentos · Pernas',
    'foundations-pull': 'Fundamentos · Puxar',
    'db-full-body-a': 'Corpo Inteiro com Halteres A',
    'db-full-body-b': 'Corpo Inteiro com Halteres B',
    'mixed-conditioning': 'Condicionamento Misto',
    'emom-10': 'EMOM 10',
  },
  programs: {
    foundations: {
      name: 'Fundamentos · 4 Semanas · Sem Equipamento',
      weekNotes: {
        '1|Day 1': 'Semana de referência. Registre onde você realmente chega em cada faixa — ainda não persiga o topo dela.',
        '2|Day 1': 'As mesmas três sessões. Busque uma repetição a mais do que registrou na semana 1, em todas as séries.',
        '3|Day 1':
          'Uma quarta série em tudo. Mantenha as repetições onde estavam em vez de forçar as duas coisas ao mesmo tempo.',
        '4|Day 1': 'Semana mais pesada do bloco. Agora leve as repetições em direção ao topo de cada faixa.',
        '4|Day 3':
          'Última sessão do bloco, com uma quarta rodada no finalizador. Concluí-la volta para a semana 1 — repita o bloco com o topo de cada faixa como seu novo piso.',
      },
    },
    'dumbbell-strength': {
      name: 'Força com Halteres · 4 Semanas',
      weekNotes: {
        '1|Day 1':
          'Semana de referência. Escolha cargas com as quais você consiga parar 2 repetições antes da falha e registre-as em cada exercício.',
        '2|Day 1':
          'A ordem A/B se inverte nesta semana. Adicione o menor incremento que você tiver a tudo em que alcançou o topo da faixa.',
        '3|Day 1': 'Uma quarta série em tudo. Mantenha as cargas onde estão nesta semana.',
        '4|Day 1': 'Semana mais pesada do bloco: quatro séries, e aumente a carga onde a semana 3 pareceu fácil.',
        '4|Day 3':
          'Última sessão do bloco. Concluí-la volta para a semana 1 — repita o bloco a partir das cargas com que você terminou.',
      },
    },
  },
};

/** Keyed by language, not region, exactly as the locale bundles are: `pt` serves pt-BR and pt-PT. */
export const seedTranslations: Record<string, SeedTranslation> = { pt };

function localizeExercise(exercise: Exercise, strings: SeedTranslation): Exercise {
  const text = strings.exercises[exercise.id];
  if (!text) return exercise;
  // `notes` falls back rather than being dropped: a half-finished table should degrade to English on
  // the strings it's missing, not silently delete the seed's coaching model. The parity test is what
  // stops that from shipping; this is what keeps it harmless if it does.
  return { ...exercise, name: text.name, notes: text.notes ?? exercise.notes };
}

function localizeProgram(program: Program, strings: SeedTranslation): Program {
  const text = strings.programs[program.id];
  if (!text) return program;
  return {
    ...program,
    name: text.name,
    weeks: program.weeks.map((week) => ({
      ...week,
      day: week.day === undefined ? undefined : (strings.days[week.day] ?? week.day),
      notes: text.weekNotes?.[seedWeekKey(week.week, week.day)] ?? week.notes,
    })),
  };
}

/**
 * The seed library with `language`'s strings applied, or unchanged English for a language we ship no
 * table for. `language` is a tag like `pt-BR`, narrowed to its base subtag here so callers can hand
 * over whatever i18next is holding.
 *
 * Only `name`, `notes` and `day` are touched. Everything else — ids, types, configs, block structure,
 * week layout, overrides — is language-agnostic by construction, which is the whole reason this is a
 * string table and not a second library.
 */
export function localizeSeed(library: Library, language: string | undefined): Library {
  const strings = seedTranslations[(language ?? '').split('-')[0]];
  if (!strings) return library;

  return {
    ...library,
    exercises: library.exercises.map((exercise) => localizeExercise(exercise, strings)),
    workouts: library.workouts.map((workout) => ({ ...workout, name: strings.workouts[workout.id] ?? workout.name })),
    programs: library.programs.map((program) => localizeProgram(program, strings)),
  };
}
