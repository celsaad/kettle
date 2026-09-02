import * as Clipboard from 'expo-clipboard';
import { File } from 'expo-file-system';
import { router } from 'expo-router';
import type { TFunction } from 'i18next';
import { useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ListHeaderRule, ListRow, ListRowSeparator } from '@/components/list-row';
import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { buildAssistantBrief } from '@/domain/assistant-brief';
import type { FieldChange } from '@/domain/library-diff';
import { diffExercise, diffProgram, diffWorkout } from '@/domain/library-diff';
import type { MergeError, MergeSummary } from '@/domain/merge';
import { mergeLibraries } from '@/domain/merge';
import type { Library } from '@/domain/types';
import type { ParseError } from '@/domain/yaml-mapping';
import { parseLibraryYaml } from '@/domain/yaml-mapping';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useLibraryStore } from '@/state/library-store';
import { useSessionHistoryStore } from '@/state/session-history-store';
import { useUnitSystem } from '@/state/preferences-store';
import type { ContentPack } from '@/storage/content-packs';
import { contentPackCounts, contentPackLibrary, contentPacks } from '@/storage/content-packs';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

/** What the preview says it's about to merge. A filename for a pick, "Pasted YAML" for a paste. */
type Source = { title: string; detail: string };
type ReadyMerge = { picked: Source; library: Library; summary: MergeSummary };

/**
 * What actually landed, snapshotted at the moment of the write.
 *
 * It has to be a snapshot rather than a re-read of the preview: `changesFor` diffs the store's library
 * against the merged one, and once the write lands those are the same object — so re-rendering the
 * preview after applying would report every item as unchanged. This holds the ids and their kind,
 * which stay true; the per-field diffs are deliberately dropped, being a statement about a "before"
 * that no longer exists.
 */
type Applied = { newCount: number; updatedCount: number; items: { id: string; kind: 'new' | 'updated'; detail: string }[] };

/**
 * A refusal on screen, plus — when there is one — the same refusal worded for whoever wrote the YAML.
 *
 * `report` is deliberately null for the failures that aren't about the *content*: a picker that
 * couldn't read the file, a disk that wouldn't take the write, a library that hasn't hydrated. Handing
 * "Couldn't save your library: no space left on device" to an assistant asks it to fix something it
 * has no access to, and offering the button at all would suggest it could.
 */
type ImportError = { message: string; report: string | null };

/**
 * The two things worth putting on the clipboard, and the two directions of the same loop: `brief` goes
 * out to an assistant before anything is written, `report` goes back to it after the importer refuses
 * what came of that.
 */
type CopyTarget = 'brief' | 'report';

/**
 * How many changed ids the preview shows before it offers the rest behind a tap.
 *
 * The list is one row per changed id *plus* a line per changed field, so a re-imported library of any
 * size renders a wall — and a wall is not a review. The three count tiles directly above already
 * answer "how much is about to change"; these rows answer "is my own stuff in here", which is a
 * question you ask about a few ids, not forty. Eight is what fits above the fold beside the tiles on
 * a small phone, which is the only thing the number is chosen for.
 */
const COLLAPSED_CHANGES = 8;

/**
 * Whether a share sheet exists to send the brief to.
 *
 * Native always has one. react-native-web's `Share` is not a degraded implementation but a hard
 * refusal — it rejects with "Share is not supported in this browser" unless the browser implements
 * `navigator.share`, which desktop Firefox and most desktop Chrome builds do not. So the row is
 * hidden there rather than offered and refused, and the clipboard button keeps its full label (see
 * `import.copyBrief` vs `import.copyBriefInstead`) because nothing above it names the brief.
 *
 * The `typeof` guard is load-bearing rather than defensive: `navigator` is absent entirely on native.
 *
 * **Read once at module scope, which is not free on web.** `app.json` sets `web.output: "static"`, so
 * a prerender runs this in Node — where `navigator` is undefined — and bakes `false` into the HTML; a
 * share-capable browser then hydrates `true`, and the share row and the copy button's label differ
 * between the two trees for one frame. Accepted rather than fixed with `useState` + `useEffect`:
 * it needs a direct load of a prerendered `/import`, and web is the platform with no persistence at
 * all (see the storage guards), so a label settling after hydration is the least of what is degraded
 * there. Anything that makes web load-bearing should revisit this.
 */
const canShareText =
  Platform.OS !== 'web' ||
  (typeof navigator !== 'undefined' && typeof (navigator as { share?: unknown }).share === 'function');

/**
 * The indented "what moved" block under an updated id.
 *
 * Only for updates, and only what moved. An updated id whose definition is byte-identical says so
 * instead of showing an empty indent — `mergeById` classifies by id, not by value, so re-importing
 * your own export lands here for every item and would otherwise look like a wall of unexplained
 * overwrites.
 */
function ChangedFields({ changes }: { changes: FieldChange[] }) {
  const { t } = useTranslation();

  return (
    <View style={styles.diffList}>
      {changes.map((field) => (
        <ThemedText key={field.label} type="small" themeColor="textSecondary">
          {field.label}: {field.from} → {field.to}
        </ThemedText>
      ))}
      {changes.length === 0 && (
        <ThemedText type="small" themeColor="textSecondary">
          {t('import.diff.sameValues')}
        </ThemedText>
      )}
    </View>
  );
}

/**
 * The "show the rest" control, shared by the preview list and the applied one so the two can't drift
 * into disagreeing about how a long list behaves.
 */
