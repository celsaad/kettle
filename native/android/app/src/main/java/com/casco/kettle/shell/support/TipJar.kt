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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

/** What a test changes about [TipJar]: its clock, where the file is written, and its patience. */
data class TipJarRuntime(
    val now: () -> Long = System::currentTimeMillis,
    val io: CoroutineDispatcher = Dispatchers.IO,
    /** How long any one store call may take before it counts as no answer. */
    val patienceMs: Long = STORE_PATIENCE_MS,
)

/**
 * Support's state and everything it does with the store and the file, which the screen only draws.
 * Everything runs on [scope]'s thread but the file, which is the runtime's `io`.
 */
class TipJar(
    private val file: SupporterFile,
    private val billing: TipBilling,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
    private val runtime: TipJarRuntime = TipJarRuntime(),
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

    /**
     * Tokens settled or settling. A flow's purchase and a sweep can both reach the same one, and the
     * second consume would fail, showing "still processing" for a tip already thanked.
     */
    private val settled = mutableSetOf<String>()

    /** Settles under way, and whether the screen has closed meanwhile: see [close]. */
    private var settling = 0
    private var closed = false

    fun open() {
        scope.launch {
            supporter = withContext(runtime.io) { file.read() }
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

    /**
     * Ends the connection, after any settle under way: ending it under a consume would drop the
     * callback, leaving that coroutine waiting for good and the tip unrecorded until the next open.
     */
    fun close() {
        closed = true
        if (settling == 0) billing.close()
    }

    private suspend fun connect() {
        store = StoreState.Connecting
        if (ask(runtime) { billing.connect(::onUpdate) } != true) {
            store = StoreState.Unavailable
            return
        }
        // A store that can't list what it holds won't sell either, so this doesn't wait twice.
        if (!settleHeld()) {
            store = StoreState.Unavailable
            return
        }
        val offers = ask(runtime) { billing.products() }?.let(::toTipTierOffers)
        store = if (offers.isNullOrEmpty()) StoreState.Unavailable else StoreState.Ready(offers)
    }

    private fun onUpdate(update: PurchaseUpdate) {
        purchasing = null
        when (update) {
            is PurchaseUpdate.Bought -> {
                scope.launch {
                    // `map` first, not `firstNotNullOfOrNull`: every purchase is settled, whichever shows.
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
    private suspend fun settleHeld(): Boolean {
        val held = ask(runtime) { billing.purchases() } ?: return false
        held.forEach { settle(it) }
        return true
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

                !settled.add(purchase.token) -> null

                else -> settlePaid(purchase, tier)
            }
        }

    private suspend fun settlePaid(
        purchase: StorePurchase,
        tier: TipTier,
    ): Outcome? {
        settling++
        try {
            return when {
                // Paid and still Play's: thanked for as pending, and counted on the next sweep.
                ask(runtime) { billing.consume(purchase) } != true -> {
                    settled.remove(purchase.token)
                    Outcome.PENDING
                }

                record(tier) -> {
                    null
                }

                else -> {
                    Outcome.NOT_SAVED
                }
            }
        } finally {
            settling--
            if (closed && settling == 0) billing.close()
        }
    }

    /**
     * Shown whether or not the write lands: the user really did pay, and "no tip" because a JSON write
     * failed would read as the payment lost. The result says whether it'll survive a restart.
     */
    private suspend fun record(tier: TipTier): Boolean =
        recording.withLock {
            val next = withTipRecorded(supporter, tier, runtime.now())
            supporter = next
            val saved = withContext(runtime.io) { file.write(next) }
            if (saved) onSaved()
            saved
        }
}

/**
 * A store call, or null when it doesn't answer in time. Play can leave a callback unanswered: an
 * emulator whose Play Store has no billing retries its reconnection inside a query, then cancels the
 * query without a result, and the screen spun on "Loading…" for good.
 */
private suspend fun <T> ask(
    runtime: TipJarRuntime,
    call: suspend () -> T,
): T? = withTimeoutOrNull(runtime.patienceMs) { call() }

/** Play's own calls answer in well under a second; a reconnection's retries take a few. */
internal const val STORE_PATIENCE_MS = 10_000L
