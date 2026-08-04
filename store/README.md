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
re-capture once and both update. `site/assets/img` holds more than the listing can show (Play caps a
phone listing at eight), so `SHOTS` in the script is the chosen subset and its order is the listing's
order.

## What the limits actually are

Each of these cost a rejected upload or a wrong guess once, so they are asserted in the script:

- **Screenshots: max dimension ≤ 2× min dimension**, and **at most eight** per device type. 2×1080
  is 2160, so that is the height ceiling; `build-assets.js` enforces both, the second with an explicit
  throw so a ninth entry in `SHOTS` fails loudly rather than at upload.

  The 2026-08-04 captures are cropped to 2160 at capture time — status bar and gesture pill both
  removed, window centred on each screen's own content — so the extract in the script is a no-op guard
  for them. Earlier captures were 1080x2242 and got trimmed from the bottom, the gesture-pill band
  being the only strip with nothing worth keeping. Letterboxing to fit would put visible bars down
  both sides, which is why neither approach does it.
- **App icon: full square, no transparency, no baked corner radius or shadow.** Play applies its own
  30% radius and shadow, so pre-rounding doubles up. The script asserts zero non-opaque pixels.
- **Feature graphic: no alpha channel.** Flattened explicitly.
- **Brand fonts must load.** The page renders fine in a system fallback, and the output looks
  plausible until it sits next to the app — so an unloaded font is a hard failure.

## Listing copy

Short description is capped at **80 characters**, full at **4000**, release notes at **500 per
language**. Only include a language tag the listing actually supports, or the release is rejected.

### Short — en-US (72)

```
Lifting and conditioning on one timer. Your training, in a file you own.
```

### Short — pt-BR (77)

```
Musculação e condicionamento no mesmo cronômetro. Seu treino, no seu arquivo.
```

### Full — en-US (3960)

```
Kettle runs your lifting and your conditioning in the same session, on one timer.

Reps, HIIT, EMOM, AMRAP, timed holds, cardio and rest all belong to one wall-clock engine, so a session that mixes them is just a session. There is no account to create, no server to trust and nothing to sync: your exercises, workouts and programs live in a plain text file on your phone, and your completed sessions are written to a local log only you can see.

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

IT BENDS TO THE SESSION YOU ARE ACTUALLY HAVING
Training deviates. Add a set when you have another one in you, drop one when you do not, or swap an exercise when the rack is taken — mid-workout, without editing your library. Walked in with no plan at all? Start an empty session and add exercises as you go.

IT REMEMBERS WHAT YOU LIFTED
The set row shows what you did last time — the same set number, the same exercise — and one tap makes it the new target, saved back to your library so next week starts there. Beat your best and the row says so as it happens; finish and the summary names the record, with an estimated 1RM for loaded sets.

CIRCUITS AND SUPERSETS
Group exercises into a circuit that runs round-robin for as many rounds as you like, with configurable rest between exercises and between rounds. A superset is just a circuit with no rest in between.

MULTI-WEEK PROGRAMS
Schedule workouts across weeks and progress them with per-week overrides — add a set in week three, cut a circuit down for a deload — without duplicating anything. Kettle queues up what is next.

HISTORY THAT IS YOURS, AND FIXABLE
Every set goes to a local log: what you did, when you did it. Streaks, weekly totals and per-exercise volume are calculated on your device from that log. Nothing is uploaded. Tapped the wrong number? Open any past session and fix the reps, load or RPE, or remove a set you did not do.

BRING YOUR OWN ASSISTANT
Because the format is documented and checked against a published schema, you can ask any AI assistant to write a program for you and paste the result in. Kettle validates it on your device and tells you exactly what it would change before anything is saved. The app itself has no AI features and never contacts a model.

WHAT KETTLE DOES NOT DO
No account. No cloud. No analytics. No crash reporting. No advertising. No third-party SDK that transmits anything. The app makes no network requests of its own, and it does not ask for the microphone, the camera, your location or your contacts. There is one optional reminder, off until you turn it on, and it is a notification on your own device.

FREE, WITH AN OPTIONAL TIP JAR
Every feature is unlocked for everyone. No paid tier, no subscription, no trial. An optional tip jar helps cover the developer account fee, and nothing is gated behind it — export included.

Available in English and Portuguese.

The format is documented at celsaad.github.io/kettle, and the source is on GitHub under the MIT license, so you can check any of this yourself.
```

### Full — pt-BR (3950)

