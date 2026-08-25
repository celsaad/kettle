# Play Console assets and listing copy

Everything the Google Play listing needs that isn't the app itself. The graphics are **generated**,
not committed — `build-assets.js` derives all of them from sources already in the repo, so there is
one copy of each thing rather than two that drift.

The live listing is <https://play.google.com/store/apps/details?id=com.casco.kettle>.

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

  The 2026-08-19 captures come off the device at 1080x2204, which is 2.041:1 — over the ceiling — so
  the extract in the script does real work again and trims 44px from the bottom. The 2026-08-04 set
  was cropped to 2160 at capture time and made that extract a no-op; don't read the code as though
  that is still true. Earlier captures were 1080x2242 and got trimmed the same way, the gesture-pill
  band being the only strip with nothing worth keeping. Letterboxing to fit would put visible bars
  down both sides, which is why none of these approaches does it.
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

### Short — ja-JP (46)

```
筋トレもコンディショニングも、1つのタイマーで。あなたのトレーニングは、あなたのファイルに。
```

### Full — en-US (3956)

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
Every set goes to a local log: what you did, when you did it. Streaks, weekly totals and per-exercise volume are calculated on your device. Nothing is uploaded. Tapped the wrong number? Open any past session and fix the reps, load or RPE, or remove a set you did not do.

BRING YOUR OWN ASSISTANT
Because the format is documented and checked against a published schema, you can ask any AI assistant to write a program for you and paste the result in. Kettle validates it on your device and tells you exactly what it would change before anything is saved. The app itself has no AI features and never contacts a model.

WHAT KETTLE DOES NOT DO
No account. No cloud. No analytics. No crash reporting. No advertising. No third-party SDK that transmits anything. The app makes no network requests of its own, and it does not ask for the microphone, the camera, your location or your contacts. There is one optional reminder, off until you turn it on, and it is a notification on your own device.

FREE, WITH AN OPTIONAL TIP JAR
Every feature is unlocked for everyone. No paid tier, no subscription, no trial. An optional tip jar helps cover the developer account fee, and nothing is gated behind it — export included.

Available in English, Portuguese and Japanese.

The format is documented at celsaad.github.io/kettle, and the source is on GitHub under the MIT license, so you can check any of this yourself.
```

### Full — pt-BR (3959)

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

Disponível em inglês, português e japonês.

O formato está documentado em celsaad.github.io/kettle e o código-fonte está no GitHub sob a licença MIT, então você pode conferir tudo por conta própria.
```

### Full — ja-JP (1993)

