# Play Console assets and listing copy

Everything the Google Play listing needs that isn't the app itself. The graphics are **generated**,
not committed — `build-assets.js` derives all of them from sources already in the repo, so there is
one copy of each thing rather than two that drift.

## Regenerating

`sharp` and `playwright` are deliberately not project dependencies. They are needed once per release
and would otherwise be installed on every CI run; the repo already treats Playwright this way for
browser checks.

```sh
mkdir ../kettle-store-tools && cd ../kettle-store-tools && npm i sharp playwright
npx playwright install chromium-headless-shell
cd -
NODE_PATH=../kettle-store-tools/node_modules node store/build-assets.js
```

**This one stays on npm even though the app itself uses pnpm.** `NODE_PATH` resolves against a flat
directory, and pnpm's default isolated layout puts transitive deps under `.pnpm/` where `NODE_PATH`
can't see them — `sharp` would resolve and its own dependencies would not. pnpm 11 reads `nodeLinker`
only from `pnpm-workspace.yaml`; both `.npmrc` and `--node-linker=hoisted` on the command line are
silently ignored, so there is no one-liner that fixes it. Adding a workspace file to a throwaway
directory to work around that is worse than just using npm here.

Output lands in `store/out/`, which is gitignored. The script fails rather than writes if an asset
would breach a Play limit — see "What the limits actually are" below.

## What each asset comes from

| Play field | Output | Derived from |
|---|---|---|
| App icon | `icon-512.png` | `assets/images/icon.png` |
| Feature graphic | `feature-graphic-1024x500.png` | `store/feature-graphic.html` + `site/fonts/*.woff2` |
| Phone screenshots | `screen-01..07-*.png` | `site/assets/img/*.jpg` |

The screenshots are the **same captures the landing page uses**. One set of images, two consumers —
re-capture once and both update.

## What the limits actually are

Each of these cost a rejected upload or a wrong guess once, so they are asserted in the script:

- **Screenshots: max dimension ≤ 2× min dimension.** The captures are 1080×2242 and 2×1080 is 2160,
  so they are 82px too tall as taken. Trimmed from the bottom, which is the gesture-pill band.
  Letterboxing to fit would put visible bars down both sides.
- **App icon: full square, no transparency, no baked corner radius or shadow.** Play applies its own
  30% radius and shadow, so pre-rounding doubles up. The script asserts zero non-opaque pixels.
- **Feature graphic: no alpha channel.** Flattened explicitly.
- **Brand fonts must load.** The page renders fine in a system fallback, and the output looks
  plausible until it sits next to the app — so an unloaded font is a hard failure.

## Listing copy

Short description is capped at **80 characters**, full at **4000**, release notes at **500 per
language**. Only include a language tag the listing actually supports, or the release is rejected.

### Short — en-US (77)

```
Workout tracker with no account and no server. Your data stays on your phone.
```

### Short — pt-BR (67)

```
Treinos sem conta e sem servidor. Seus dados ficam no seu aparelho.
```

### Full — en-US (3068)

