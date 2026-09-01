import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { Pressable, ScrollView, StyleSheet, View, type StyleProp, type TextStyle } from 'react-native';
import { Trans, useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

function CodeBlock({ children }: { children: string }) {
  const theme = useTheme();
  return (
    <ThemedView type="backgroundElement" style={[styles.codeBlock, { borderColor: theme.border }]}>
      <ThemedText style={styles.codeText}>{children}</ThemedText>
    </ThemedView>
  );
}

/**
 * A paragraph of the guide, with `<c>` in the string rendered as a code span.
 *
 * `Trans` rather than cutting each sentence at its tokens and concatenating the pieces: the tokens
 * are YAML field names sitting mid-sentence, and a language that orders the clause differently has to
 * be able to move them — a fixed sequence of fragments is precisely the thing that cannot be
 * translated. This is the only screen that needs it; everywhere else in the app the interpolated
 * values are whole strings rather than styled fragments.
 *
 * The tokens themselves stay put in every locale, being YAML keys rather than words.
 */
function Body({ id, style }: { id: string; style?: StyleProp<TextStyle> }) {
  return (
    <ThemedText themeColor="textSecondary" style={style ?? styles.body}>
      <Trans i18nKey={id} components={{ c: <ThemedText style={styles.inline} /> }} />
    </ThemedText>
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
  const { t } = useTranslation();
  const close = () => router.back();

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={close} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('programGuide.title')}</ThemedText>
        <Body id="programGuide.intro" style={styles.intro} />

        <Section title={t('programGuide.ids.title')}>
          <Body id="programGuide.ids.body" />
        </Section>

        <Section title={t('programGuide.write.title')}>
          <Body id="programGuide.write.body" />
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

        <Section title={t('programGuide.fields.title')}>
          <Body id="programGuide.fields.body" />
        </Section>

        <Section title={t('programGuide.overrides.title')}>
          <Body id="programGuide.overrides.intro" />
          <CodeBlock>{`overrides:
  - exercise: pullups
    config: { sets: 5 }
  - block: finisher-rounds
    config: { rounds: 2 }`}</CodeBlock>
          <Body id="programGuide.overrides.body" />
        </Section>

        <Section title={t('programGuide.multiDay.title')}>
          <Body id="programGuide.multiDay.body" />
          <CodeBlock>{`weeks:
  - week: 1
    day: Monday
    workout: push-day
  - week: 1
    day: Thursday
    workout: pull-day`}</CodeBlock>
        </Section>

        <Section title={t('programGuide.restDays.title')}>
          <Body id="programGuide.restDays.body" />
          <CodeBlock>{`weeks:
  - week: 1
    day: Day 1
    workout: push-day
  - week: 1
    day: Day 2
    rest_day: true
    notes: Walk, stretch, nothing heavy.
  - week: 1
    day: Day 3
    workout: pull-day`}</CodeBlock>
          <Body id="programGuide.restDays.tail" />
        </Section>

        <Section title={t('programGuide.importIt.title')}>
          <Body id="programGuide.importIt.body" />
          {/*
           * The step this page builds up to, as the control rather than as directions to it. It used
           * to read "Library tab → Import → pick your file → …", which asks someone who has just
           * finished writing a program to go and find the screen that accepts it.
           *
           * `replace`, not `push`: both this screen and import are modals, so pushing leaves the user
           * two deep and returns them mid-scroll into a guide they're done with. Replacing means
           * dismissing import lands on Programs, which is where the imported program has appeared.
           */}
          <Pressable
            onPress={() => router.replace('/import')}
            accessibilityRole="button"
            style={[styles.importButton, { borderColor: theme.border }]}>
            <ThemedText type="smallMedium">{t('programGuide.openImport')}</ThemedText>
          </Pressable>
        </Section>

        <Pressable onPress={close} accessibilityRole="button" style={[styles.doneButton, { backgroundColor: theme.accent }]}>
          <ThemedText type="heading" style={{ color: theme.onAccent }}>
            {t('common.done')}
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
  // Bordered rather than filled: the filled accent button on this screen is Done, and a second solid
  // button would read as a peer of it. 44px floor via `minHeight`, like every other control here.
  importButton: {
    minHeight: 44,
    borderRadius: 15,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.two,
    paddingHorizontal: Spacing.three,
    alignSelf: 'flex-start',
  },
  doneButton: {
    minHeight: 52,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.four,
  },
});
