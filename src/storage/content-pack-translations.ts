import type { LibraryTranslation } from '@/storage/library-translation';

/**
 * Per-language strings for the bundled content packs, applied over the single English structural
 * definition in `content-packs.ts` at the moment a pack is merged.
 *
 * Same shape and same reasoning as `seed-translations.ts`, and for the same two reasons: structure is
 * what makes a library correct and must stay single-sourced, and a pack's strings become the user's
 * own data the instant they land — so they are read once, at merge time, and frozen. Putting them in
 * `en.json`/`pt.json` instead would re-render them on every language change and rename exercises
 * somebody had already edited and logged against.
 *
 * The pack's *row* on the import screen is the opposite case and does live in the locale bundles
 * (`import.packs.<id>`): that text is never written anywhere, so it is free to follow the UI language.
 *
 * `content-packs.test.ts` fails a language that a content edit left behind, in both directions — a
 * missing string and a stale key pointing at content that was renamed away.
 */

const steadyStrengthPt: LibraryTranslation = {
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
    'ss-rest': { name: 'Descanso' },
    'ss-sit-to-stand': {
      name: 'Levantar da Cadeira',
      notes:
        'De pé a partir de uma cadeira e sentar de novo. Faça 12 em todas as séries duas vezes antes de o programa acrescentar uma quarta.',
    },
    'ss-wall-pushup': {
      name: 'Flexões na Parede',
      notes: 'Afaste mais os pés da parede para dificultar quando dominar 15 em todas as séries.',
    },
    'ss-heel-raises': {
      name: 'Elevação de Calcanhares',
      notes:
        'O apoio é o encosto de uma cadeira ou uma bancada. Tire uma das mãos do apoio antes de aumentar as repetições.',
    },
    'ss-seated-march': {
      name: 'Marcha Sentada',
      notes: 'Uma esquerda e uma direita são duas repetições, então o número que você registra conta as duas pernas.',
    },
    'ss-standing-balance': {
      name: 'Equilíbrio em Pé',
      notes: 'Mantido por lado. Aumente toda a faixa em 5s quando alcançar 30s em todas as séries.',
    },
    'ss-band-row': {
      name: 'Remada com Elástico',
      notes:
        'O elástico é a carga, então um passo para trás é o menor incremento que você tem quando alcançar 12 em todas as séries.',
    },
    'ss-hip-hinge': {
      name: 'Flexão de Quadril',
      notes: 'Mãos no encosto de uma cadeira. Desça as mãos pelo encosto conforme a amplitude aumenta.',
    },
    'ss-walk': {
      name: 'Caminhada',
      notes: 'Quinze minutos para começar. Adicione uma distância à configuração se quiser acompanhá-la também.',
    },
  },
  workouts: {
    'ss-strength-a': 'Firme · Força A',
    'ss-strength-b': 'Firme · Força B',
    'ss-balance-walk': 'Firme · Equilíbrio e Caminhada',
  },
  programs: {
    'ss-4-weeks': {
      name: 'Firme e Forte · 4 Semanas',
      weekNotes: {
        '1|Day 1':
          'Semana de referência. Registre onde você realmente chega em cada faixa — a parte de baixo dela é uma resposta legítima.',
        '2|Day 1': 'As mesmas três sessões. Uma repetição a mais do que na semana 1, em todas as séries.',
        '3|Day 1':
          'Uma quarta série nos dias de força. Mantenha as repetições onde estavam em vez de forçar as duas coisas ao mesmo tempo.',
        '4|Day 1': 'Última semana do bloco. Agora leve as repetições em direção ao topo de cada faixa.',
        '4|Day 5': 'Concluir isto volta para a semana 1 — repita o bloco com o topo de cada faixa como seu novo piso.',
      },
    },
  },
};

