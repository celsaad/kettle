import { router } from 'expo-router';
import { ErrorCode, finishTransaction, useIAP, type Purchase } from 'expo-iap';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ModalHeader } from '@/components/modal-header';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { TIP_SKU_LIST, tierForSku, toTipTierOffers, type TipTier } from '@/domain/tip';
import { useTheme } from '@/hooks/use-theme';
import { isIapAvailable } from '@/hooks/safe-iap';
import { useTipStore } from '@/state/tip-store';

export { ModalErrorBoundary as ErrorBoundary } from '@/components/error-fallback';

const TIER_LABEL_KEYS: Record<TipTier, string> = {
  small: 'support.tierSmall',
  medium: 'support.tierMedium',
  large: 'support.tierLarge',
};

/** Transient outcome of the last purchase attempt. Cancellation returns to `null`, not an error. */
type Outcome = { kind: 'pending' } | { kind: 'failed' } | { kind: 'notSaved' } | null;

/**
 * Everything that touches Play Billing, split out so `useIAP` is only ever mounted where the native
 * module exists — hooks can't be called conditionally, so the check has to be a component boundary
 * rather than an `if` inside one. See `safe-iap.ts` for why the module can be missing.
 */
function TipTiers({ onRecorded }: { onRecorded: (tier: TipTier) => Promise<boolean> }) {
  const theme = useTheme();
  const { t } = useTranslation();
  const [purchasingSku, setPurchasingSku] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<Outcome>(null);
  const [storeFailed, setStoreFailed] = useState(false);

  // Uses the module-level `finishTransaction` rather than the one `useIAP` returns, so this handler
  // doesn't have to close over a binding the hook below hasn't produced yet. Both do the same work.
  const onPurchaseSuccess = async (purchase: Purchase) => {
    setPurchasingSku(null);

    // Slow payment methods (boleto, some cards) land here before any money moves. Acknowledging it
    // as a tip would thank the user for a payment that can still fail, and finishing the transaction
    // now would consume a purchase Play hasn't completed.
    if (purchase.purchaseState === 'pending') {
      setOutcome({ kind: 'pending' });
      return;
    }

    const tier = tierForSku(purchase.productId);
    if (!tier) return;

    const saved = await onRecorded(tier);
    setOutcome(saved ? null : { kind: 'notSaved' });

    // `isConsumable` is what makes a tip repeatable: without it Play treats the SKU as owned and
    // refuses every subsequent purchase of the same tier.
    await finishTransaction({ purchase, isConsumable: true });
  };

  const { connected, products, fetchProducts, requestPurchase } = useIAP({
    onPurchaseSuccess,
    onPurchaseError: (error) => {
      setPurchasingSku(null);
      // Backing out of the Play sheet is the common path, not a failure worth a red message.
      setOutcome(error.code === ErrorCode.UserCancelled ? null : { kind: 'failed' });
    },
    onError: () => setStoreFailed(true),
  });

  useEffect(() => {
    if (!connected) return;
    setStoreFailed(false);
    fetchProducts({ skus: TIP_SKU_LIST, type: 'in-app' });
  }, [connected, fetchProducts]);

  const offers = toTipTierOffers(products);

  const tip = async (sku: string) => {
    setOutcome(null);
    setPurchasingSku(sku);
    try {
      await requestPurchase({ request: { google: { skus: [sku] } }, type: 'in-app' });
    } catch {
      // onPurchaseError covers the store's own failures; this only catches a rejected call.
      setPurchasingSku(null);
      setOutcome({ kind: 'failed' });
    }
  };

  const outcomeMessage =
    outcome === null
      ? null
      : { pending: t('support.pending'), failed: t('support.failed'), notSaved: t('support.notSaved') }[outcome.kind];

  return (
    <>
      {storeFailed ? (
        <View style={styles.stateBlock}>
          <ThemedText type="small" style={{ color: theme.accentText }}>
            {t('support.storeUnavailable')}
          </ThemedText>
        </View>
      ) : offers.length === 0 ? (
        <View style={styles.stateBlock}>
          <ActivityIndicator color={theme.textSecondary} />
          <ThemedText type="small" themeColor="textSecondary">
            {t('support.loading')}
          </ThemedText>
        </View>
      ) : (
        <View style={styles.tierList}>
          {offers.map((offer) => {
            const label = t(TIER_LABEL_KEYS[offer.tier]);
            const busy = purchasingSku === offer.sku;
            return (
              <Pressable
                key={offer.sku}
                onPress={() => tip(offer.sku)}
                disabled={purchasingSku !== null}
                accessibilityRole="button"
                accessibilityLabel={t('support.tipA11y', { tier: label, price: offer.displayPrice })}
                accessibilityState={{ disabled: purchasingSku !== null, busy }}
                style={({ pressed }) => pressed && styles.pressed}>
                <ThemedView
                  type="backgroundElement"
                  style={[styles.tierRow, { borderColor: theme.border }, purchasingSku !== null && !busy && styles.dimmed]}>
                  <ThemedText type="heading" style={styles.tierLabel}>
                    {label}
                  </ThemedText>
                  {busy ? (
                    <ActivityIndicator color={theme.textSecondary} />
                  ) : (
                    // `accentText`, not `accent`: the theme notes record white-on-accent at 3.64,
                    // which clears AA-large only, and this sits at body size on a plain surface.
                    <ThemedText type="heading" style={{ color: theme.accentText }}>
                      {offer.displayPrice}
                    </ThemedText>
                  )}
                </ThemedView>
              </Pressable>
            );
          })}
        </View>
      )}

      {outcomeMessage && (
        <ThemedText type="small" style={[styles.paragraph, { color: theme.accentText }]}>
          {outcomeMessage}
        </ThemedText>
      )}
    </>
  );
}