```
Kettle is a workout tracker for people who want to own their training data.

There is no account to create, no server to trust and nothing to sync. Your exercises, workouts and programs live in a plain text file on your phone that you can read, edit, back up and take with you. Your completed sessions are written to a local log that only you can see.

PLAN IN A FILE YOU CONTROL
Your library is ordinary YAML. Edit it in the app, or open it in any text editor and write it by hand. Import a file or paste text straight in, and Kettle merges it into what you already have without touching your history. Export everything whenever you want, in a format that will still open in ten years.

A RUNNER THAT KEEPS UP
Work through a session block by block, with live timers and the next block always in view:
• Reps — sets with a rep count, load and RPE
• HIIT — work and rest intervals over rounds
• EMOM — every minute on the minute
• AMRAP — as many rounds as possible in a time cap
• Timed holds — planks, hangs, wall sits, carries
• Cardio — by duration or distance
• Rest — timed to the second

Timing is wall-clock based, so it survives you switching apps. Audio cues, haptics and a pre-session countdown mean you rarely need to look at the screen. Every finished set is saved as you go, so a crash costs you at most the set in progress.

CIRCUITS AND SUPERSETS
Group exercises into a circuit that runs round-robin for as many rounds as you like, with configurable rest between exercises and between rounds. A superset is just a circuit with no rest in between.

MULTI-WEEK PROGRAMS
Schedule workouts across weeks and progress them with per-week overrides — add a set in week three, cut a circuit down for a deload — without duplicating anything. Kettle queues up what is next.

HISTORY THAT IS YOURS
Every set appends to a local log: what you did, when you did it. Streaks, weekly totals and per-exercise volume are calculated on your device from that log. Nothing is uploaded, and nothing is overwritten.

BRING YOUR OWN ASSISTANT
Because the format is documented and checked against a published schema, you can ask any AI assistant to write a program for you and paste the result in. Kettle validates it on your device and tells you exactly what it would change before anything is saved. The app itself has no AI features and never contacts a model.

WHAT KETTLE DOES NOT DO
No account. No cloud. No analytics. No crash reporting. No advertising. No third-party SDK that transmits anything. The app makes no network requests of its own, and it does not ask for the microphone, the camera, your location or your contacts.

FREE, WITH AN OPTIONAL TIP JAR
Every feature is unlocked for everyone. There is no paid tier, no subscription and no trial. An optional tip jar helps cover the developer account fee, and nothing whatsoever is gated behind it — export included.

Available in English and Portuguese.

The library format is documented at celsaad.github.io/kettle, and the source is on GitHub under the MIT license, so you can check any of the above for yourself.
```

### Full — pt-BR (3145)

```
O Kettle é um app de treinos para quem quer ser dono dos próprios dados.

Não tem conta para criar, não tem servidor para confiar e não tem nada para sincronizar. Seus exercícios, treinos e programas ficam em um arquivo de texto no seu aparelho, que você pode ler, editar, salvar e levar com você. As sessões concluídas vão para um registro local que só você vê.

MONTE EM UM ARQUIVO QUE É SEU
Sua biblioteca é YAML comum. Edite pelo app ou abra em qualquer editor de texto e escreva à mão. Importe um arquivo ou cole o texto direto: o Kettle junta ao que você já tem sem mexer no seu histórico. Exporte tudo quando quiser, em um formato que ainda vai abrir daqui a dez anos.

UM EXECUTOR QUE ACOMPANHA
Faça a sessão bloco a bloco, com cronômetros ao vivo e o próximo bloco sempre à vista:
• Séries — repetições, carga e PSE
• HIIT — intervalos de esforço e descanso por rounds
• EMOM — a cada minuto, no minuto
• AMRAP — o máximo de rounds dentro de um tempo
• Isometria — pranchas, barras, cadeirinha, carregamentos
• Cardio — por duração ou distância
• Descanso — cronometrado ao segundo

O tempo é medido pelo relógio real, então sobrevive a você trocar de app. Sinais sonoros, vibração e uma contagem regressiva antes de começar fazem você quase não precisar olhar a tela. Cada série concluída é salva na hora: uma falha custa no máximo a série em andamento.

CIRCUITOS E SUPERSÉRIES
Junte exercícios em um circuito que roda alternando os movimentos pelo número de rounds que quiser, com descanso configurável entre exercícios e entre rounds. Uma supersérie é só um circuito sem descanso no meio.

PROGRAMAS DE VÁRIAS SEMANAS
Distribua treinos ao longo das semanas e faça a progressão com ajustes por semana — mais uma série na semana três, um circuito menor para deload — sem duplicar nada. O Kettle já deixa o próximo na fila.

UM HISTÓRICO QUE É SEU
Cada série entra em um registro local: o que você fez e quando. Sequências, totais da semana e volume por exercício são calculados no seu aparelho a partir desse registro. Nada é enviado e nada é sobrescrito.

TRAGA SEU PRÓPRIO ASSISTENTE
Como o formato é documentado e validado por um schema público, você pode pedir a qualquer assistente de IA que escreva um programa e colar o resultado. O Kettle valida no seu aparelho e mostra exatamente o que seria alterado antes de salvar. O app em si não tem recursos de IA e nunca conversa com nenhum modelo.

O QUE O KETTLE NÃO FAZ
Sem conta. Sem nuvem. Sem análise de uso. Sem relatório de falhas. Sem anúncios. Sem nenhum SDK de terceiros que transmita qualquer coisa. O app não faz requisições de rede próprias e não pede microfone, câmera, localização nem contatos.

GRATUITO, COM CAIXINHA OPCIONAL
Todos os recursos estão liberados para todo mundo. Não há versão paga, assinatura nem teste. Uma caixinha opcional ajuda a cobrir a taxa da conta de desenvolvedor, e absolutamente nada fica trancado atrás dela — exportar incluído.

Disponível em inglês e português.

O formato da biblioteca está documentado em celsaad.github.io/kettle e o código-fonte está no GitHub sob a licença MIT, então você pode conferir tudo isso por conta própria.
```