const steadyStrengthJa: LibraryTranslation = {
  // `1日目` rather than a weekday name, for the same reason the seed uses it — which is legibility,
  // not ordering. Weeks run in the order `content-packs.ts` writes them and no label is ever read
  // for sorting (see `next-up.ts`); a numbered label simply reads in the order it runs.
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
    'ss-rest': { name: '休憩' },
    'ss-sit-to-stand': {
      name: '椅子からの立ち座り',
      notes: '椅子から立ち、また座ります。全セットで12回に届く日が2回続いてから、プログラムが4セット目を足します。',
    },
    'ss-wall-pushup': {
      name: '壁腕立て伏せ',
      notes: '全セットで15回を安定して出せるようになったら、足を壁から遠ざけて負荷を上げます。',
    },
    'ss-heel-raises': {
      name: 'かかと上げ',
      notes: '椅子の背やカウンターが支えです。回数を増やす前に、支えから片手を離します。',
    },
    'ss-seated-march': {
      name: '座ったままの足踏み',
      notes: '左右で1回ずつが2レップなので、記録する数は両脚の合計です。',
    },
    'ss-standing-balance': {
      name: '片脚立ちバランス',
      notes: '片側ずつ保持します。全セットで30秒に届いたら、範囲全体を5秒引き上げます。',
    },
    'ss-band-row': {
      name: 'バンドロウ',
      notes: 'バンドが負荷なので、全セットで12回に届いたら一歩下がるのがいちばん小さい増分です。',
    },
    'ss-hip-hinge': {
      name: 'ヒップヒンジ',
      notes: '椅子の背に手を置きます。可動域が広がるにつれて、手を背の下のほうへ移していきます。',
    },
    'ss-walk': {
      name: 'ウォーキング',
      notes: 'まずは15分から。距離も記録したいなら、設定に距離を追加してください。',
    },
  },
  workouts: {
    'ss-strength-a': 'ステディ · 筋力 A',
    'ss-strength-b': 'ステディ · 筋力 B',
    'ss-balance-walk': 'ステディ · バランスと歩行',
  },
  programs: {
    'ss-4-weeks': {
      name: 'ステディ＆ストロング · 4週間',
      weekNotes: {
        '1|Day 1': '基準になる週です。それぞれの範囲のどこに着地するかをそのまま記録してください。下限でも立派な答えです。',
        '2|Day 1': '同じ3セッションです。全セットで、第1週より1レップ多いところを狙います。',
        '3|Day 1': '筋力の日は4セットにします。レップ数は据え置きにして、両方を同時に押し上げないようにします。',
        '4|Day 1': 'ブロック最後の週です。ここで各範囲の上限までレップ数を押し上げます。',
        '4|Day 5': 'やり切ると第1週に戻ります。各範囲の上限を新しい下限として、もう一度ブロックを回してください。',
      },
    },
  },
};

const barbellGymPt: LibraryTranslation = {
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
    'gym-rest': { name: 'Descanso' },
    'gym-back-squat': {
      name: 'Agachamento com Barra',
      notes:
        'Nenhuma carga vem definida — escolha a sua na primeira sessão e depois adicione o menor par de anilhas da academia quando alcançar 8 em todas as séries.',
    },
    'gym-bench-press': {
      name: 'Supino Reto',
      notes: 'Aumente a carga quando alcançar 8 em todas as séries, não apenas na melhor delas.',
    },
    'gym-deadlift': {
      name: 'Levantamento Terra',
      notes:
        'Uma faixa mais curta do que a do resto do bloco, então o topo dela chega mais cedo. Aumente a carga com 5 em todas as séries.',
    },
    'gym-overhead-press': {
      name: 'Desenvolvimento Militar',
      notes: 'O menor incremento que você tem já é grande aqui. Espere ficar no topo da faixa por um tempo.',
    },
    'gym-barbell-row': { name: 'Remada Curvada com Barra' },
    'gym-pullups': {
      name: 'Barra Fixa',
      notes:
        'Registre as repetições assistidas como repetições. O número que você vai superar da próxima vez é seu de qualquer forma.',
    },
    'gym-cooldown': {
      name: 'Volta à Calma',
      notes:
        'Bicicleta, remo, caminhada — o que estiver livre. Adicione uma distância à configuração se quiser acompanhá-la também.',
    },
  },
  workouts: {
    'gym-day-a': 'Academia · Dia A',
    'gym-day-b': 'Academia · Dia B',
  },
  programs: {
    'gym-4-weeks': {
      name: 'Academia com Barra · 4 Semanas',
      weekNotes: {
        '1|Day 1':
          'Semana de referência. Escolha cargas com as quais você consiga parar 2 repetições antes da falha e registre-as em cada exercício.',
        '2|Day 1':
          'A ordem A/B se inverte nesta semana. Adicione o menor incremento que você tiver a tudo em que alcançou o topo da faixa.',
        '3|Day 1': 'Uma quarta série nos exercícios principais. Mantenha as cargas onde estão nesta semana.',
        '4|Day 1': 'Semana mais pesada do bloco: quatro séries, e aumente a carga onde a semana 3 pareceu fácil.',
        '4|Day 5':
          'Última sessão do bloco. Concluí-la volta para a semana 1 — repita o bloco a partir das cargas com que você terminou.',
      },
    },
  },
};

