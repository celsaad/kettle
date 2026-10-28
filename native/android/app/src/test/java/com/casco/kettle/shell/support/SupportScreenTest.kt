package com.casco.kettle.shell.support

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.SupporterState
import com.casco.kettle.domain.TipProduct
import com.casco.kettle.domain.TipTier
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeSupporterFile(
    var state: SupporterState = SupporterState.EMPTY,
) : SupporterFile {
    val written = mutableListOf<SupporterState>()

    /** A full disk or a revoked folder. */
    var failing = false

    override fun read() = state

    override fun write(state: SupporterState): Boolean {
        if (failing) return false
        written += state
        this.state = state
        return true
    }
}

/** The store, held to what Play does: what it holds stays held until consumed. */
private class FakeBilling(
    var available: Boolean = true,
    var products: List<TipProduct>? = listOf(TipProduct("tip_small", "R$ 4,90"), TipProduct("tip_medium", "R$ 14,90")),
    vararg held: StorePurchase,
) : TipBilling {
    val held = held.toMutableList()
    val launched = mutableListOf<String>()
    val consumed = mutableListOf<String>()
    private var onUpdate: (PurchaseUpdate) -> Unit = {}

    /** The store's sheet closing, as Play reports it to the listener. */
    fun finish(update: PurchaseUpdate) {
        if (update is PurchaseUpdate.Bought) held += update.purchases
        onUpdate(update)
    }

    override suspend fun connect(onUpdate: (PurchaseUpdate) -> Unit): Boolean {
        this.onUpdate = onUpdate
        return available
    }

    override suspend fun products() = products

    override suspend fun purchases() = held.toList()

    override suspend fun consume(purchase: StorePurchase): Boolean {
        consumed += purchase.token
        held.remove(purchase)
        return true
    }

    override fun launch(sku: String) {
        launched += sku
    }

    override fun close() = Unit
}

private fun paid(
    sku: String,
    token: String,
) = StorePurchase(listOf(sku), pending = false, token = token)

private fun unpaid(
    sku: String,
    token: String,
) = StorePurchase(listOf(sku), pending = true, token = token)

