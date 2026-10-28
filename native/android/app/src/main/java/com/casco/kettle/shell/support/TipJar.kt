package com.casco.kettle.shell.support

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.TipTier
import com.casco.kettle.domain.TipTierOffer
import com.casco.kettle.domain.tierForSku
import com.casco.kettle.domain.toTipTierOffers
import com.casco.kettle.domain.withTipRecorded
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the store part of the screen shows. */
sealed interface StoreState {
    data object Connecting : StoreState

    /**
     * No store, a store that failed to answer, or one that sells none of the tips. All three read the
     * same to the user, and RN's screen spun forever on the last.
     */
    data object Unavailable : StoreState

    data class Ready(
        val offers: List<TipTierOffer>,
    ) : StoreState
}

/** The outcome of the last purchase, shown under the tiers. Backing out is no outcome at all. */
enum class Outcome { PENDING, FAILED, NOT_SAVED }

/**
 * Support's state and everything it does with the store and the file, which the screen only draws.
 * Everything runs on [scope]'s thread but the file, which is [io]'s.
 */
class TipJar(
    private val file: SupporterFile,
    private val billing: TipBilling,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val io: CoroutineDispatcher,
    private val onSaved: () -> Unit,
) {
    var supporter by mutableStateOf(SupporterState.EMPTY)
        private set
    var store by mutableStateOf<StoreState>(StoreState.Connecting)
        private set

    /** The SKU whose store sheet is open, which holds every other tier off. */
    var purchasing by mutableStateOf<String?>(null)
        private set
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    /** One tip at a time: each counts from the last, so two at once would write the same count. */
    private val recording = Mutex()

    fun open() {
        scope.launch {
            supporter = withContext(io) { file.read() }
            connect()
        }
    }

    fun retry() {
        scope.launch { connect() }
    }

    fun tip(sku: String) {
        outcome = null
        purchasing = sku
        billing.launch(sku)
    }

    fun close() = billing.close()

    private suspend fun connect() {
        store = StoreState.Connecting
        if (!billing.connect(::onUpdate)) {
            store = StoreState.Unavailable
            return
        }
        settleHeld()
        val offers = billing.products()?.let(::toTipTierOffers)
        store = if (offers.isNullOrEmpty()) StoreState.Unavailable else StoreState.Ready(offers)
    }

    private fun onUpdate(update: PurchaseUpdate) {
        purchasing = null
        when (update) {
            is PurchaseUpdate.Bought -> {
                scope.launch {
                    outcome = update.purchases.map { settle(it) }.firstOrNull { it != null }
                }
            }

            PurchaseUpdate.Cancelled -> {
                outcome = null
            }

            PurchaseUpdate.Failed -> {
                outcome = Outcome.FAILED
            }

            // Settling what the store holds is what lets it sell the tier again.
            PurchaseUpdate.AlreadyOwned -> {
                outcome = null
                scope.launch { settleHeld() }
            }
        }
    }

    /**
     * Every paid tip the store holds unconsumed: one that completed while Support was closed, an RN-era
     * one included, which Play refunds after three days unless it's consumed.
     */
    private suspend fun settleHeld() {
        billing.purchases()?.forEach { settle(it) }
    }

    /**
     * Consumed first, then recorded, so a tip is counted once: a record that failed after its consume
     * would otherwise be counted again by the next sweep. A consume that fails leaves the purchase with
     * Play, for the next sweep to count. Neither stops for the screen closing, since the user has paid.
     */
    private suspend fun settle(purchase: StorePurchase): Outcome? =
        withContext(NonCancellable) {
            val tier = purchase.skus.firstNotNullOfOrNull(::tierForSku)
            when {
                // Not paid yet, so never consumed: that would close it unpaid.
                purchase.pending -> Outcome.PENDING

                // Not a tip. Nothing else is sold, but what isn't ours isn't consumed.
                tier == null -> null

                // Paid and still Play's: thanked for as pending, and counted on the next sweep.
                !billing.consume(purchase) -> Outcome.PENDING

                record(tier) -> null

                else -> Outcome.NOT_SAVED
            }
        }

    /**
     * Shown whether or not the write lands: the user really did pay, and "no tip" because a JSON write
     * failed would read as the payment lost. The result says whether it'll survive a restart.
     */
    private suspend fun record(tier: TipTier): Boolean =
        recording.withLock {
            val next = withTipRecorded(supporter, tier, now())
            supporter = next
            val saved = withContext(io) { file.write(next) }
            if (saved) onSaved()
            saved
        }
}