const barbellGymJa: LibraryTranslation = {
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
    'gym-rest': { name: '休憩' },
    'gym-back-squat': {
      name: 'バックスクワット',
      notes:
        '重量は最初から入っていません。初回のセッションで自分の重さを決め、全セットで8回に届いたらジムにあるいちばん小さいプレートを1組足します。',
    },
    'gym-bench-press': {
      name: 'ベンチプレス',
      notes: '調子のよい1セットではなく、全セットで8回に届いてから重量を足します。',
    },
    'gym-deadlift': {
      name: 'デッドリフト',
      notes: 'ほかの種目より範囲が狭いので、上限に届くのが早くなります。全セットで5回に届いたら重量を足します。',
    },
    'gym-overhead-press': {
      name: 'オーバーヘッドプレス',
      notes: 'ここでは手持ちのいちばん小さい増分でも大きく感じます。しばらく上限に留まるつもりでいてください。',
    },
    'gym-barbell-row': { name: 'バーベルロウ' },
    'gym-pullups': {
      name: '懸垂',
      notes: '補助ありの回数もそのまま記録します。次に超えるべき数字は、どちらにしてもあなた自身のものです。',
    },
    'gym-cooldown': {
      name: 'クールダウン',
      notes:
        'バイク、ローイング、ウォーキング — 空いているもので構いません。距離も記録したいなら、設定に距離を追加してください。',
    },
  },
  workouts: {
    'gym-day-a': 'ジム · A日',
    'gym-day-b': 'ジム · B日',
  },
  programs: {
    'gym-4-weeks': {
      name: 'バーベルジム · 4週間',
      weekNotes: {
        '1|Day 1': '基準になる週です。限界の2レップ手前で止められる重量を選び、各種目に書き込んでください。',
        '2|Day 1':
          'この週は A と B の順番が入れ替わります。範囲の上限に届いたものには、手持ちのいちばん小さい増分を足します。',
        '3|Day 1': 'メイン種目を4セットにします。この週は重量を据え置きにします。',
        '4|Day 1': 'ブロックでいちばんきつい週です。4セットにして、第3週が楽に感じたところは重量を足します。',
        '4|Day 5':
          'ブロック最後のセッションです。やり切ると第1週に戻ります。終えたときの重量から、もう一度ブロックを回してください。',
      },
    },
  },
};

