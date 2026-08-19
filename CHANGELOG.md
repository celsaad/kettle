# Changelog

> **What shipped, per release, plus the Play release notes that went out with it.** Why things are
> the way they are is in [`docs/decisions.md`](docs/decisions.md); what's still open is in
> [`docs/open-work.md`](docs/open-work.md). This file is the record of *what changed and when* — it
> is not a backlog and nothing here is planned work.

Versions are the `version` / `android.versionCode` pair in `app.json`, which is the single source
both the build and this file follow.

**Play release notes are per release, per language, 500 characters each, and are not cumulative** —
every version needs new ones. Include a `pt-BR` block only if the listing really carries that
language; an unsupported tag is rejected on upload. The limits and the upload format live in
[`store/README.md`](store/README.md); the copy itself lives here, next to what it describes.

---

## Unreleased

### What changed

**Kettle speaks Japanese.** Set your phone to Japanese and the whole app follows — every screen, the
runner's spoken announcements, the notifications, and the starter library Kettle sets up on first
launch, exercise names and coaching notes included. Dates, numbers and the first day of your week were
already following your device and still do.

Nothing you have written is touched. Your own exercise, workout and program names, your notes and your
day labels are your data, and they read exactly as you typed them whatever language the app is in.

**The starter exercises come with a drawing.** Open any exercise from the library Kettle sets up on
first launch — the push-ups, the dumbbell lifts, the burpees, the carries — and there is now a line
drawing of the movement above the notes. It's drawn in whatever theme you're using, it stays with the
exercise if you rename it, and screen readers get a description of the movement in your own language.

Exercises you write yourself don't get one, and neither does Rest. Nothing is downloaded, nothing is
stored, and none of it touches the file you export.

**You can turn the session sounds off.** Settings has a new *Session sounds* switch that silences the
countdown tick, the ding when the exercise changes and the chime at a halfway mark. It's on unless you
say otherwise. Kettle plays those cues even when your phone is on silent — a timing cue you can't hear
isn't a timing cue — so this is the only way to quiet them without turning down whatever you're
training to. Vibration is separate and stays on your phone's own settings.

### Fixed

- **EMOM minutes start at the rep count you prescribed.** They started at zero, so hitting the target
  meant tapping up to it every minute — and a minute you left alone recorded nothing at all.
- **The chime for reaching a hold's target or an interval's halfway mark now sounds again if you go
  back and redo that step**, instead of staying silent the second time through.
- **Buttons and text fields grow to fit their label.** "Adicionar bloco" was clipped in the workout
  editor in Portuguese; the same fixed heights would have cut off any long translation, and any label
  at a large accessibility text size.
- **Kettle opens without waiting for your whole training history.** It used to read and check every
  session file you had ever logged before drawing anything, so the wait grew a little with every
  workout you finished. It now starts on your library and fills the history in behind you — the
  difference is invisible in your first month and adds up over years.
- **A workout with an impossible number of sets can no longer make the session screen unopenable.**
  An exercise asking for tens of thousands of sets — a typo, or a file from someone else — used to
  take the runner down every time you tried to start it, with no way back except editing the file by
  hand. Sets and rounds now cap at 500, and an EMOM block at 500 intervals — so a 60-minute block
  needs intervals of at least seven seconds. Both are refused when a file is imported and in the
  editor, so a limit can't be crossed in one place and discovered in the other.
- **If your library ever fails to load, Kettle keeps a copy of it.** It has always started you over
  with the built-in library rather than leaving you on a blank screen; now the file it couldn't read
  is saved next to it as `exercises.unreadable.yaml`, so you can fix the line and import it back
  instead of losing what you wrote.
- **A program week can no longer override an exercise into something the format would reject.** Week
  overrides skipped every check the rest of the file goes through, so a negative rest or a set count
  in the thousands could get in that way. An override that doesn't hold up is now ignored and the
  exercise runs as your library defines it, and the override editor says so rather than saving it.
- **Two workouts started in the same instant get two files.** Sessions are named after the moment they
  start, and two sharing a name meant one quietly overwriting the other.

---

## 0.7.0 — versionCode 10, 18 August 2026

### What changed

**Four tabs instead of five, and the app opens where you start a workout.**

