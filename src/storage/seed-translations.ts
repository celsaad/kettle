import type { Library } from '@/domain/types';
import type { LibraryTranslation } from '@/storage/library-translation';
import { libraryWeekKey, localizeLibrary, translationFor } from '@/storage/library-translation';

/**
 * Per-language strings for the seed library, applied over the single English structural definition in
 * `seed-library.ts` on first launch.
 *
 * **Why a string table rather than one `Library` literal per language:** structure is what makes the
 * seed correct — ids wire blocks to exercises, weeks resolve sparsely, overrides repeat per week — and
 * a second literal would fork all of it to change the strings. Here a content edit changes structure
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
 *
 * The table *shape* and the code that applies it live in `library-translation.ts`, shared with the
 * bundled content packs — one implementation, so the seed and the packs cannot come to disagree about
 * which fields a translation may touch.
 */
export type SeedTranslation = LibraryTranslation;

/** The seed's own name for the shared week key, so call sites keep reading as being about the seed. */
export const seedWeekKey = libraryWeekKey;

const pt: SeedTranslation = {
  days: {
    'Day 1': 'Dia 1',
    'Day 2': 'Dia 2',
    'Day 3': 'Dia 3',
    'Day 4': 'Dia 4',
    'Day 5': 'Dia 5',
    'Day 6': 'Dia 6',
    'Day 7': 'Dia 7',
  },
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
        '1|Day 2': 'O descanso é onde a última sessão vira alguma coisa. Caminhe se quiser; nada pesado.',
        '2|Day 1': 'As mesmas três sessões. Busque uma repetição a mais do que registrou na semana 1, em todas as séries.',
        '3|Day 1':
          'Uma quarta série em tudo. Mantenha as repetições onde estavam em vez de forçar as duas coisas ao mesmo tempo.',
        '4|Day 1': 'Semana mais pesada do bloco. Agora leve as repetições em direção ao topo de cada faixa.',
        '4|Day 5':
          'Última sessão do bloco, com uma quarta rodada no finalizador. Concluí-la volta para a semana 1 — repita o bloco com o topo de cada faixa como seu novo piso.',
      },
    },
    'dumbbell-strength': {
      name: 'Força com Halteres · 4 Semanas',
      weekNotes: {
        '1|Day 1':
          'Semana de referência. Escolha cargas com as quais você consiga parar 2 repetições antes da falha e registre-as em cada exercício.',
        '1|Day 2': 'Um dia entre as sessões faz parte do plano, não é uma falha nele.',
        '2|Day 1':
          'A ordem A/B se inverte nesta semana. Adicione o menor incremento que você tiver a tudo em que alcançou o topo da faixa.',
        '3|Day 1': 'Uma quarta série em tudo. Mantenha as cargas onde estão nesta semana.',
        '4|Day 1': 'Semana mais pesada do bloco: quatro séries, e aumente a carga onde a semana 3 pareceu fácil.',
        '4|Day 5':
          'Última sessão do bloco. Concluí-la volta para a semana 1 — repita o bloco a partir das cargas com que você terminou.',
      },
    },
  },
};