/**
 * Support on Play Billing (#198). `support.test.tsx` had one case, the App Store's naming on iOS, which
 * Android doesn't have; these are the Android cases the port asked for instead.
 *
 * The harness recipe (#193): the class runs in `pt`, since an English assertion passes a hardcoded
 * English string, and the screen takes its boundaries as parameters, so the fakes record what was
 * written and consumed rather than what reached the disk or Play.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class SupportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val unavailable = "Não foi possível conectar à Play Store. Verifique sua conexão e tente de novo."
    private var saved = 0

    private fun show(
        file: SupporterFile = FakeSupporterFile(),
        billing: TipBilling = FakeBilling(),
    ) = compose.setContent {
        KettleTheme(darkTheme = false) {
            // 2026-10-27T12:00Z, and the file written inline, so a write has landed by the next assertion.
            SupportScreen(
                file,
                billing,
                onSaved = { saved++ },
                now = { 1_793_102_400_000 },
                io = Dispatchers.Unconfined,
            )
        }
    }

    private fun finish(
        billing: FakeBilling,
        update: PurchaseUpdate,
    ) {
        compose.runOnUiThread { billing.finish(update) }
        compose.waitForIdle()
    }

    @Test
    fun `the tiers the store sells are listed in tier order with its own prices`() {
        show(
            billing =
                FakeBilling(
                    products = listOf(TipProduct("tip_medium", "R$ 14,90"), TipProduct("tip_small", "R$ 4,90")),
                ),
        )

        compose
            .onNodeWithText(
                "Uma gorjeta ajuda a cobrir a taxa de desenvolvedor do Google Play. " +
                    "Ela não desbloqueia nada: todos os recursos já são seus.",
            ).assertExists()
        val small =
            compose
                .onNodeWithText("Gorjeta pequena")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val medium =
            compose
                .onNodeWithText("Gorjeta média")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assert(small < medium) { "small at $small should sit above medium at $medium" }
        compose.onNodeWithText("R$ 4,90").assertExists()
        compose.onNodeWithText("Gorjeta grande").assertDoesNotExist()
        compose.onNodeWithText("Carregando…").assertDoesNotExist()
    }

    /** The a11y props are asserted here: TalkBack is checked once, at the end of the migration. */
    @Test
    fun `a tier is one 48dp button that names its tier and price`() {
        show()

        compose
            .onNode(hasClickAction() and hasText("Gorjeta pequena"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText("R$ 4,90"))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a tip is consumed, recorded, written and thanked for`() {
        val file = FakeSupporterFile()
        val billing = FakeBilling()
        show(file, billing)

        compose.onNodeWithText("Gorjeta pequena").performClick()
        assertEquals(listOf("tip_small"), billing.launched)
        compose.onNode(hasClickAction() and hasText("Gorjeta média")).assertIsNotEnabled()
        finish(billing, PurchaseUpdate.Bought(listOf(paid("tip_small", "t1"))))

        assertEquals(listOf("t1"), billing.consumed)
        assertEquals(listOf(SupporterState(1, "2026-10-27T12:00:00.000Z", TipTier.SMALL)), file.written)
        assertEquals(1, saved)
        compose.onNodeWithText("Obrigado!").performScrollTo().assertExists()
        compose.onNodeWithText("Você deixou uma gorjeta. Isso ajuda de verdade.").assertExists()
        compose.onNode(hasClickAction() and hasText("Gorjeta média")).assertIsEnabled()
    }

    @Test
    fun `a tip that can't be written still shows, and says it won't last`() {
        val file = FakeSupporterFile().apply { failing = true }
        val billing = FakeBilling()
        show(file, billing)

        compose.onNodeWithText("Gorjeta média").performClick()
        finish(billing, PurchaseUpdate.Bought(listOf(paid("tip_medium", "t1"))))

        assertEquals(0, saved)
        compose.onNodeWithText("Você deixou uma gorjeta. Isso ajuda de verdade.").assertExists()
        compose
            .onNodeWithText("Sua gorjeta foi concluída, mas não foi possível salvá-la neste aparelho.")
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `without the store it says so, and a past tip is still thanked for`() {
        show(
            FakeSupporterFile(SupporterState(2, "2026-07-28T10:00:00.000Z", TipTier.LARGE)),
            FakeBilling(available = false),
        )

        compose.onNodeWithText("Você deixou 2 gorjetas. Isso ajuda de verdade.").assertExists()
        compose.onNodeWithText(unavailable).performScrollTo().assertExists()
        compose.onNodeWithText("Gorjeta pequena").assertDoesNotExist()
        compose.onNodeWithText("Carregando…").assertDoesNotExist()
    }

    /** RN's screen spun forever here. A store that sells nothing is a state, with a way out. */
    @Test
    fun `an empty product list says so rather than loading forever, and can try again`() {
        val billing = FakeBilling(products = emptyList())
        show(billing = billing)

        compose.onNodeWithText(unavailable).assertExists()
        compose.onNodeWithText("Carregando…").assertDoesNotExist()

        billing.products = listOf(TipProduct("tip_large", "R$ 29,90"))
        compose
            .onNodeWithText("Tentar de novo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        compose.onNodeWithText("Gorjeta grande").assertExists()
        compose.onNodeWithText(unavailable).assertDoesNotExist()
    }

    /**
     * A tip that completed while Support was closed, an RN-era one included: Play refunds it after three
     * days unless it's consumed, and only a query on connect ever sees it. One still unpaid is left be.
     */
    @Test
    fun `a paid tip left unconsumed is settled on connect, and an unpaid one left alone`() {
        val file = FakeSupporterFile()
        val billing = FakeBilling(held = arrayOf(paid("tip_large", "old"), unpaid("tip_small", "slow")))
        show(file, billing)

        assertEquals(listOf("old"), billing.consumed)
        assertEquals(listOf(SupporterState(1, "2026-10-27T12:00:00.000Z", TipTier.LARGE)), file.written)
        assertEquals(1, saved)
        compose.onNodeWithText("Você deixou uma gorjeta. Isso ajuda de verdade.").assertExists()
    }

    @Test
    fun `a purchase still being paid is thanked for but not consumed or recorded`() {
        val file = FakeSupporterFile()
        val billing = FakeBilling()
        show(file, billing)

        compose.onNodeWithText("Gorjeta pequena").performClick()
        finish(billing, PurchaseUpdate.Bought(listOf(unpaid("tip_small", "slow"))))

        assertEquals(emptyList<String>(), billing.consumed)
        assertEquals(emptyList<SupporterState>(), file.written)
        compose.onNodeWithText("Seu pagamento ainda está sendo processado. Obrigado!").performScrollTo().assertExists()
        compose.onNodeWithText("Obrigado!").assertDoesNotExist()
    }

    @Test
    fun `backing out says nothing, and a failure says nothing was charged`() {
        val billing = FakeBilling()
        show(billing = billing)
        val failed = "A compra não foi concluída. Nada foi cobrado."

        compose.onNodeWithText("Gorjeta pequena").performClick()
        finish(billing, PurchaseUpdate.Cancelled)
        compose.onNodeWithText(failed).assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("Gorjeta média")).assertIsEnabled()

        compose.onNodeWithText("Gorjeta pequena").performClick()
        finish(billing, PurchaseUpdate.Failed)
        compose.onNodeWithText(failed).performScrollTo().assertExists()
    }

    /** Play refuses a tier it still holds unconsumed; settling what it holds is what frees it. */
    @Test
    fun `a tier the store still holds is settled when it refuses another`() {
        val file = FakeSupporterFile()
        val billing = FakeBilling()
        show(file, billing)

        compose.onNodeWithText("Gorjeta pequena").performClick()
        compose.runOnUiThread { billing.held += paid("tip_small", "stuck") }
        finish(billing, PurchaseUpdate.AlreadyOwned)

        assertEquals(listOf("stuck"), billing.consumed)
        assertEquals(1, file.written.size)
        compose.onNode(hasClickAction() and hasText("Gorjeta pequena")).assertIsEnabled()
    }
}