- **Today and Build are one tab, called Workouts.** It opens with the same "next up" card as before —
  what your program has queued, or a rest day — and your whole workout list is right underneath it,
  searchable as it always was. Starting from the card still carries the program week, so that week's
  overrides apply; starting from the list still runs the workout as written.
- **The numbers have a screen of their own now, called Your numbers**, reached from `Stats` in
  History's header. Your streak, this week and all time live there, with a chart of sessions per week
  over the last eight — instead of six tiles filling the top of History and pushing the log itself
  below the fold. History is back to being a log, with a one-line summary above it.
- **The "Recent" list came off the home screen.** It was History's first five rows without the detail,
  the editing or the export, and History is one tap away.
- The settings gear is where it always was, top-right of the first tab.
- **Every list is rows now, not a stack of cards.** Workouts, exercises, programs, your history and
  the Settings rows are all hairline-separated lines with no box around them — so more fits on a
  screen, and names line up with everything else on the page instead of being indented inside a frame.
- **A program's own screen is rows too**, so a four-week plan reads as a list of days rather than as
  twelve stacked panels each with its own full-width orange button. Every day still starts from the
  same place — it's the round start button the workout list uses.
- **The "next up" panel lost its box too.** It's still the first thing on the Workouts tab and still
  the only orange button, but it now starts at the same edge as everything below it.
- **"Start an empty session" moved under the Start button**, where it reads as the alternative to it.
  It was at the top of the screen next to the title, which ran out of room at larger text sizes.
- **Sorting is gone from Workouts, Library and Programs; your lists read in the order your file is
  written.** That was already the default, so unless you'd changed it nothing moves. It buys every one
  of those screens a row back, and the file is the one place the order is genuinely yours.
- **The count moved into the search box** — "Search 26 workouts" — instead of sitting on a line of its
  own under the title.
- **Library's type shortcuts now cover every type**, with EMOM, AMRAP and Cardio joining HIIT, Reps
  and Hold. The row scrolls sideways, so it stays one row however long your text is set.
- **Orange now means one thing on the Workouts screen: start the workout that's queued.** The start
  button on each row of the list, and "Start an empty session" at the top, were competing with it for
  attention; they're still exactly where they were and still do exactly the same thing, just quietly.
  The card itself is a little shorter, so more of your list fits above the fold.
- **Your numbers can tell you whether you're getting stronger, not just how much you've done.** Under
  the chart there's now a line per exercise you've trained more than once in the last eight weeks:
  what you're lifting (or holding) now, the shape of how you got there, and what it changed by. That's
  a fact you previously had to open nine sessions and do arithmetic to work out. Strength work and
  holds only — HIIT and EMOM numbers are set by the workout rather than by you, so a bigger one there
  means you edited the workout, and cardio needs route-by-route comparison Kettle doesn't do yet.
  Those are left out rather than half-answered.

### Fixed

- **The session runner's progress bars now work at any length.** A circuit with a lot of exercises —
  a mobility routine with 27 of them, say — used to draw more dashes than there was room for, so every
  one of them collapsed to a sliver and you couldn't tell which was the current one. Both bars are now
  continuous, so they read the same at three exercises or fifty.
- **A single-block workout no longer looks finished before you start.** With only one block, the
  progress bar was permanently full; it now stays out of the way, since there's no progress to show.
- **One way to advance, instead of two.** The timed and interval screens had both a forward arrow and
  a labelled button doing exactly the same thing, inches apart. The arrow is gone; the button stays.
- The get-ready countdown leads with the **workout's name** rather than a huge number — the name is
  what you're actually checking in those three seconds.
- Long exercise notes are capped at two lines in the runner, so a paragraph can't push the timer down
  the screen. The full note is still on the exercise.
- **A program with named weekdays now runs in the order you wrote it.** Days sharing a week number
  were being put in alphabetical order, so a program labelled `Monday` through `Sunday` was run
  starting from Friday — while the program's own screen listed it correctly, Monday first. Day labels
  are now display text only: name them anything, and Kettle follows the order they appear in your
  file. Numbered days past `Day 9` were affected the same way (`Day 10` came before `Day 2`).

### Play release notes

Counted at 463 (en-US) and 487 (pt-BR), against a limit of 500. Re-count programmatically if you edit
them rather than trusting these numbers — over-limit copy is a rejected upload, not a truncated one,
and pt-BR is the tight one here with 13 characters spare.