const ja: SeedTranslation = {
  /**
   * `1日目` rather than `月曜日`, and **not for ordering.** `sortedProgramWeeks` breaks no ties at
   * all — it sorts on week number and leans on a stable sort to leave a week in the order
   * `seed-library.ts` writes it — so no label can reorder anybody's week, and weekday names are
   * refused here for a plainer reason: a numbered label reads in the order it runs, while
   * `月曜日`…`日曜日` only does for a reader whose week starts where the program's does.
   *
   * (This used to cite a `day.localeCompare` tiebreak, which was real, ordered a calendar week
   * *Friday → Monday → Saturday*, broke past `Day 9`, and was removed. See `next-up.ts`: adding one
   * back would reintroduce the bug, so nothing here should be read as asking for one.)
   */
  days: {
    'Day 1': '1日目',
    'Day 2': '2日目',
    'Day 3': '3日目',
    'Day 4': '4日目',
    'Day 5': '5日目',
    'Day 6': '6日目',
    'Day 7': '7日目',
  },
  exercises: {
    rest: { name: '休憩' },
    pushups: {
      name: '腕立て伏せ',
      notes: '限界の2レップ手前で止めます。全セットで15回に届く日が2回続いたら、より難しいバリエーションに進みましょう。',
    },
    'bodyweight-squats': { name: '自重スクワット' },
    'inverted-rows': {
      name: 'インバーテッドロウ',
      notes: 'テーブルの下か低いバーで行います。可動域の上まで安定して出せるようになったら、足を前に出して負荷を上げます。',
    },
    'split-squats': {
      name: 'スプリットスクワット',
      notes: 'レップ数は片脚あたりです。弱いほうの脚を記録すると、次に超えるべき数字が正直なものになります。',
    },
    'glute-bridge': { name: 'ヒップリフト' },
    plank: {
      name: 'プランク',
      notes: '全セットで45秒に届いたら、範囲全体を5秒引き上げます。',
    },
    'mountain-climbers': { name: 'マウンテンクライマー' },
    'db-goblet-squat': {
      name: 'ダンベル・ゴブレットスクワット',
      notes:
        '重量は最初から入っていません。初回のセッションで自分の重さを決め、全セットで12回に届いたら手持ちのいちばん小さい増分を足していきます。',
    },
    'db-floor-press': {
      name: 'ダンベル・フロアプレス',
      notes: '床から押すのでベンチは要りません。全セットで12回に届いたら重量を足します。',
    },
    'db-row': { name: 'ダンベルロウ', notes: 'レップ数は片側あたりです。' },
    'db-romanian-deadlift': { name: 'ダンベル・ルーマニアンデッドリフト' },
    'db-overhead-press': { name: 'ダンベル・オーバーヘッドプレス' },
    'farmers-carry': { name: 'ファーマーズキャリー', notes: '重量を増やす前に、まず時間を伸ばします。' },
    burpees: { name: 'バーピー' },
    'emom-pushups': {
      name: 'EMOM 腕立て伏せ',
      notes: '毎分の開始で腕立て伏せ8回、残りの時間は休憩です。その1分のうちに終えられなくなったら目標を下げます。',
    },
    'amrap-12-bodyweight': {
      name: 'AMRAP 12',
      notes:
        '1ラウンドは腕立て伏せ10回、自重スクワット15回、インバーテッドロウ10回です。制限時間の計測とラウンド数の記録はアプリが行います。AMRAP には種目リストがないので、動作はこのメモに書いてあります。',
    },
    'easy-cardio': {
      name: '軽い有酸素のクールダウン',
      notes:
        'ランニング、バイク、ローイング、ウォーキング — できるものなら何でも。距離も記録したいなら、設定に距離を追加してください。',
    },
  },
  workouts: {
    'foundations-push': '基礎 · プッシュ',
    'foundations-legs': '基礎 · レッグ',
    'foundations-pull': '基礎 · プル',
    'db-full-body-a': 'ダンベル全身 A',
    'db-full-body-b': 'ダンベル全身 B',
    'mixed-conditioning': 'ミックスコンディショニング',
    'emom-10': 'EMOM 10',
  },
  programs: {
    foundations: {
      name: '基礎 · 4週間 · 器具なし',
      weekNotes: {
        '1|Day 1':
          '基準になる週です。それぞれの範囲のどこに着地するかをそのまま記録してください。まだ上限を狙わなくて大丈夫です。',
        '1|Day 2': '前のセッションが力になるのは休養している間です。歩くくらいは構いませんが、重いことはしないでください。',
        '2|Day 1': '同じ3セッションです。全セットで、第1週に記録した数より1レップ多いところを狙います。',
        '3|Day 1': 'すべて4セットにします。レップ数は据え置きにして、両方を同時に押し上げないようにします。',
        '4|Day 1': 'ブロックでいちばんきつい週です。ここで各範囲の上限までレップ数を押し上げます。',
        '4|Day 5':
          'ブロック最後のセッションで、仕上げのサーキットは4ラウンドです。やり切ると第1週に戻ります。各範囲の上限を新しい下限として、もう一度ブロックを回してください。',
      },
    },
    'dumbbell-strength': {
      name: 'ダンベル筋力 · 4週間',
      weekNotes: {
        '1|Day 1': '基準になる週です。限界の2レップ手前で止められる重量を選び、各エクササイズに書き込んでください。',
        '1|Day 2': 'セッションの間に1日空けるのは計画の一部で、計画の穴ではありません。',
        '2|Day 1':
          'この週は A と B の順番が入れ替わります。範囲の上限に届いたものには、手持ちのいちばん小さい増分を足します。',
        '3|Day 1': 'すべて4セットにします。この週は重量を据え置きにします。',
        '4|Day 1': 'ブロックでいちばんきつい週です。4セットにして、第3週が楽に感じたところは重量を足します。',
        '4|Day 5':
          'ブロック最後のセッションです。やり切ると第1週に戻ります。終えたときの重量から、もう一度ブロックを回してください。',
      },
    },
  },
};

/** Keyed by language, not region, exactly as the locale bundles are: `pt` serves pt-BR and pt-PT. */
export const seedTranslations: Record<string, SeedTranslation> = { pt, ja };

/**
 * The seed library with `language`'s strings applied, or unchanged English for a language we ship no
 * table for. `language` is a tag like `pt-BR`, narrowed to its base subtag by `translationFor`, so
 * callers can hand over whatever i18next is holding.
 */
export function localizeSeed(library: Library, language: string | undefined): Library {
  return localizeLibrary(library, translationFor(seedTranslations, language));
}