export default function SupportScreen() {
  const theme = useTheme();
  const { t } = useTranslation();
  const supporter = useTipStore((state) => state.supporter);
  const hydrate = useTipStore((state) => state.hydrate);
  const recordTip = useTipStore((state) => state.recordTip);

  // Hydrated here rather than in _layout.tsx — see the note on useTipStore for why cold start
  // deliberately doesn't wait on this file.
  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <SafeAreaView
      style={[styles.safeArea, { backgroundColor: theme.background }]}
      edges={['top', 'bottom', 'left', 'right']}>
      <ModalHeader onClose={() => router.back()} />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <ThemedText type="subtitle">{t('support.title')}</ThemedText>

        <ThemedText themeColor="textSecondary" style={styles.paragraph}>
          {t('support.pitch')}
        </ThemedText>
        <ThemedText themeColor="textSecondary" style={styles.paragraph}>
          {t('support.why')}
        </ThemedText>

        {/* A past tip is still worth acknowledging even when the store can't be reached now. */}
        {supporter.tipCount > 0 && (
          <ThemedView type="backgroundElement" style={[styles.thankYou, { borderColor: theme.border }]}>
            <ThemedText type="heading">{t('support.thankYouTitle')}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {t('support.thankYouBody', { count: supporter.tipCount })}
            </ThemedText>
          </ThemedView>
        )}

        {isIapAvailable ? (
          <TipTiers onRecorded={recordTip} />
        ) : (
          <View style={styles.stateBlock}>
            <ThemedText type="small" style={{ color: theme.accentText }}>
              {t('support.storeUnavailable')}
            </ThemedText>
          </View>
        )}
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
  paragraph: {
    marginTop: Spacing.three,
  },
  thankYou: {
    marginTop: Spacing.four,
    gap: Spacing.one,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  stateBlock: {
    marginTop: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  tierList: {
    marginTop: Spacing.four,
    gap: Spacing.two - 3,
  },
  tierRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    // minHeight, never height: a fixed height clips at large accessibility text sizes.
    minHeight: 44,
    borderRadius: 16,
    borderWidth: 1,
    padding: Spacing.two + 6,
  },
  tierLabel: {
    flex: 1,
  },
  pressed: {
    opacity: 0.7,
  },
  dimmed: {
    opacity: 0.5,
  },
});