```
<en-US>
What's new

Today and Build are now one tab, Workouts. It opens with the same next-up card, with your whole workout list underneath.

The numbers moved to their own screen, Your numbers, reached from Stats in History — now with a line per exercise showing whether you're getting stronger.

Every list is rows instead of cards, so more fits on screen.

Fixed: a program with named weekdays runs in the order you wrote it.

Report anything that hangs or loses data.
</en-US>
<pt-BR>
Novidades

Hoje e Montar agora são uma aba só, Treinos. Ela abre com o mesmo cartão do próximo treino, com toda a sua lista logo abaixo.

Os números ganharam uma tela própria, Seus números, a partir de Números no Histórico — agora com uma linha por exercício mostrando se você está ficando mais forte.

Todas as listas são linhas em vez de cartões, então cabe mais na tela.

Corrigido: um programa com dias nomeados roda na ordem que você escreveu.

Relate travamentos ou perda de dados.
</pt-BR>
```

---

## 0.6.0 — versionCode 9, 7 August 2026

### What changed

**Programs can have rest days.**

- A week in a program can now be a scheduled day off: it runs nothing, logs nothing, and never
  appears in your history — but it holds its place in the plan. On the day, Today says "Rest day"
  instead of queuing your next session.
- The rest day steps aside by itself a day later, so a week written out in full runs the way it
  reads. If you want to train anyway, the card has a button that jumps straight to the next real
  session, and "Start an empty session" is where it always was.
- Writing one by hand: `rest_day: true` on a week, with no `workout`. It can carry a `day` label and
  a note ("walk, stretch, nothing heavy") like any other week. The published format reference and
  the in-app guide both cover it.
- In the app, every week in the program editor has a "Rest day" switch. Turning it on drops that
  week's workout and any overrides, since a rest day has neither.
- **The starter programs and all four downloadable examples now write their weeks out in full**,
  rest days included — Foundations and Dumbbell Strength train on days 1, 3 and 5 and rest on the
  other four.
- **Only a fresh install gets the rewritten starter programs**, though. Kettle writes the starter
  library once, the first time it runs, and never touches it again — so if you already have Kettle,
  your copy of Foundations and Dumbbell Strength keeps the weeks it has and behaves exactly as
  before. Add rest days to them in the program editor, or re-import an example file to pick up its
  new ones. Your logged history is untouched either way.
- One thing this deliberately doesn't change: the day-streak counter still counts consecutive days
  you trained, so a rest day still resets it.

**Fixed.**

- Coming back to Kettle after a timer ran out while your phone was away could skip the step that was
  waiting for you and log it as done — a set you never performed, recorded at 0 reps. On the last
  step of a workout it counted that step twice instead, so a three-round HIIT could finish saying
  four. Both are gone; returning to a finished timer now lands you on the next step, once.
- A cardio step with a set duration logged however long your phone had been away rather than how long
  you actually went for — a 60-second row could land in your history as ten minutes. It now logs the
  duration it was set for, or the honest elapsed time if you ended it early. Cardio with no set
  duration is unchanged: it counts up until you stop it, and that whole time is the measurement.
  Rest is unchanged too, and deliberately — the time you spent resting really was that long.

### Play release notes

Counted at 477 (en-US) and 486 (pt-BR), against a limit of 500. Re-count programmatically if you edit
them rather than trusting these numbers — over-limit copy is a rejected upload, not a truncated one,
and pt-BR is the tight one here with 14 characters spare.

```
<en-US>
What's new

A week in a program can now be a rest day: it runs nothing and logs nothing, but it keeps its place in the plan. Today says "Rest day" instead of queuing your next session, and steps aside on its own the day after. Every week in the program editor has a switch for it.

Fixed: coming back to a timer that ran out while your phone was away no longer logs a set you never did, and cardio logs how long you actually went for.

Report anything that hangs or loses data.
</en-US>
<pt-BR>
Novidades

Uma semana de um programa agora pode ser um dia de descanso: não executa nada e não registra nada, mas mantém seu lugar no plano. A tela Hoje mostra "Dia de descanso" em vez da próxima sessão e sai do caminho sozinha no dia seguinte. Cada semana no editor de programas tem um botão para isso.

Corrigido: voltar a um timer que acabou com o celular guardado não registra mais uma série que você não fez, e o cardio registra o tempo real.

Relate travamentos ou perda de dados.
</pt-BR>
```

---