function ChangeListToggle({ total, expanded, onToggle }: { total: number; expanded: boolean; onToggle: () => void }) {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="button"
      // No `accessibilityLabel`: the text below names it, and a duplicate would drift from the
      // count it quotes. `expanded` is what tells a screen reader this reveals rather than navigates.
      accessibilityState={{ expanded }}
      style={[styles.showAllButton, { borderColor: theme.border }]}>
      <ThemedText type="smallMedium" themeColor="textSecondary">
        {expanded ? t('import.showFewer') : t('import.showAll', { count: total })}
      </ThemedText>
    </Pressable>
  );
}

/**
 * Where an import failure becomes a sentence. The parser and the merge return descriptors — they're
 * logic-layer code and hold no prose — so every reason a file is refused is worded here, in both
 * locales, rather than in whichever module happened to detect it.
 */
function errorMessage(t: TFunction, error: ParseError | MergeError): string {
  switch (error.kind) {
    case 'invalidYaml':
      return t('import.error.invalidYaml', { detail: error.detail });
    case 'schemaMismatch':
      return t('import.error.schemaMismatch', { detail: error.detail });
    case 'unknownExercise':
      return t('import.error.unknownExercise', { workout: error.workoutId, exercise: error.exerciseId });
    case 'unknownWorkout':
      return t('import.error.unknownWorkout', {
        program: error.programId,
        // The day is the user's own label ("Monday", "Push A"), so it renders verbatim inside the frame.
        week: error.day ? t('import.error.weekWithDay', { week: error.week, day: error.day }) : error.week,
        workout: error.workoutId,
      });
  }
}