const kettlebellPt: LibraryTranslation = {
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
    'kb-rest': { name: 'Descanso' },
    'kb-swing': {
      name: 'Swing com Kettlebell',
      notes:
        'Trabalho e descanso são de 30s cada, então o bloco inteiro dura oito minutos. Aumente as rodadas antes de aumentar a carga.',
    },
    'kb-goblet-squat': {
      name: 'Agachamento Goblet com Kettlebell',
      notes: 'Um único kettlebell junto ao peito. Aumente a carga quando alcançar 12 em todas as séries.',
    },
    'kb-clean-press': {
      name: 'Clean e Desenvolvimento',
      notes:
        'As repetições são por lado — registre o lado mais fraco, para que o número a superar da próxima vez seja o honesto.',
    },
    'kb-single-leg-deadlift': { name: 'Levantamento Terra Unilateral', notes: 'As repetições são por perna.' },
    'kb-row': { name: 'Remada com Kettlebell', notes: 'As repetições são por lado.' },
    'kb-halo': { name: 'Halo', notes: 'Um círculo para cada lado é uma repetição.' },
    'kb-carry': { name: 'Caminhada em Rack', notes: 'Aumente o tempo antes de aumentar a carga.' },
  },
  workouts: {
    'kb-strength-a': 'Kettlebell · Força A',
    'kb-strength-b': 'Kettlebell · Força B',
    'kb-swing-day': 'Kettlebell · Swings',
  },
  programs: {
    'kb-4-weeks': {
      name: 'Kettlebell Essencial · 4 Semanas',
      weekNotes: {
        '1|Day 1': 'Semana de referência. Um kettlebell basta — registre o peso dele em cada exercício conforme avança.',
        '2|Day 1': 'As mesmas três sessões. Supere a semana 1 em uma repetição por série antes de mexer na carga.',
        '3|Day 1': 'Uma quarta série nos dias de força, e mais duas rodadas de swings.',
        '4|Day 1': 'Semana mais pesada do bloco. Aumente a carga onde a semana 3 pareceu fácil.',
        '4|Day 5':
          'Última sessão do bloco. Concluí-la volta para a semana 1 — repita o bloco a partir da carga com que você terminou.',
      },
    },
  },
};

const kettlebellJa: LibraryTranslation = {
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
    'kb-rest': { name: '休憩' },
    'kb-swing': {
      name: 'ケトルベルスイング',
      notes: '運動も休憩も30秒ずつなので、ブロック全体で8分です。重量を足す前に、まずラウンド数を増やします。',
    },
    'kb-goblet-squat': {
      name: 'ケトルベル・ゴブレットスクワット',
      notes: '1個のベルを胸の前で保持します。全セットで12回に届いたら重量を足します。',
    },
    'kb-clean-press': {
      name: 'クリーン＆プレス',
      notes: 'レップ数は片側あたりです。弱いほうを記録すると、次に超えるべき数字が正直なものになります。',
    },
    'kb-single-leg-deadlift': { name: '片脚デッドリフト', notes: 'レップ数は片脚あたりです。' },
    'kb-row': { name: 'ケトルベルロウ', notes: 'レップ数は片側あたりです。' },
    'kb-halo': { name: 'ハロー', notes: '左右に1周ずつで1レップです。' },
    'kb-carry': { name: 'ラックキャリー', notes: '重量を増やす前に、まず時間を伸ばします。' },
  },
  workouts: {
    'kb-strength-a': 'ケトルベル · 筋力 A',
    'kb-strength-b': 'ケトルベル · 筋力 B',
    'kb-swing-day': 'ケトルベル · スイング',
  },
  programs: {
    'kb-4-weeks': {
      name: 'ケトルベル基礎 · 4週間',
      weekNotes: {
        '1|Day 1': '基準になる週です。ベルは1個で足ります。その重さを各種目に書き込みながら進めてください。',
        '2|Day 1': '同じ3セッションです。重量に触れる前に、各セットで第1週より1レップ多く出します。',
        '3|Day 1': '筋力の日は4セットにし、スイングは2ラウンド増やします。',
        '4|Day 1': 'ブロックでいちばんきつい週です。第3週が楽に感じたところは重量を足します。',
        '4|Day 5':
          'ブロック最後のセッションです。やり切ると第1週に戻ります。終えたときの重量から、もう一度ブロックを回してください。',
      },
    },
  },
};

/**
 * Keyed by pack id, then by language — language, not region, exactly as the locale bundles are, so
 * `pt` serves pt-BR and pt-PT. A pack with no table for a language falls back to its English
 * structure, one string at a time, rather than all at once.
 */
export const contentPackTranslations: Record<string, Record<string, LibraryTranslation>> = {
  'steady-strength': { pt: steadyStrengthPt, ja: steadyStrengthJa },
  'barbell-gym': { pt: barbellGymPt, ja: barbellGymJa },
  'kettlebell-basics': { pt: kettlebellPt, ja: kettlebellJa },
};
