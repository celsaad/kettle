import { StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Three steps for someone who has just installed the app.
 *
 * Kettle opens on a seeded library, so a first run already has something to start — but nothing said
 * what the tabs were for, and the surface area is the app's real onboarding problem: a runner, a
 * library, a builder, programs, history and an importer all arrive at once. So this names the three
 * that matter in order and stays quiet about the rest; Library and History are discoverable once you
 * have a reason to look for them.
 *
 * **Nothing here is tappable, deliberately.** A card that duplicates navigation invites the
 * broken-button look this codebase has already removed twice (the arrows on the old Recent rows, and
 * `SessionNextCard`'s). Every step's affordance is somewhere else on the screen and one tap away —
 * the workout list below for step 2, the starter-pack link directly under this card for step 3.
 *
 * **Step 2 names no tab, which is a correction rather than a style choice.** It used to read "Make it
 * yours in Build" — and when Build merged into this screen, that became an instruction to go to the
 * tab you are already looking at. It now points at the workout list directly below the card, which is
 * where editing actually starts.
 *
 * **Step 3 carries the claim the store listing is written on** — a library that is a file you own and
 * can have an assistant write — where it used to read "Plan weeks in Programs". That step restated
 * the tab bar to someone who had installed on the strength of the ownership pitch and would not meet
 * it anywhere in the app. Programs keeps its tab, its empty state and the guide behind it.
 *
 * Two constraints on the wording, both load-bearing rather than stylistic. It may not imply the app
 * generates anything: the app has no AI features and never contacts a model, which is what the Play
 * zero-data-collected declaration rests on, so the assistant stays outside ("have an AI draft one",
 * as the listing words it). And it may not carry training intent — see the ownership entry in
 * `docs/decisions.md`. Format, never advice.
 *
 * Who sees it is the caller's business — see `sessions.length === 0` in the Workouts screen.
 */
export function FirstRunCard() {
  const theme = useTheme();
  const { t } = useTranslation();

  const steps = [
    { title: t('today.firstRun.step1Title'), body: t('today.firstRun.step1Body') },
    { title: t('today.firstRun.step2Title'), body: t('today.firstRun.step2Body') },
    { title: t('today.firstRun.step3Title'), body: t('today.firstRun.step3Body') },
  ];

  return (
    <ThemedView type="backgroundElement" style={[styles.card, { borderColor: theme.border }]}>
      <ThemedText type="label" themeColor="accentText">
        {t('today.firstRun.title')}
      </ThemedText>

      {steps.map((step, index) => (
        <View key={step.title} style={styles.step}>
          {/*
            minWidth/minHeight rather than a fixed size: the badge holds text, so it has to grow with
            the accessibility text size instead of clipping the numeral. (Decorative geometry like the
            play triangle is the case that keeps a fixed height, not this.)
          */}
          <View style={[styles.badge, { backgroundColor: theme.accentSoft }]}>
            <ThemedText type="smallMedium" themeColor="accentText">
              {index + 1}
            </ThemedText>
          </View>
          <View style={styles.stepText}>
            <ThemedText type="smallMedium">{step.title}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {step.body}
            </ThemedText>
          </View>
        </View>
      ))}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  card: {
    marginTop: Spacing.three,
    borderRadius: 22,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  step: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  badge: {
    minWidth: 24,
    minHeight: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
  },
  stepText: {
    flex: 1,
    gap: 2,
  },
});
