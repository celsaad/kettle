import { NativeTabs } from 'expo-router/unstable-native-tabs';
import { useTranslation } from 'react-i18next';

import { useTheme } from '@/hooks/use-theme';

export default function TabLayout() {
  const theme = useTheme();
  const { t } = useTranslation();

  return (
    // Label and icon colors have to be set explicitly: without them the native tab bar falls back to
    // platform defaults, which are picked for a system-colored bar, not this app's `backgroundElement`
    // — against the near-white light-mode bar that left labels invisible and icons washed out. Same
    // focused/unfocused pairing the web tab bar uses (`_layout.web.tsx`): accentText / textSecondary.
    <NativeTabs
      backgroundColor={theme.backgroundElement}
      indicatorColor={theme.backgroundSelected}
      labelStyle={{ default: { color: theme.textSecondary }, selected: { color: theme.accentText } }}
      iconColor={{ default: theme.textSecondary, selected: theme.accentText }}
      tintColor={theme.accentText}>
      {/* `house` rather than something workout-shaped: this is still the tab the app opens on, and
          the icon's job is "home", not a second reading of the label beside it. */}
      <NativeTabs.Trigger name="index">
        <NativeTabs.Trigger.Label>{t('tabs.workouts')}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf={{ default: 'house', selected: 'house.fill' }} md="home" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="library">
        <NativeTabs.Trigger.Label>{t('tabs.library')}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf={{ default: 'book', selected: 'book.fill' }} md="menu_book" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="history">
        <NativeTabs.Trigger.Label>{t('tabs.history')}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf={{ default: 'clock', selected: 'clock.fill' }} md="history" />
      </NativeTabs.Trigger>

      <NativeTabs.Trigger name="programs">
        <NativeTabs.Trigger.Label>{t('tabs.programs')}</NativeTabs.Trigger.Label>
        <NativeTabs.Trigger.Icon sf={{ default: 'calendar', selected: 'calendar' }} md="calendar_month" />
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