## 0.5.0 — versionCode 8, 5 August 2026

### What changed

**Backups into a folder you choose.**

- Pick a folder once — anywhere on your device, including whatever folder your sync app already
  watches — and Kettle writes `kettle-library.yaml` and `kettle-history.yaml` into it at the end of
  every session. There's a "Back up now" button in Settings for when you don't want to wait.
- It overwrites those two files and touches nothing else in the folder. Nothing is uploaded and no
  account is involved: this is Kettle writing to your own storage, so the app still collects and
  shares no data.
- If a backup can't be written — the folder was moved, or the permission was withdrawn — the session
  itself is unaffected and the completion screen says so afterwards. It will never interrupt a
  workout.
- Being straight about what this restores: the library file can be imported back into a fresh
  install. The session log can't, because nothing in Kettle reads one back in yet — it's exported to
  keep and to read. Settings says so rather than implying otherwise.
- Android only for now. iOS hands out folder access that expires when the app closes, so a folder
  chosen there would stop being written to without saying anything.

**A way to reach the developer.**

- Two rows in Settings: an email address, and the GitHub issue tracker. The email opens with a
  subject already carrying the app version, since that's the first thing any report needs and the
  last thing anyone remembers. Subject only — no body, no device details, no logs.
- If there's no mail app to open, the address goes on the clipboard instead and Kettle says so.
- Kettle sends nothing itself: it hands off to the mail app or browser you already have, and you see
  the whole message before it goes. The app still collects and shares no data.

**Fixed.**

- A long workout's chips no longer bury "Start session" on Today. The card shows eight and
  summarises the rest as a "+N more" chip; the full list is one tap away in the runner.

### Play release notes

Counted at 463 (en-US) and 472 (pt-BR), against a limit of 500. Re-count programmatically if you edit
them rather than trusting these numbers — over-limit copy is a rejected upload, not a truncated one.

```
<en-US>
What's new

Pick a folder and Kettle backs up your library and your log into it after every session — including a folder your sync app already watches. Nothing is uploaded and no account is involved. There's a "Back up now" button for when you don't want to wait.

Something wrong? Settings now has a way to reach me directly, by email or on GitHub.

A long workout no longer pushes "Start session" off the Today screen.

Report anything that hangs or loses data.
</en-US>
<pt-BR>
Novidades

Escolha uma pasta e o Kettle salva sua biblioteca e seu histórico nela ao fim de cada sessão — inclusive uma pasta que seu app de sincronização já acompanha. Nada é enviado e não precisa de conta. Há um botão "Fazer backup agora" para quando não quiser esperar.

Algo errado? Agora dá para falar comigo direto nos Ajustes, por e-mail ou no GitHub.

Um treino longo não empurra mais "Iniciar sessão" para fora da tela Hoje.

Relate travamentos ou perda de dados.
</pt-BR>
```

---

## 0.4.0 — versionCode 7, 4 August 2026

### What changed

**The runner remembers what you lifted.**

- The set row shows what you did on that same set number last time, and one tap adopts it as the new
  target — written back to the library, so next week starts there.
- A personal record is marked on the row as it happens, not only at the end.
- The completion summary names the record, with an estimated 1RM for loaded sets (Epley).
- An optional rest-day reminder, off until turned on, local notification only.

**The runner bends mid-session.**

- Add a set when you have another one in you, drop one when you don't.
- Swap an exercise when the rack is taken, for the rest of that exercise only.
- Start an empty session with nothing planned and add exercises as you go.
- The exercise picker grew a search field, since adding offers the whole library rather than the
  handful a swap filters to.

**A hold ends itself.**

- A `timed_hold` now ends on its own at the top of its range, with a 3-2-1 of ticks into the end, so a
  dead hang or an L-sit no longer logs the seconds you spent reaching for the phone.
- `hold_sec_min` became optional: a hold can have no target at all.

**A circuit says where you are in it.**

- The runner's header carries a second line inside a circuit: which round you're on, and which
  exercise of the round-robin. It stays put through the rests between exercises and between rounds,
  so the answer doesn't disappear at the moment you have time to look for it.

**The log can be corrected.**

- Edit a logged set from History: reps, load and RPE; hold seconds; HIIT and AMRAP rounds; cardio
  duration and distance. An `emom` minute list is shown read-only.