Deliberately absent from the listing copy, so it isn't re-added: **no competitor names** (fine on our
own site, a trademark risk in a listing), no emoji or shouty headlines (Play's spam policy), and no
health or medical claims, which would contradict the Health apps declaration below.

## Declarations, and what was answered

Recorded because the reasoning is not obvious from the answers, and each will be asked again.

- **Health apps declaration — Health and Fitness › Activity and Fitness.** Every app must complete
  this, not only health apps. The category covers "apps that monitor and record physical activities,
  exercise routines, and workouts", which is exactly this app. **No** to Health Connect (no
  dependency, no `android.permission.health.*`), and no to every Medical category, Medical Device
  and Health Subjects Research.
- **Data safety — no data collected, no data shared.** Consistent with the above rather than
  contradicting it: Google defines collection as data transmitted off the device, and the session
  log never leaves it. Play Billing keeps payment data out of app code.
- **Foreground service permissions — not applicable.** `FOREGROUND_SERVICE_MEDIA_PLAYBACK` was
  removed rather than declared; the declaration wanted a public video demonstrating media playback
  for a permission the app never used. See the `enableBackgroundPlayback` commit.
- **Tablet assets — deliberately empty.** Optional, and the app is portrait-locked with a
  phone-width content cap. Filling the slots with web-build captures would misrepresent the Android
  tablet experience. Real tablet support is layout work, not a config flip.
- **AI-generated content declaration.** The feature graphic is a CSS layout of existing brand
  artwork, rasterised deterministically — no generative model produced any pixel — but its
  composition was authored by an AI assistant. Declared accordingly. Screenshots are captures of the
  running app and are not AI-generated.

## Release notes

Per release, per language, 500 characters each. Not cumulative — the next version needs new ones.

Include a `pt-BR` block only if the listing really has that language — an unsupported tag is
rejected on upload. Both blocks below are within the limit as written (490 and 464 characters);
re-count if you edit them.

### 0.2.0 (versionCode 4)

```
<en-US>
What's new in 0.2.0

New here? Today opens with three short steps telling you where to start, and clears away once you finish your first session.

Importing a library now says what it changed — how many items were added or updated, and which ones — instead of just closing.

Today no longer goes blank if you delete every workout: it says so, and offers to build one.

Please test starting a session, importing a library and exporting your history. Report anything that hangs or loses data.
</en-US>
<pt-BR>
Novidades da 0.2.0

Primeira vez? A tela Hoje abre com três passos curtos dizendo por onde começar, e some depois do seu primeiro treino.

Importar uma biblioteca agora mostra o que mudou — quantos itens entraram ou foram atualizados, e quais — em vez de só fechar.

A tela Hoje não fica mais em branco se você apagar todos os treinos: ela avisa e oferece criar um.

Teste iniciar um treino, importar e exportar seu histórico. Relate travamentos ou perda de dados.
</pt-BR>
```

While the app is in closed testing the last line is deliberately spent on direction rather than on
positioning — a tester who is told what to exercise reports something, and "real feedback" is what
the track is assessed on.

