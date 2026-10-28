package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `src/domain/__tests__/tip.test.ts`, case for case. `tipPurchaseRequest` and `tipStoreCopyKeys` stay
 * behind: one is expo-iap's request shape and the other picks the App Store's copy, and neither has an
 * Android caller. What the file reads as is the conformance corpus's (`conformance/supporter.yaml`).
 */
class TipTest {
    @Nested
    inner class ToTipTierOffers {
        private fun product(
            id: String,
            displayPrice: String,
        ) = TipProduct(id, displayPrice)

        /** The store returns products in whatever order it likes, so ordering is the tiers' own. */
        @Test
        fun `orders tiers smallest to largest regardless of the order products arrive in`() {
            val offers =
                toTipTierOffers(
                    listOf(
                        product(TipTier.LARGE.sku, "R$ 29,90"),
                        product(TipTier.SMALL.sku, "R$ 4,90"),
                        product(TipTier.MEDIUM.sku, "R$ 14,90"),
                    ),
                )

            assertEquals(listOf(TipTier.SMALL, TipTier.MEDIUM, TipTier.LARGE), offers.map { it.tier })
        }

        @Test
        fun `carries the store's own localized price string through untouched`() {
            val offers = toTipTierOffers(listOf(product(TipTier.SMALL.sku, "R$ 4,90")))

            assertEquals(listOf(TipTierOffer(TipTier.SMALL, "R$ 4,90")), offers)
        }

        // A half-configured Play Console should cost the user a button, not the whole screen.
        @Test
        fun `drops unknown SKUs and omits tiers the store did not return`() {
            val offers =
                toTipTierOffers(listOf(product("some_other_product", "$1"), product(TipTier.MEDIUM.sku, "R$ 14,90")))

            assertEquals(listOf(TipTier.MEDIUM), offers.map { it.tier })
        }

        @Test
        fun `returns nothing when the store returned nothing`() {
            assertEquals(emptyList<TipTierOffer>(), toTipTierOffers(emptyList()))
        }
    }

    @Nested
    inner class TierForSku {
        @Test
        fun `maps every published SKU back to its tier`() {
            assertEquals(listOf("tip_small", "tip_medium", "tip_large"), TIP_SKU_LIST)
            assertEquals(TipTier.entries, TIP_SKU_LIST.map(::tierForSku))
        }

        @Test
        fun `returns null for a SKU the app does not sell`() {
            assertNull(tierForSku("tip_enormous"))
        }
    }

    @Nested
    inner class WithTipRecorded {
        @Test
        fun `counts repeat tips rather than flipping a boolean`() {
            // 2026-07-28T10:00Z, then 2026-08-01T10:00Z.
            val first = withTipRecorded(SupporterState.EMPTY, TipTier.SMALL, at = 1_785_232_800_000)
            val second = withTipRecorded(first, TipTier.LARGE, at = 1_785_578_400_000)

            assertEquals(2L, second.tipCount)
            assertEquals(TipTier.LARGE, second.lastTier)
            assertEquals("2026-08-01T10:00:00.000Z", second.lastTipAt)
        }

        @Test
        fun `does not change the state it was given`() {
            withTipRecorded(SupporterState.EMPTY, TipTier.SMALL, at = 0)

            assertEquals(SupporterState(0, null, null), SupporterState.EMPTY)
        }

        /** `Date.toISOString`'s shape: always the milliseconds, always UTC, and a leap day as itself. */
        @Test
        fun `writes the time as Date toISOString does`() {
            assertEquals("1970-01-01T00:00:00.000Z", withTipRecorded(SupporterState.EMPTY, TipTier.SMALL, 0).lastTipAt)
            assertEquals(
                "2028-02-29T23:59:59.999Z",
                withTipRecorded(SupporterState.EMPTY, TipTier.SMALL, 1_835_481_599_999).lastTipAt,
            )
        }
    }

    @Test
    fun `hasTipped is false for the empty state and true once a tip lands`() {
        assertFalse(SupporterState.EMPTY.hasTipped)
        assertTrue(withTipRecorded(SupporterState.EMPTY, TipTier.MEDIUM, at = 0).hasTipped)
    }

    @Nested
    inner class SupporterFile {
        @Test
        fun `accepts the empty state and a recorded tip`() {
            assertEquals(
                SupporterState.EMPTY,
                parseSupporterState("""{"tipCount":0,"lastTipAt":null,"lastTier":null}"""),
            )
            assertEquals(
                SupporterState(3, "2026-07-28T10:00:00.000Z", TipTier.LARGE),
                parseSupporterState("""{"tipCount":3,"lastTipAt":"2026-07-28T10:00:00.000Z","lastTier":"large"}"""),
            )
        }

        /** App-written, so a refusal means corruption, and it reads as no tips rather than throwing. */
        @Test
        fun `reads corrupt state as the empty state`() {
            val recorded = SupporterState(1, "2026-07-28T10:00:00.000Z", TipTier.SMALL)
            for (text in listOf(
                "{}",
                """{"tipCount":-1,"lastTipAt":null,"lastTier":null}""",
                """{"tipCount":1.5,"lastTipAt":null,"lastTier":null}""",
                """{"tipCount":1,"lastTipAt":null,"lastTier":"huge"}""",
                "\"not an object\"",
            )) {
                assertEquals(SupporterState.EMPTY, parseSupporterState(text), text)
            }
            // The control: the same reader keeps a good one.
            assertEquals(recorded, parseSupporterState(serializeSupporterState(recorded)))
        }

        /** RN reads it with `JSON.parse`, and wrote it with `JSON.stringify`: the same text, key for key. */
        @Test
        fun `writes the file as JSON stringify does`() {
            assertEquals(
                """{"tipCount":2,"lastTipAt":"2026-08-01T10:00:00.000Z","lastTier":"medium"}""",
                serializeSupporterState(SupporterState(2, "2026-08-01T10:00:00.000Z", TipTier.MEDIUM)),
            )
            assertEquals(
                """{"tipCount":0,"lastTipAt":null,"lastTier":null}""",
                serializeSupporterState(SupporterState.EMPTY),
            )
        }

        @Test
        fun `escapes a string JSON can't carry bare`() {
            val odd = SupporterState(1, "a\"b\\c\nd\u0001", TipTier.SMALL)

            assertEquals(
                """{"tipCount":1,"lastTipAt":"a\"b\\c\nd\u0001","lastTier":"small"}""",
                serializeSupporterState(odd),
            )
            assertEquals(odd, parseSupporterState(serializeSupporterState(odd)))
        }
    }
}