- Remove a set, or an exercise when its last set goes.
- A session still being written to by the runner refuses to be edited — see the decision log.

**Listing and site.**

- The pitch leads with the mixed-session runner rather than with the file format.
- Screenshots re-captured against a populated library, the runner leading.
- The log is no longer described as append-only anywhere, because it no longer is.
- Supersets are documented on both the site and the YAML reference, including the two things authors
  get wrong: `rounds` carries the set count, and zeroing an exercise's own `rest_sec` doesn't pair
  anything.

**Fixed.**

- Deleting a program returns to Programs, instead of the empty "Program not found" screen it used to
  leave behind.
- `rest_sec: 0` no longer produces a flash of rest screen, a chime and a notification between every
  back-to-back set — which is what made supersets feel broken.
- Circuit visits are numbered by round.
- A long workout name no longer pushes "Finish" off the session header.
- Reordering blocks: a block lands where you dropped it rather than a position further on, the drag
  handle wins over the scroll it sits in, and a drag Android interrupts still commits.
- A long import preview no longer buries the Merge button below hundreds of rows.
- Settings' data section leads with Import rather than with the exports.

### Play release notes

Counted at 468 (en-US) and 483 (pt-BR), against a limit of 500. Re-count programmatically if you edit
them rather than trusting these numbers — the first draft of the en-US block came in at 507, which is
a rejected upload rather than a truncated one, and the count written beside it was wrong too.

```
<en-US>
What's new

The set row now shows what you lifted last time on that same set — one tap makes it your target. Beat your best and Kettle says so as it happens, then names the record and estimates your 1RM at the end.

The session bends: add a set, drop one, swap an exercise when the rack is taken, or start with nothing planned.

Mis-logged a set? Fix it. Tap Edit on any past session instead of throwing the whole thing away.

Report anything that hangs or loses data.
</en-US>
<pt-BR>
Novidades

A linha da série mostra o que você levantou da última vez na mesma série — um toque vira seu alvo. Superou seu recorde e o Kettle avisa na hora; ao terminar, nomeia o recorde e estima seu 1RM.

A sessão se adapta: acrescente uma série, tire uma, troque um exercício se o aparelho estiver ocupado, ou comece sem plano nenhum.

Registrou errado? Agora dá para corrigir. Toque em Editar em qualquer sessão passada em vez de apagar tudo.

Relate travamentos ou perda de dados.
</pt-BR>
```

---

## 0.3.0 — versionCode 6, 2 August 2026

### What changed

- Search on every list, with an honest empty state when nothing matched.
- The three library lists can be ordered.
- Export the whole log, not one session at a time.
- Stopped re-rendering every card on every keystroke — the four list screens became `FlatList`s with
  memoised rows.
- The step-exit arrow points forward rather than up.

### Play release notes

**Reconstructed from `git log`, not the published text.** The notes that actually went to Play for
this version were written in the Play Console and never mirrored back here, so treat this as a record
of what changed rather than as a copy of what testers read. Not re-counted, since it was never
uploaded in this form.

```
<en-US>
Search in Workouts, Library, Programs and History — and a clear answer when nothing matches, instead of a blank screen.

Your workout, program and exercise lists can be put in the order you want.

Export your whole history in one go rather than one session at a time.

Long lists are faster to type into.
</en-US>
```

---

## 0.2.1 — versionCode 5

### What changed

- The step exit became the primary action on holds and count-up intervals.
- Runner button labels centre instead of left-aligning when they wrap.

### Play release notes

**Reconstructed from `git log`, not the published text** — same caveat as 0.3.0 above.

```
<en-US>
The button that ends a hold or a count-up interval is now the obvious one to press.

Fixed button labels sitting off-centre when they wrapped onto two lines.
</en-US>
```

---

## 0.2.0 — versionCode 4

### Play release notes

The published text, as uploaded. 490 (en-US) and 464 (pt-BR).

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

---

## 0.1.0 — versionCode 3

First public build. Kept as the shape to copy for a first release.

```
<en-US>
First public build of Kettle.

Plan workouts in a plain YAML file you own, then run them with live timers for all seven exercise types: reps, HIIT, EMOM, AMRAP, timed holds, cardio and rest. Multi-week programs with per-week progressions. Everything is stored on your device and never leaves it: no account, no server, no analytics, no ads.

English and Portuguese. Please report anything that breaks.
</en-US>
```