export default function ImportScreen() {
  const theme = useTheme();
  const { t, i18n } = useTranslation();
  const currentLibrary = useLibraryStore((state) => state.library);
  // Nothing logged is what "new here" means everywhere else in the app (see the Workouts screen), and
  // it costs no stored flag. It reads the count rather than subscribing to the sessions themselves,
  // so a session landing while this screen is open cannot reorder it under the user.
  const packsFirst = useSessionHistoryStore((state) => state.sessions.length) === 0;
  const replaceLibrary = useLibraryStore((state) => state.replaceLibrary);
  // The diff prints a target weight, and a weight is never formatted without going through the
  // preference — a kilogram figure shown to somebody working in pounds describes a change they didn't
  // make.
  const unitSystem = useUnitSystem();

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ImportError | null>(null);
  const [ready, setReady] = useState<ReadyMerge | null>(null);
  const [applied, setApplied] = useState<Applied | null>(null);
  const [pasting, setPasting] = useState(false);
  const [pasted, setPasted] = useState('');
  // Keyed by which button copied, since there are two of them: a "Copied" that isn't tied to a target
  // would confirm the wrong one the moment the screen has both on it.
  const [copied, setCopied] = useState<{ target: CopyTarget; status: 'copied' | 'failed' } | null>(null);
  // Kept apart from `copied` rather than folded into it: a share that couldn't open says nothing
  // about the clipboard, and reusing that flag would put "Couldn't reach the clipboard" under a
  // button that never touched it.
  const [shareFailed, setShareFailed] = useState(false);
  // Why the clipboard didn't fill the box — the two outcomes that leave it empty. An empty clipboard
  // is not an error, but silence after a tap reads as a dead button, which is what this exists to
  // avoid.
  const [pasteNote, setPasteNote] = useState<'empty' | 'failed' | null>(null);
  // One flag for both lists rather than one each: they never render together, and carrying the
  // expansion across the merge is the honest default — someone who opened the full list to find an id
  // is the same person reading what landed. It needs no reset between files, because there is no
  // second file: the pick and paste rows are gone once a preview exists, so the only ways out of one
  // are Cancel and Merge.
  const [showAllChanges, setShowAllChanges] = useState(false);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    },
    [],
  );

  const close = () => router.back();

  /** Every refusal goes through here, so none can leave a stale "Copied" over a different error. */
  const refuse = (message: string, report: string | null = null) => {
    setError({ message, report });
    setCopied(null);
  };

  /** A refusal the YAML's author can act on — the only kind that gets a report to hand back. */
  const refuseContent = (reason: ParseError | MergeError) => {
    const message = errorMessage(t, reason);
    refuse(message, `${t('import.repairPrompt')}\n\n${message}`);
  };

  /**
   * The one path to the clipboard, shared by both directions of the loop.
   *
   * The `report` it carries is the refusal plus a line of framing, and deliberately *not* the rejected
   * YAML: an assistant that just wrote it still has it, and a hand-edited file is on disk where the
   * user can point at it, so appending it would mostly mean putting a whole library on the clipboard
   * to say something both ends already know. The `brief` is built in `domain/assistant-brief.ts`.
   */
  const copy = async (target: CopyTarget, text: string) => {
    setShareFailed(false);
    try {
      // The boolean is the API's own way of saying it didn't take, distinct from throwing — so a
      // "Copied" claimed over a false here would be the one thing these buttons must never do.
      if (!(await Clipboard.setStringAsync(text))) {
        setCopied({ target, status: 'failed' });
        return;
      }
      setCopied({ target, status: 'copied' });
      // Announced rather than left to the label change: the button's own text switches to "Copied",
      // which a screen reader doesn't re-read while focus stays put.
      AccessibilityInfo.announceForAccessibility(t('import.copiedAnnouncement'));
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopied(null), 2000);
    } catch {
      // Reachable on web, where the clipboard is permission-gated and throws when denied. For the
      // report that means the error it would replace is the one still worth reading, so both degrade
      // to a note beside the button rather than taking the screen over.
      setCopied({ target, status: 'failed' });
    }
  };

  /**
   * The same brief, handed to the OS share sheet instead of the clipboard.
   *
   * This is the one change that collapses the round trip: copy-then-leave-then-find-the-app-then-paste
   * becomes one tap that lands the text in whichever assistant the user has. **Kettle still never
   * calls a model** — the share sheet is the OS listing apps that accept text, so no vendor is named
   * here or anywhere else, and nothing is transmitted by the app itself. A prefilled URL to a named
   * assistant was the obvious alternative and is impossible regardless: the brief measures ~16 KB
   * against the seed library and grows with the user's, which no URL survives.
   *
   * Sharing plain text rather than a file on purpose. `storage/export.ts` shares by URI because a
   * library *is* a file; this is a prompt, and a chat target takes text into its compose box while an
   * attached `.md` mostly doesn't.
   *
   * Dismissing the sheet resolves normally on native but *not* on web, which is why the `AbortError`
   * check below is load-bearing rather than defensive — see the comment on it. Every other rejection
   * is a genuine failure to open the sheet, and the clipboard button below is the working fallback,
   * which is why that degrades to a note rather than a refusal.
   */
  const shareTheBrief = async (text: string) => {
    setCopied(null);
    setShareFailed(false);
    try {
      await Share.share({ message: text });
    } catch (err) {
      // Backing out of the sheet is not a failure to open one. Native resolves on dismissal, but
      // react-native-web forwards straight to `navigator.share`, which rejects with an `AbortError`
      // — so without this, cancelling a sheet that opened perfectly well on mobile Safari or Chrome
      // leaves "Couldn't open the share sheet" on screen.
      if ((err as Error | undefined)?.name === 'AbortError') return;
      setShareFailed(true);
    }
  };

  /**
   * Fills the paste box from the clipboard, which is the return leg of that same trip.
   *
   * Deliberately stops at filling the box rather than going straight to `review`: the box is what
   * lets someone see they pasted the chat's prose along with the YAML, and the preview behind
   * "Review paste" is the only thing standing between arbitrary text and their library.
   *
   * `getStringAsync` resolves `''` rather than throwing when it has nothing to give, so the silent
   * outcome gets a note — the alternative is a button that visibly does nothing.
   *
   * **That empty string does not only mean "empty".** On iOS 16+ a paste is permission-gated behind
   * a system prompt, and a denial resolves `''` too — expo-clipboard's own docs say there is no way
   * to tell the two apart. So the note is worded to cover both, because the honest message here is a
   * disjunction: telling someone their clipboard is empty while their YAML sits in it is worse than
   * naming both possibilities.
   */
  const pasteFromClipboard = async () => {
    setPasteNote(null);
    try {
      const text = await Clipboard.getStringAsync();
      if (!text.trim()) {
        setPasteNote('empty');
        return;
      }
      setPasted(text);
    } catch {
      // Web's read half is permission-gated and *throws* when denied, unlike iOS's. Reachable only
      // there, which is why this is the branch that can still say "couldn't read" outright.
      setPasteNote('failed');
    }
  };

  /**
   * The only path from a parsed library to a confirmed preview. Every source lands here, so a pack is
   * merged, validated and previewed by exactly the code a hand-written file goes through — which is
   * the point of shipping packs as libraries rather than as a special case: there is no second import
   * path that could accept something this one would refuse.
   */
  const reviewLibrary = (library: Library, source: Source) => {
    if (!currentLibrary) {
      refuse(t('import.libraryNotLoaded'));
      return;
    }
    const merge = mergeLibraries(currentLibrary, library);
    if (!merge.ok) {
      refuseContent(merge.error);
      return;
    }
    setReady({ picked: source, library: merge.library, summary: merge.summary });
  };

  /**
   * The only path from raw YAML to a confirmed preview. Both text sources land here, so a paste is
   * refused for the same reasons and in the same words as a file — the two differ in where the text
   * came from and in nothing else.
   */
  const review = (text: string, source: Source) => {
    const parsed = parseLibraryYaml(text);
    if (!parsed.ok) {
      refuseContent(parsed.error);
      return;
    }
    reviewLibrary(parsed.data, source);
  };

  /**
   * A bundled pack, previewed like anything else.
   *
   * The language is read here and frozen into the preview, rather than at render: what a merge writes
   * becomes the user's own data the moment it lands, and re-picking it on a later language change is
   * the exact rename the never-translate-user-data rule exists to prevent. The pack's *row* is the
   * opposite case and does follow the UI language — it is never written anywhere.
   *
   * No `busy` and no failure of its own: the content is in the bundle, already type-checked, and the
   * only thing that can refuse it is the merge.
   */
  const reviewPack = (pack: ContentPack) => {
    setError(null);
    setReady(null);
    reviewLibrary(contentPackLibrary(pack, i18n.language), {
      title: t(`import.packs.${pack.id}.name`),
      detail: t('import.packs.sourceDetail'),
    });
  };

  /**
   * "9 exercises · 3 workouts · 1 program", counted off the pack's own structure.
   *
   * Three separately-pluralised parts rather than one sentence with three placeholders, because
   * i18next's `count` pluralises the whole string against a single number — a pack with one program
   * and nine exercises has no single number to pluralise against, and English is not the language
   * this would go wrong in.
   */
  const packCountLine = (pack: ContentPack) => {
    const counts = contentPackCounts(pack);
    return [
      t('import.packs.exerciseCount', { count: counts.exercises }),
      t('import.packs.workoutCount', { count: counts.workouts }),
      t('import.packs.programCount', { count: counts.programs }),
    ].join(' · ');
  };

  const pickFile = async () => {
    setError(null);
    setReady(null);
    setBusy(true);
    try {
      const result = await File.pickFileAsync();
      if (result.canceled) return;

      const file = result.result;
      const text = await file.text();
      const sizeBytes = file.size;
      const sizeLabel = sizeBytes < 1024 ? `${sizeBytes} B` : `${(sizeBytes / 1024).toFixed(1)} KB`;
      review(text, { title: file.name, detail: t('import.pickedFrom', { size: sizeLabel }) });
    } catch (err) {
      // The picker's and the filesystem's own message, which is the platform's and stays untranslated
      // — but the sentence around it says which step failed, and that part is ours.
      refuse(t('import.error.readFailed', { detail: (err as Error).message }));
    } finally {
      setBusy(false);
    }
  };

  /**
   * No I/O, so no `busy` and no failure mode of its own — a paste can only be refused by the parser
   * or the merge, which is the whole reason this path is worth having: an assistant's YAML is text in
   * a chat window, and saving it to a file on a phone just to hand it back to the picker is several
   * awkward steps that buy nothing.
   */
  const reviewPaste = () => {
    setError(null);
    setReady(null);
    setPasteNote(null);
    const text = pasted.trim();
    if (!text) return;
    review(text, {
      title: t('import.pastedTitle'),
      detail: t('import.pastedLines', { count: text.split('\n').length }),
    });
  };

  /**
   * Applies the merge and says what it did, rather than closing on the spot.
   *
   * The modal used to vanish the instant the write landed, which is the one moment in the flow with
   * nothing to show for it: the change is invisible by nature — a few ids folded into a library of
   * dozens — so "it closed" was the only evidence the import had happened at all, and it reads the
   * same as a button that did nothing. The extra tap buys a statement of what changed.
   */
  const confirmMerge = async () => {
    if (!ready) return;
    setBusy(true);
    try {
      // Snapshotted before the write, while `changedItems` still describes a real before-and-after.
      const summary = { newCount, updatedCount, items: changedItems.map(({ id, kind, detail }) => ({ id, kind, detail })) };
      await replaceLibrary(ready.library);
      setReady(null);
      setApplied(summary);
      setBusy(false);
      // The screen changes under the user without focus moving, and the heading it swaps in isn't
      // where a screen reader is looking — the same reason the copy buttons announce.
      AccessibilityInfo.announceForAccessibility(
        t('import.applied.announcement', { new: summary.newCount, updated: summary.updatedCount }),
      );
    } catch (err) {
      refuse(t('import.error.saveFailed', { detail: (err as Error).message }));
      setBusy(false);
    }
  };

  // Programs are listed alongside exercises and workouts, and counted in the tiles below, because the
  // merge writes all three (§6: surface updates clearly so local tweaks aren't lost silently). They
  // were missing from both, so a program-only file previewed as "0 new, 0 updated" and then merged
  // its programs anyway — the one case where the preview contradicted what the button was about to do.
  /**
   * What an updated id actually loses, per §6 and open question §12.5. Computed here rather than in
   * `merge.ts` because both sides are already on hand — the store's library and the merged result —
   * so the merge doesn't have to carry a diff nobody but this preview wants.
   *
   * **Called per rendered row, not per changed id.** It used to be folded into `changedItems` as a
   * `changes` field, which meant a re-imported library — where every id counts as an update — diffed
   * its entire contents on *every* render, including the renders that only toggled a button. A real
   * library runs to a few hundred ids and the diff is the expensive half of that pass (measured), so
   * the version that hurts is the one nobody sees. Nothing but a visible row ever reads a diff: the
   * count tiles work off `kind`, and the applied snapshot deliberately drops the field entirely.
   */
  const changesFor = (kind: 'exercise' | 'workout' | 'program', id: string): FieldChange[] => {
    if (!ready || !currentLibrary) return [];
    switch (kind) {
      case 'exercise': {
        const before = currentLibrary.exercises.find((exercise) => exercise.id === id);
        const after = ready.library.exercises.find((exercise) => exercise.id === id);
        return before && after ? diffExercise(before, after, unitSystem) : [];
      }
      case 'workout': {
        const before = currentLibrary.workouts.find((workout) => workout.id === id);
        const after = ready.library.workouts.find((workout) => workout.id === id);
        return before && after ? diffWorkout(before, after) : [];
      }
      case 'program': {
        const before = currentLibrary.programs.find((program) => program.id === id);
        const after = ready.library.programs.find((program) => program.id === id);
        return before && after ? diffProgram(before, after) : [];
      }
    }
  };

  // Each row carries which library it came out of rather than its diff, so that the diff can wait
  // until something is about to draw it. `detail` is the noun the row prints; `entity` is what
  // `changesFor` needs and is never rendered.
  const changedItems = ready
    ? [
        ...ready.summary.newExercises.map((id) => ({
          id,
          kind: 'new' as const,
          entity: 'exercise' as const,
          detail: t('import.newExercise'),
        })),
        ...ready.summary.updatedExercises.map((id) => ({
          id,
          kind: 'updated' as const,
          entity: 'exercise' as const,
          detail: t('import.updatedExercise'),
        })),
        ...ready.summary.newWorkouts.map((id) => ({
          id,
          kind: 'new' as const,
          entity: 'workout' as const,
          detail: t('import.newWorkout'),
        })),
        ...ready.summary.updatedWorkouts.map((id) => ({
          id,
          kind: 'updated' as const,
          entity: 'workout' as const,
          detail: t('import.updatedWorkout'),
        })),
        ...ready.summary.newPrograms.map((id) => ({
          id,
          kind: 'new' as const,
          entity: 'program' as const,
          detail: t('import.newProgram'),
        })),
        ...ready.summary.updatedPrograms.map((id) => ({
          id,
          kind: 'updated' as const,
          entity: 'program' as const,
          detail: t('import.updatedProgram'),
        })),
      ]
    : [];

  const newCount = changedItems.filter((item) => item.kind === 'new').length;
  const updatedCount = changedItems.length - newCount;
  const visibleChangedItems = showAllChanges ? changedItems : changedItems.slice(0, COLLAPSED_CHANGES);
  const appliedItems = applied?.items ?? [];
  const visibleAppliedItems = showAllChanges ? appliedItems : appliedItems.slice(0, COLLAPSED_CHANGES);
  // Hoisted out of the JSX: narrowing `error.report` inside the press handler's closure doesn't hold,
  // since TypeScript has to assume a property can change between render and tap.
  const errorReport = error?.report ?? null;
  const copyLabel = (target: CopyTarget, idle: string) =>
    copied?.target === target && copied.status === 'copied' ? t('import.copied') : idle;
  const copyFailed = (target: CopyTarget) => copied?.target === target && copied.status === 'failed';

  // Lifted out of the idle block so it can render in either of two places, never both. Someone with
  // nothing logged is met by three programs they can add in one tap; someone returning came here to
  // import a file, and demoting the file path for them would trade a real job for a first-run
  // nicety. Only the three source blocks reorder -- the assistant brief stays last for everyone,
  // being the one thing here a first-timer is least likely to want.
  /**
   * The line under the copy button: whichever of the two failures just happened, or the caption.
   *
   * The caption is suppressed when the share row is up, because that row already carries the same
   * `copyBriefDetail` sentence and repeating it under the fallback button puts it on screen twice
   * three lines apart. Where there is no share row it is the only description of what gets copied,
   * so it stays — which is also why `shareFailed` can't be reached in that branch.
   */
  let briefNote: string | null = null;
  if (shareFailed) briefNote = t('import.shareFailed');
  else if (copyFailed('brief')) briefNote = t('import.copyFailed');
  else if (!canShareText) briefNote = t('import.copyBriefDetail');

  const starterPacks = (
    <>
      {/*
      A list of peers, so rows rather than the two dashed pick boxes above: those are two
      *actions* on the same screen, these are three interchangeable things to choose between.
      The heading rule is what gives the list a top edge — without a fill on the rows, the
      first name would otherwise read as one more line of the section's own caption.
    */}
      {/*
        `label`, not `heading`: this is a section marker, and at `heading` it was the same 20px in the
        same color as "Choose exercises.yaml" above it and "Steady & Strong" below it — three peers,
        so it read as one more item rather than as the title of the list under it. `label` +
        `textSecondary` is what Settings' `Section` and Analytics already use for exactly this.
      */}
      <View style={styles.packsHeader}>
        <ThemedText type="label" themeColor="textSecondary">
          {t('import.packs.title')}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {t('import.packs.detail')}
        </ThemedText>
      </View>
      <ListHeaderRule />
      {contentPacks.map((pack, index) => (
        <View key={pack.id}>
          {index > 0 && <ListRowSeparator />}
          <Pressable
            onPress={() => reviewPack(pack)}
            // A pack review is instant, so this isn't about *its* I/O — it's that `pickFile`
            // holds `busy` across the picker and the file read after it, and a pack picked
            // inside that window is silently replaced when the file's merge resolves into the
            // same `ready`. Merge & import would then land the file.
            disabled={busy}
            // No `accessibilityLabel`: the two lines inside name it, and a duplicate would drift
            // from the counts it quotes. Voice Control matches the visible words either way.
            accessibilityRole="button">
            <ListRow>
              <View style={styles.fileText}>
                <ThemedText type="heading">{t(`import.packs.${pack.id}.name`)}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t(`import.packs.${pack.id}.detail`)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {packCountLine(pack)}
                </ThemedText>
              </View>
            </ListRow>
          </Pressable>
        </View>
      ))}
    </>
  );

  /**
   * What goes *out* to an assistant, before there is any YAML to bring in — so not an input source,
   * whatever its position on the screen.
   *
   * Rendered only with a library loaded: the brief's whole point is the ids in it, and offering it
   * empty would hand an assistant a confident list of nothing.
   *
   * **Its position is the one thing that moves with `packsFirst`, and it moves the opposite way to
   * the packs.** For someone returning it sits between the two file rows, because that is the order
   * of the trip — send the format out, paste the YAML back — and it is the affordance they came for.
   * For someone with nothing logged it stays last, under everything: the packs are the offer that
   * costs no typing, and putting "send a JSON Schema somewhere" above them would put the screen's
   * most technical control in front of exactly the reader the packs-first order exists to serve.
   */
  const assistantBlock = currentLibrary && (
    <>
      {canShareText && (
        <Pressable
          onPress={() => shareTheBrief(buildAssistantBrief(currentLibrary))}
          accessibilityRole="button"
          // No `accessibilityLabel`: the two lines inside name it, and a duplicate would drift from
          // them and break Voice Control, which matches the visible words.
          style={[styles.fileRow, styles.pickRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={styles.fileText}>
            <ThemedText type="heading">{t('import.shareBrief')}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('import.copyBriefDetail')}
            </ThemedText>
          </View>
        </Pressable>
      )}
      <View style={styles.briefRow}>
        <Pressable
          onPress={() => copy('brief', buildAssistantBrief(currentLibrary))}
          accessibilityRole="button"
          style={[styles.copyButton, { borderColor: theme.border }]}>
          <ThemedText type="smallMedium" themeColor="textSecondary">
            {/*
              Two labels for one button, because what it needs to say depends on whether the row
              above exists. Beneath the share row it is the fallback and "Copy it instead" is the
              whole sentence; with no share sheet on the platform it is the only way to the brief and
              has to name what it copies.
            */}
            {copyLabel('brief', canShareText ? t('import.copyBriefInstead') : t('import.copyBrief'))}
          </ThemedText>
        </Pressable>
        {briefNote && (
          <ThemedText type="small" themeColor="textSecondary" style={styles.copyFailed}>
            {briefNote}
          </ThemedText>
        )}
      </View>
    </>
  );

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('import.title')}</ThemedText>

        {!ready && !applied && (
          <>
            {/*
              First for someone who has logged nothing. The packs are the only offer on this screen
              that costs no file and no typing, and they sat third, under two verbs naming machinery
              — on the screen the app's own first-run link now points at.
            */}
            {packsFirst && starterPacks}

            <Pressable
              onPress={pickFile}
              disabled={busy}
              accessibilityRole="button"
              accessibilityLabel={t('import.chooseFile')}
              style={[
                styles.fileRow,
                styles.pickRow,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}>
              <View style={styles.fileText}>
                <ThemedText type="heading">{t('import.chooseFile')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.chooseFileDetail')}
                </ThemedText>
              </View>
              {busy && <ActivityIndicator color={theme.accentText} />}
            </Pressable>

            {!packsFirst && assistantBlock}

            <Pressable
              onPress={() => setPasting((open) => !open)}
              accessibilityRole="button"
              accessibilityLabel={t('import.pasteToggle')}
              accessibilityState={{ expanded: pasting }}
              style={[
                styles.fileRow,
                styles.pickRow,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}>
              <View style={styles.fileText}>
                <ThemedText type="heading">{t('import.pasteToggle')}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.pasteToggleDetail')}
                </ThemedText>
              </View>
            </Pressable>

            {pasting && (
              <>
                <TextInput
                  value={pasted}
                  // Typing clears the clipboard note as well as setting the text: it describes one
                  // attempt at filling this box, and left standing it sits beside a box the user
                  // has since filled by hand, still claiming there was nothing to paste.
                  onChangeText={(text) => {
                    setPasted(text);
                    setPasteNote(null);
                  }}
                  multiline
                  autoCapitalize="none"
                  autoCorrect={false}
                  // YAML is whitespace-significant, so the keyboard must not be allowed to "helpfully"
                  // capitalize an id or swap a quote for a smart one on the way in.
                  placeholder={t('import.pastePlaceholder')}
                  placeholderTextColor={theme.textSecondary}
                  accessibilityLabel={t('import.pastePlaceholder')}
                  style={[
                    styles.pasteInput,
                    { borderColor: theme.border, backgroundColor: theme.backgroundElement, color: theme.text },
                  ]}
                />
                {/*
                  The return leg of the trip the share row starts, and the reason it sits here rather
                  than beside Review: on a phone the alternative is a long-press, a "Paste" bubble and
                  a caret placed in a box that already has focus — for text that arrived from another
                  app seconds ago. It fills the box and stops there; `reviewPaste` stays the only way
                  past, because the box is where you see that the chat's prose came along with the
                  YAML.
                */}
                <View style={styles.briefRow}>
                  <Pressable
                    onPress={pasteFromClipboard}
                    accessibilityRole="button"
                    style={[styles.copyButton, { borderColor: theme.border }]}>
                    <ThemedText type="smallMedium" themeColor="accentText">
                      {t('import.pasteFromClipboard')}
                    </ThemedText>
                  </Pressable>
                  {pasteNote && (
                    <ThemedText type="small" themeColor="textSecondary" style={styles.copyFailed}>
                      {pasteNote === 'empty' ? t('import.clipboardEmpty') : t('import.clipboardFailed')}
                    </ThemedText>
                  )}
                </View>
                <Pressable
                  onPress={reviewPaste}
                  // `busy` as well as the empty check, for the same reason the pack rows below carry
                  // it: `pickFile` stays busy across the picker *and* the read that follows it, and a
                  // second source reviewed inside that window is overwritten by the file's merge when
                  // it lands. The user would then confirm a merge for the source they walked away
                  // from. Narrow — the OS picker covers the screen for most of it — but the fix is a
                  // prop, and only the read is uncovered.
                  disabled={busy || !pasted.trim()}
                  accessibilityRole="button"
                  accessibilityLabel={t('import.reviewPaste')}
                  style={[
                    styles.reviewButton,
                    { backgroundColor: theme.accentSoft, opacity: busy || !pasted.trim() ? 0.5 : 1 },
                  ]}>
                  <ThemedText type="heading" themeColor="accentText">
                    {t('import.reviewPaste')}
                  </ThemedText>
                </Pressable>
              </>
            )}

            {!packsFirst && starterPacks}

            {packsFirst && assistantBlock}
          </>
        )}

        {ready && (
          <View style={[styles.fileRow, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            <View style={styles.fileText}>
              <ThemedText type="heading" style={styles.fileName}>
                {ready.picked.title}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {ready.picked.detail}
              </ThemedText>
            </View>
          </View>
        )}

        {error && (
          <View style={styles.error}>
            <ThemedText type="small" style={{ color: theme.accentText }}>
              {error.message}
            </ThemedText>
            {errorReport && (
              <View style={styles.copyRow}>
                <Pressable
                  onPress={() => copy('report', errorReport)}
                  accessibilityRole="button"
                  style={[styles.copyButton, { borderColor: theme.border }]}>
                  <ThemedText type="smallMedium" themeColor="textSecondary">
                    {copyLabel('report', t('import.copyError'))}
                  </ThemedText>
                </Pressable>
                {copyFailed('report') && (
                  <ThemedText type="small" themeColor="textSecondary" style={styles.copyFailed}>
                    {t('import.copyFailed')}
                  </ThemedText>
                )}
              </View>
            )}
          </View>
        )}

        {ready && (
          <>
            <View style={styles.countsRow}>
              <View style={[styles.countCard, { backgroundColor: theme.accentSoft }]}>
                <ThemedText type="subtitle" themeColor="accentText">
                  {newCount}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.new')}
                </ThemedText>
              </View>
              <View style={[styles.countCard, { backgroundColor: theme.accentCalmSoft }]}>
                <ThemedText type="subtitle" themeColor="accentCalmText">
                  {updatedCount}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.updated')}
                </ThemedText>
              </View>
              <View
                style={[
                  styles.countCard,
                  { backgroundColor: theme.backgroundElement, borderWidth: 1, borderColor: theme.border },
                ]}>
                <ThemedText type="subtitle">
                  {ready.summary.newWorkouts.length + ready.summary.updatedWorkouts.length}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.workoutNoun', {
                    count: ready.summary.newWorkouts.length + ready.summary.updatedWorkouts.length,
                  })}
                </ThemedText>
              </View>
            </View>

            <View style={styles.changedList}>
              {visibleChangedItems.map((item) => (
                <View key={`${item.detail}-${item.id}`}>
                  <View style={styles.changedRow}>
                    <ThemedText
                      style={[
                        styles.changedGlyph,
                        { color: item.kind === 'new' ? theme.accentText : theme.accentCalmText },
                      ]}>
                      {item.kind === 'new' ? '+' : '↻'}
                    </ThemedText>
                    <ThemedText type="smallMedium" style={styles.changedName}>
                      {item.id}
                    </ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {item.detail}
                    </ThemedText>
                  </View>
                  {/*
                    Only for updates, and only what moved. An updated id whose definition is
                    byte-identical says so instead of showing an empty indent — `mergeById` classifies
                    by id, not by value, so re-importing your own export lands here for every item and
                    would otherwise look like a wall of unexplained overwrites.
                  */}
                  {item.kind === 'updated' && <ChangedFields changes={changesFor(item.entity, item.id)} />}
                </View>
              ))}
              {changedItems.length === 0 && (
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.noChanges')}
                </ThemedText>
              )}
              {changedItems.length > COLLAPSED_CHANGES && (
                <ChangeListToggle
                  total={changedItems.length}
                  expanded={showAllChanges}
                  onToggle={() => setShowAllChanges((shown) => !shown)}
                />
              )}
            </View>

            <ThemedText type="small" themeColor="textSecondary" style={styles.note}>
              {t('import.updateNote')}
            </ThemedText>
          </>
        )}

        {applied && (
          <>
            <View style={[styles.appliedCard, { backgroundColor: theme.accentSoft }]}>
              <ThemedText type="heading" themeColor="accentText">
                {t('import.applied.title')}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {t('import.applied.newCount', { count: applied.newCount })} ·{' '}
                {t('import.applied.updatedCount', { count: applied.updatedCount })}
              </ThemedText>
            </View>

            {/*
              The same rows as the preview, minus the per-field diffs — those describe a "before" the
              write has already replaced, so re-showing them would be a claim this screen can no
              longer stand behind.
            */}
            <View style={styles.changedList}>
              {visibleAppliedItems.map((item) => (
                <View key={`${item.detail}-${item.id}`} style={styles.changedRow}>
                  <ThemedText
                    style={[styles.changedGlyph, { color: item.kind === 'new' ? theme.accentText : theme.accentCalmText }]}>
                    {item.kind === 'new' ? '+' : '↻'}
                  </ThemedText>
                  <ThemedText type="smallMedium" style={styles.changedName}>
                    {item.id}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textSecondary">
                    {item.detail}
                  </ThemedText>
                </View>
              ))}
              {applied.items.length === 0 && (
                <ThemedText type="small" themeColor="textSecondary">
                  {t('import.noChanges')}
                </ThemedText>
              )}
              {applied.items.length > COLLAPSED_CHANGES && (
                <ChangeListToggle
                  total={applied.items.length}
                  expanded={showAllChanges}
                  onToggle={() => setShowAllChanges((shown) => !shown)}
                />
              )}
            </View>
          </>
        )}
      </ScrollView>

      {/*
        Pinned below the scroll rather than being its last child, because the preview's length is a
        property of the imported file: a re-imported library renders a row per changed id and a line
        per changed field, which put Merge several screens down with nothing on screen saying it was
        there. A primary action that a big enough file can hide is the same as one that isn't there.
      */}
      <View style={[styles.footer, { borderTopColor: theme.border }]}>
        <View style={styles.footerInner}>
          {/*
            Once the write has landed there is nothing left to cancel and nothing left to merge, so the
            pair collapses to one button. Leaving "Cancel" beside a completed import would suggest it
            could still be undone, which it can't — the library is already on disk.
          */}
          {applied ? (
            <View style={styles.buttonRow}>
              <Pressable
                onPress={close}
                accessibilityRole="button"
                accessibilityLabel={t('common.done')}
                style={[styles.mergeButton, { backgroundColor: theme.accent }]}>
                <ThemedText type="heading" style={{ color: theme.onAccent }}>
                  {t('common.done')}
                </ThemedText>
              </Pressable>
            </View>
          ) : (
            <View style={styles.buttonRow}>
              <Pressable
                onPress={close}
                accessibilityRole="button"
                accessibilityLabel={t('common.cancel')}
                style={[styles.cancelButton, { borderColor: theme.border }]}>
                <ThemedText type="heading" themeColor="textSecondary">
                  {t('common.cancel')}
                </ThemedText>
              </Pressable>
              <Pressable
                onPress={confirmMerge}
                disabled={!ready || busy}
                accessibilityRole="button"
                accessibilityLabel={t('import.mergeButton')}
                style={[styles.mergeButton, { backgroundColor: theme.accent, opacity: !ready || busy ? 0.5 : 1 }]}>
                <ThemedText type="heading" style={{ color: theme.onAccent }}>
                  {t('import.mergeButton')}
                </ThemedText>
              </Pressable>
            </View>
          )}
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollContent: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.four,
  },
  fileRow: {
    marginTop: Spacing.three - 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: 14,
    padding: Spacing.two + 4,
  },
  pickRow: {
    borderStyle: 'dashed',
  },
  // No leading glyph on these rows, deliberately. There used to be two outlined squares — a taller
  // one for "choose a file", a square one for "paste" — sized apart so the rows would read as
  // different sources. They didn't: the 6px difference was too small to carry meaning and just
  // looked misaligned, and an empty outlined square beside a label reads as an unchecked checkbox,
  // implying a selection these rows never had. The labels already draw the distinction.
  pasteInput: {
    marginTop: Spacing.two,
    minHeight: 132,
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: Spacing.two + 4,
    paddingTop: Spacing.one + 4,
    paddingBottom: Spacing.one + 4,
    fontSize: 14,
    textAlignVertical: 'top',
  },
  reviewButton: {
    marginTop: Spacing.two,
    minHeight: 48,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fileText: {
    flex: 1,
    gap: 2,
  },
  // Only the gap above: `ListHeaderRule` carries the space below it, so that the first row sits the
  // same distance from the line as every other row does from its separator.
  // `Spacing.two` under the label rather than the 2px a heading took: an uppercase tracked marker
  // needs air under it to read as a marker, and it is what Settings' own section titles use.
  packsHeader: {
    marginTop: Spacing.three,
    gap: Spacing.two,
  },
  fileName: {},
  error: {
    marginTop: Spacing.two,
    gap: Spacing.two - 2,
    alignItems: 'flex-start',
  },
  copyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flexWrap: 'wrap',
  },
  briefRow: {
    marginTop: Spacing.three - 2,
    gap: Spacing.one + 2,
    alignItems: 'flex-start',
  },
  copyButton: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: Spacing.two + 2,
    borderWidth: 1,
    borderRadius: 12,
  },
  copyFailed: {
    flexShrink: 1,
  },
  appliedCard: {
    marginTop: Spacing.three - 2,
    borderRadius: 14,
    padding: Spacing.two + 4,
    gap: 2,
  },
  countsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.three - 2,
  },
  countCard: {
    flex: 1,
    alignItems: 'center',
    borderRadius: 12,
    paddingVertical: Spacing.two + 2,
  },
  changedList: {
    marginTop: Spacing.three - 2,
    gap: Spacing.two - 1,
  },
  changedRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + 2,
  },
  changedGlyph: {
    width: 18,
    textAlign: 'center',
    fontWeight: '700',
  },
  changedName: {
    flex: 1,
  },
  // Indented under the id it belongs to, past the glyph column so the two read as one entry.
  diffList: {
    marginTop: 2,
    marginLeft: 18 + Spacing.two + 2,
    gap: 1,
  },
  note: {
    marginTop: Spacing.two + 4,
  },
  // Full-bleed so its rule spans the screen, with the buttons themselves held to the same centred
  // column as the scroll content — a border stopping at `MaxContentWidth` on a tablet reads as a card.
  footer: {
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: Spacing.three,
    paddingTop: Spacing.two + 2,
    paddingBottom: Spacing.one,
  },
  footerInner: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: MaxContentWidth,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: Spacing.three - 4,
  },
  showAllButton: {
    marginTop: Spacing.one + 2,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderRadius: 12,
  },
  cancelButton: {
    flex: 1,
    minHeight: 52,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mergeButton: {
    flex: 1.4,
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