```
Kettle は、筋トレもコンディショニングも同じセッションの中で、1つのタイマーで動かします。

レップ、HIIT、EMOM、AMRAP、ホールド、カーディオ、休憩は、すべて同じ実時間エンジンで動きます。だから、それらが混ざった日も「ただのセッション」です。作るアカウントも、預けるサーバーも、同期するものもありません。エクササイズ、ワークアウト、プログラムは端末の中のテキストファイルにあり、終えたセッションはあなたにしか見えないローカルの記録に残ります。

自分の手の中のファイルで組み立てる
ライブラリはごく普通の YAML です。アプリで編集しても、好きなテキストエディタで開いて手で書いてもかまいません。ファイルを読み込んでも、テキストを貼り付けても、Kettle は履歴に触れずに今あるものへ統合します。書き出しはいつでもでき、10年後でも開ける形式です。

ペースについてくる実行画面
ライブのタイマーと、常に見えている次のブロックとで、セッションをブロックごとに進めます。
• レップ — 回数、負荷、RPE を記録するセット
• HIIT — 運動と休憩をラウンドで繰り返す
• EMOM — 毎分、分の頭から
• AMRAP — 制限時間内にできるだけ多くのラウンドを
• ホールド — プランク、ぶら下がり、空気椅子、キャリー
• カーディオ — 時間または距離で
• 休憩 — 秒単位で計測

計測は実時間なので、他のアプリに切り替えても狂いません。音の合図、振動、開始前のカウントダウンがあるので、画面を見続ける必要はほとんどありません。終えたセットはその場で保存されるため、落ちても失うのは進行中のセットだけです。

その日の実際のトレーニングに合わせられる
トレーニングは計画どおりにはいきません。もう1セットできる日は足し、できない日は減らし、ラックが埋まっていればエクササイズを入れ替える。すべてトレーニング中に、ライブラリを書き換えずにできます。何も決めずに来た日は、空のセッションを始めて、その場で足していってください。

前回挙げた重さを覚えている
セットの行には前回の記録が出ます。同じセット番号、同じエクササイズです。ひとタップでそれが新しい目標になり、ライブラリに保存されるので、来週はそこから始まります。自己ベストを超えればその場で行が知らせ、終了時の要約が記録の名前を挙げ、負荷のあるセットには推定 1RM も出します。

サーキットとスーパーセット
エクササイズをまとめてサーキットにすると、好きなラウンド数だけ順ぐりに回ります。エクササイズ間とラウンド間の休憩は、それぞれ設定できます。スーパーセットは、間に休憩を入れないサーキットのことです。

数週間のプログラム
ワークアウトを週にわたって並べ、週ごとの上書きで進めていけます。第3週でセットを1つ足す、ディロードでサーキットを短くする。どれも中身を複製せずにできます。次に何をするかは Kettle が用意します。

あなたのもので、直せる履歴
すべてのセットがローカルの記録に入ります。何をしたか、いつしたか。連続日数、週の合計、エクササイズごとのボリュームは、その記録から端末の中で計算されます。どこにもアップロードされません。数字を打ち間違えたら、過去のセッションを開いてレップ、負荷、RPE を直せますし、やっていないセットは削除できます。

好きな AI アシスタントを連れてくる
形式は文書化され、公開されたスキーマで検証されているので、どの AI アシスタントにプログラムを書いてもらっても、結果を貼り付けるだけで済みます。Kettle は端末の中で検証し、保存する前に何がどう変わるかを正確に伝えます。アプリ自体に AI 機能はなく、モデルに接続することもありません。

Kettle がしないこと
アカウントなし。クラウドなし。利用状況の分析なし。クラッシュレポートなし。広告なし。何かを送信する第三者 SDK もなし。アプリは自分からネットワークに接続せず、マイク、カメラ、位置情報、連絡先も要求しません。リマインダーが1つだけありますが、自分でオンにするまで動かず、それも端末の中の通知です。

無料、チップは任意
すべての機能が誰にでも開放されています。有料版も、サブスクも、試用期間もありません。任意のチップはデベロッパー登録料の足しになりますが、その裏に隠れている機能はありません。書き出しも含めてです。

英語・ポルトガル語・日本語に対応しています。

形式は celsaad.github.io/kettle に文書化されており、ソースコードは MIT ライセンスで GitHub にあります。ここに書いたことは、すべて自分で確かめられます。
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

Copy is written here for **en-US, pt-BR and ja-JP**. That is not the same claim as the listing
carrying all three: a language exists for upload only once it is added in the Console, and a tag the
Console does not know is rejected rather than ignored. ja-JP was written when the app shipped
Japanese, so check the Console before tagging a block with it. The blocks in the changelog are
wrapped in those tags in upload order, so a release is a copy-paste from there.

## Tip jar products

Created under **Monetize with Play › Products › In-app products** (the docs now call these "one-time
products"; the Console still says both). Requires a payments profile first — that lives on the
sibling **Monetisation setup** page and is the step with real lead time, since it needs bank details.

| Product ID | Price (USD) | Name | Description |
|---|---|---|---|
| `tip_small` | $1 | Small tip | A small thank-you for Kettle. Nothing is unlocked — every feature is already free, and your data stays on your device. |
| `tip_medium` | $3 | Medium tip | A thank-you for Kettle, and a hand with the developer account fee. Nothing is unlocked; every feature is already free. |
| `tip_large` | $5 | Large tip | A generous thank-you for Kettle. Nothing is unlocked — the whole app is free, with no ads and no account. |

Portuguese and Japanese, each only for a listing that carries the language:

Portuguese:

| Product ID | Name | Description |
|---|---|---|
| `tip_small` | Gorjeta pequena | Um obrigado pelo Kettle. Nada é desbloqueado — todos os recursos já são gratuitos e seus dados ficam no seu aparelho. |
| `tip_medium` | Gorjeta média | Um obrigado pelo Kettle e uma ajuda com a taxa da conta de desenvolvedor. Nada é desbloqueado; tudo já é gratuito. |
| `tip_large` | Gorjeta grande | Um obrigado generoso pelo Kettle. Nada é desbloqueado — o app inteiro é gratuito, sem anúncios e sem conta. |

Japanese:

| Product ID | Name | Description |
|---|---|---|
| `tip_small` | 少額のチップ | Kettle への少しばかりのお礼です。解放される機能はありません。すべての機能はもともと無料で、データは端末の中にとどまります。 |
| `tip_medium` | 中くらいのチップ | Kettle へのお礼と、デベロッパー登録料の足しです。解放される機能はありません。すべての機能はもともと無料です。 |
| `tip_large` | 多めのチップ | Kettle への大きなお礼です。解放される機能はありません。アプリ全体が無料で、広告もアカウントもありません。 |

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
  BRL and JPY figures by hand, Portuguese and Japanese being two of the three shipped languages. The
  app renders
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
- The 2026-08-19 log was built by running sessions back to back, so every session reads `0 min` in
  History and Stats totals barely a minute. Nothing is wrong; it just doesn't look like a year of
  training. Worth a slower log before the next upload — `history` and `stats` are 2 of the 8 slots.
- `programs.jpg` was captured **before** the week-count fix and still shows the pre-fix
  "8 weeks with notes or overrides" for a four-week program. Re-shoot it before the next upload.
- The completion capture names two records but no estimated 1RM, both PRs being unloaded. The copy
  says "for loaded sets", so it is accurate — but a `heaviest set` PR would demonstrate the whole
  claim, and the 2026-08-04 capture did.
