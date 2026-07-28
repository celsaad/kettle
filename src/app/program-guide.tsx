import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

function CodeBlock({ children }: { children: string }) {
  const theme = useTheme();
  return (
    <ThemedView type="backgroundElement" style={[styles.codeBlock, { borderColor: theme.border }]}>
      <ThemedText style={styles.codeText}>{children}</ThemedText>
    </ThemedView>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <ThemedText type="heading" style={styles.sectionTitle}>
        {title}
      </ThemedText>
      {children}
    </View>
  );
}

export default function ProgramGuideScreen() {
  const theme = useTheme();
  const close = () => router.back();

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">Writing a program by hand</ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.intro}>
          Programs — multi-week plans that step through your workouts, optionally tweaking sets/reps/ rounds as weeks go by —
          can be built right in the app, from the + button on the Programs tab. This page is for the other way: writing one
          as YAML and importing the file, which is handy for drafting a long block on a real keyboard or sharing it with
          someone else.
        </ThemedText>

        <Section title="1. Find your workout IDs">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            A program points at workouts by their id, not their display name. A workout's id is set automatically from its
            name when you create it in Build: lowercased, with anything that isn't a letter or number turned into a single
            hyphen. So a workout named <ThemedText style={styles.inline}>"Calisthenics A"</ThemedText> has the id{' '}
            <ThemedText style={styles.inline}>calisthenics-a</ThemedText>.
          </ThemedText>
        </Section>

        <Section title="2. Write the program">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            Save this as a .yaml file, filling in your own workout ids:
          </ThemedText>
          <CodeBlock>{`version: 1

programs:
  - id: pull-progression
    name: 6-Week Pull Progression
    weeks:
      - week: 1
        workout: calisthenics-a
        notes: Baseline — see where you land.
      - week: 3
        workout: calisthenics-a
        overrides:
          - exercise: pullups
            config: { sets: 5 }
      - week: 6
        workout: finisher-circuit
        notes: Deload.`}</CodeBlock>
        </Section>

        <Section title="3. Fields">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            • <ThemedText style={styles.inline}>id</ThemedText> — the program's own slug, hand-typed (lowercase-with-hyphens
            is conventional). {'\n'}• <ThemedText style={styles.inline}>name</ThemedText> — what shows in the Programs list.{' '}
            {'\n'}• <ThemedText style={styles.inline}>weeks</ThemedText> — a list; each entry needs a{' '}
            <ThemedText style={styles.inline}>week</ThemedText> number and a{' '}
            <ThemedText style={styles.inline}>workout</ThemedText> id. {'\n'}•{' '}
            <ThemedText style={styles.inline}>notes</ThemedText> — optional freeform text shown on that week. {'\n'}•{' '}
            <ThemedText style={styles.inline}>overrides</ThemedText> — optional, see below.
          </ThemedText>
        </Section>

        <Section title="4. Overrides (optional)">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            Each entry targets exactly one exercise or one circuit block, by id, with a partial config patch using that
            exercise's/circuit's own field names:
          </ThemedText>
          <CodeBlock>{`overrides:
  - exercise: pullups
    config: { sets: 5 }
  - block: finisher-rounds
    config: { rounds: 2 }`}</CodeBlock>
          <ThemedText themeColor="textSecondary" style={styles.body}>
            An exercise override only makes sense for a single exercise block. A block override only works on a circuit that
            was given its own id in Build (the optional "Block ID" field when editing a circuit) — it patches the circuit's
            own <ThemedText style={styles.inline}>rounds</ThemedText>/rest fields, not one member's config. Overrides don't
            carry forward — repeat one in every later week you want it to keep applying to.
          </ThemedText>
        </Section>

        <Section title="5. More than one session a week (optional)">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            Add a freeform <ThemedText style={styles.inline}>day</ThemedText> label when two weeks share the same week number
            — e.g. a push/pull split:
          </ThemedText>
          <CodeBlock>{`weeks:
  - week: 1
    day: Monday
    workout: push-day
  - week: 1
    day: Thursday
    workout: pull-day`}</CodeBlock>
        </Section>

        <Section title="6. Import it">
          <ThemedText themeColor="textSecondary" style={styles.body}>
            Library tab → Import → pick your file → review the summary → Merge & import. Programs merge by id: a new id gets
            added, but re-importing an id that already exists replaces that whole program — all its weeks — with what's in
            the file, it doesn't merge week-by-week. So to add or change one week, include the program's complete, up-to-date
            week list in the file you import, not just the changed week.
          </ThemedText>
        </Section>

        <Pressable onPress={close} style={[styles.doneButton, { backgroundColor: theme.accent }]}>
          <ThemedText type="heading" style={{ color: theme.onAccent }}>
            Done
          </ThemedText>
        </Pressable>
      </ScrollView>
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
  intro: {
    marginTop: Spacing.one + 2,
    lineHeight: 20,
  },
  section: {
    marginTop: Spacing.four - 4,
  },
  sectionTitle: {
    marginBottom: Spacing.one + 2,
  },
  body: {
    lineHeight: 20,
    marginBottom: Spacing.two - 2,
  },
  inline: {
    fontFamily: 'monospace',
    fontSize: 13,
  },
  codeBlock: {
    borderRadius: 12,
    borderWidth: 1,
    padding: Spacing.two + 4,
    marginBottom: Spacing.two - 2,
  },
  codeText: {
    fontFamily: 'monospace',
    fontSize: 12.5,
    lineHeight: 18,
  },
  doneButton: {
    height: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.four,
  },
});