### 0.1.0 (versionCode 3) — superseded, kept as the shape to copy

```
<en-US>
First public build of Kettle.

Plan workouts in a plain YAML file you own, then run them with live timers for all seven exercise types: reps, HIIT, EMOM, AMRAP, timed holds, cardio and rest. Multi-week programs with per-week progressions. Everything is stored on your device and never leaves it: no account, no server, no analytics, no ads.

English and Portuguese. Please report anything that breaks.
</en-US>
```

## Tip jar products

Created under **Monetize with Play › Products › In-app products** (the docs now call these "one-time
products"; the Console still says both). Requires a payments profile first — that lives on the
sibling **Monetisation setup** page and is the step with real lead time, since it needs bank details.

| Product ID | Price (USD) | Name | Description |
|---|---|---|---|
| `tip_small` | $1 | Small tip | A small thank-you for Kettle. Nothing is unlocked — every feature is already free, and your data stays on your device. |
| `tip_medium` | $3 | Medium tip | A thank-you for Kettle, and a hand with the developer account fee. Nothing is unlocked; every feature is already free. |
| `tip_large` | $5 | Large tip | A generous thank-you for Kettle. Nothing is unlocked — the whole app is free, with no ads and no account. |

Portuguese, for listings that have it:

| Product ID | Name | Description |
|---|---|---|
| `tip_small` | Gorjeta pequena | Um obrigado pelo Kettle. Nada é desbloqueado — todos os recursos já são gratuitos e seus dados ficam no seu aparelho. |
| `tip_medium` | Gorjeta média | Um obrigado pelo Kettle e uma ajuda com a taxa da conta de desenvolvedor. Nada é desbloqueado; tudo já é gratuito. |
| `tip_large` | Gorjeta grande | Um obrigado generoso pelo Kettle. Nada é desbloqueado — o app inteiro é gratuito, sem anúncios e sem conta. |

Why these, and what not to change casually:

- **The names are the app's own tier labels**, `support.tierSmall`/`tierMedium`/`tierLarge` in the
  locale bundles. Someone taps "Small tip" and Play's sheet has to say "Small tip"; a mismatch there
  reads as the wrong purchase and gets cancelled. Renaming a tier in the app means renaming it here.
- **Every description says nothing is unlocked**, which is both true and defensive: Play reviews IAP
  descriptions for misrepresentation, and a tip that sounds like it buys something is what gets
  flagged. It also matches `site/support.html` and the store listing.
- **Prices must stay ascending.** `TIP_TIERS` in `domain/tip.ts` fixes the display order small →
  medium → large and deliberately does *not* sort by price — Android reports price as optional, so
  sorting by it would scramble the list exactly when the store returns partial data. Price medium
  above large and the UI shows them out of order with no warning.
- **Product IDs are permanent.** They cannot be renamed, and cannot be reused after deletion.
  Everything else in these tables is editable in the Console at any time.
- **There is no "consumable" setting in the Console.** Consumability is entirely app-side:
  `finishTransaction({ purchase, isConsumable: true })` in `app/support.tsx`. Without it Play treats
  the SKU as owned and refuses every later purchase of that tier — so "buy the same tier twice" is
  the test that actually proves the tip jar works, not "buy one".
- Play auto-converts to other currencies and its rounding is occasionally odd. Worth checking the
  BRL figures by hand, Portuguese being one of the two shipped languages. The app renders
  `displayPrice` straight from the store, so whatever Play decides is what users see.

Testing needs **Licence testing** (account level — back out of the app, then `Setup › Licence
testing`), an account opted into a test track, and the build **installed from Play**. In-app products
do not resolve on a locally installed APK.

## Open

- The lead screenshot (`today`) shows a zeroed streak and an empty Recent list. Capture it again with
  a few sessions logged — it is the first image anyone sees.
- `screen-07-import` predates the removal of the leading glyphs on that screen.
- No `og:image` card for the site yet; if one is made, it is a website asset and outside Play's
  scope, but reusing it as a listing graphic would put it back in scope.