```
O Kettle faz a musculação e o condicionamento na mesma sessão, no mesmo cronômetro.

Séries, HIIT, EMOM, AMRAP, isometrias, cardio e descanso rodam no mesmo motor, medido pelo relógio real — uma sessão que mistura tudo é só uma sessão. Sem conta para criar, sem servidor para confiar, sem nada para sincronizar: seus exercícios, treinos e programas ficam em um arquivo de texto no seu aparelho, e as sessões concluídas vão para um registro local que só você vê.

MONTE EM UM ARQUIVO QUE É SEU
Sua biblioteca é YAML comum. Edite pelo app ou abra em qualquer editor de texto. Importe um arquivo ou cole o texto direto: o Kettle junta ao que você já tem sem mexer no histórico. Exporte tudo quando quiser, em um formato que ainda vai abrir daqui a dez anos.

UM EXECUTOR QUE ACOMPANHA
Faça a sessão bloco a bloco, com cronômetros ao vivo e o próximo bloco sempre à vista:
• Séries — repetições, carga e PSE
• HIIT — intervalos de esforço e descanso por rounds
• EMOM — a cada minuto, no minuto
• AMRAP — o máximo de rounds dentro de um tempo
• Isometria — pranchas, barras, cadeirinha, carregamentos
• Cardio — por duração ou distância
• Descanso — cronometrado ao segundo

O tempo é medido pelo relógio real, então sobrevive a você trocar de app. Sinais sonoros, vibração e uma contagem regressiva fazem você quase não precisar olhar a tela. Cada série é salva na hora: uma falha custa no máximo a série em andamento.

ELE SE ADAPTA AO TREINO QUE VOCÊ ESTÁ FAZENDO
Treino de verdade sai do script. Acrescente uma série quando ainda tiver gás, tire uma quando não tiver, ou troque um exercício se o aparelho estiver ocupado — no meio do treino, sem mexer na biblioteca. Chegou sem plano? Comece uma sessão vazia e vá adicionando.

ELE LEMBRA O QUE VOCÊ LEVANTOU
A linha mostra o que você fez da última vez — mesma série, mesmo exercício — e um toque vira o novo alvo, salvo na biblioteca para a próxima semana começar de lá. Superou seu recorde e a linha avisa na hora; ao terminar, o resumo nomeia o recorde e estima o 1RM quando há carga.

CIRCUITOS E SUPERSÉRIES
Junte exercícios em um circuito que roda alternando os movimentos pelo número de rounds que quiser, com descanso configurável entre exercícios e entre rounds. Uma supersérie é um circuito sem descanso no meio.

PROGRAMAS DE VÁRIAS SEMANAS
Distribua treinos ao longo das semanas e progrida com ajustes por semana — mais uma série na semana três, um circuito menor para deload — sem duplicar nada. O Kettle já deixa o próximo na fila.

UM HISTÓRICO QUE É SEU — E CORRIGÍVEL
Cada série entra em um registro local: o que você fez e quando. Sequências, totais da semana e volume por exercício são calculados no seu aparelho a partir desse registro. Nada é enviado. Digitou errado? Abra qualquer sessão passada e corrija as repetições, a carga ou a PSE, ou remova uma série que você não fez.

TRAGA SEU PRÓPRIO ASSISTENTE
Como o formato é documentado e validado por um schema público, você pode pedir a qualquer assistente de IA que escreva um programa e colar o resultado. O Kettle valida no seu aparelho e mostra o que seria alterado antes de salvar. O app não tem recursos de IA e nunca conversa com nenhum modelo.

O QUE O KETTLE NÃO FAZ
Sem conta. Sem nuvem. Sem análise de uso. Sem relatório de falhas. Sem anúncios. Sem nenhum SDK de terceiros que transmita qualquer coisa. O app não faz requisições de rede e não pede microfone, câmera, localização nem contatos. Existe um único lembrete opcional, desligado até você ligar, e é uma notificação no seu aparelho.

GRATUITO, COM CAIXINHA OPCIONAL
Todos os recursos estão liberados. Não há versão paga, assinatura nem teste. Uma caixinha opcional ajuda a cobrir a taxa da conta de desenvolvedor, e nada fica trancado atrás dela — exportar incluído.

Disponível em inglês e português.

O formato está documentado em celsaad.github.io/kettle e o código-fonte está no GitHub sob a licença MIT, então você pode conferir tudo por conta própria.
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

**The copy lives in [`CHANGELOG.md`](../CHANGELOG.md), next to what it describes.** Only the rules
stay here, because they are Play's rather than ours.

Per release, per language, **500 characters each, and not cumulative** — the next version needs new
ones, and re-uploading the previous version's text is a rejection rather than a no-op.

Include a `<pt-BR>` block only if the listing really carries that language; an unsupported tag is
rejected on upload. The blocks in the changelog are wrapped in those tags in upload order, so a
release is a copy-paste from there.

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

- No `og:image` card for the site yet; if one is made, it is a website asset and outside Play's
  scope, but reusing it as a listing graphic would put it back in scope.
- The captures are of a **real** library and log — 347 exercises, real workout names, actual history.
  That is the populated state the listing wanted, and it is public once uploaded. Re-capturing against
  seeded data is the alternative if that ever becomes unwanted.
