package com.casco.kettle.domain

/**
 * The tip jar: `src/domain/tip.ts`, which holds the reasoning. Ordered smallest to largest, and this
 * order *is* the display order, never the fetched price's.
 *
 * [sku] is the Play Console product ID. Those are permanent once created, so treat them as fixed.
 */
enum class TipTier(
    val sku: String,
) {
    SMALL("tip_small"),
    MEDIUM("tip_medium"),
    LARGE("tip_large"),
}

val TIP_SKU_LIST: List<String> = TipTier.entries.map { it.sku }

fun tierForSku(sku: String): TipTier? = TipTier.entries.firstOrNull { it.sku == sku }

/** The part of a store product the screen needs, so the domain doesn't depend on Play Billing's types. */
data class TipProduct(
    val id: String,
    val displayPrice: String,
)

/** A tier ready to render: the store's own localized price string, never formatted by hand. */
data class TipTierOffer(
    val tier: TipTier,
    val sku: String,
    val displayPrice: String,
)

/**
 * The fetched products as tiers, in tier order. Unknown SKUs are dropped and missing ones don't
 * appear, so a partly configured Play Console costs a button rather than the screen.
 */
fun toTipTierOffers(products: List<TipProduct>): List<TipTierOffer> =
    TipTier.entries.mapNotNull { tier ->
        products.firstOrNull { it.id == tier.sku }?.let { TipTierOffer(tier, tier.sku, it.displayPrice) }
    }

/**
 * `supporter.json`, app-owned and written only by the app: native's Support from M4 on, while RN
 * Settings reads it until M5. Its format is frozen until then.
 */
data class SupporterState(
    /** Tips are consumable, so this counts repeat tips rather than being a boolean. */
    val tipCount: Long,
    /** `Date.toISOString`'s shape. Null only in the empty state. */
    val lastTipAt: String?,
    val lastTier: TipTier?,
) {
    val hasTipped: Boolean get() = tipCount > 0

    companion object {
        val EMPTY = SupporterState(0, null, null)
    }
}

/** A tip recorded at [at], epoch milliseconds: a parameter, so the domain reads no clock. */
fun withTipRecorded(
    current: SupporterState,
    tier: TipTier,
    at: Long,
): SupporterState = SupporterState(current.tipCount + 1, isoInstant(at), tier)

/**
 * What a `supporter.json` says, held to `loadSupporterState` by `conformance/supporter.yaml`. Anything
 * the schema refuses reads as the empty state, never an error: the file is the app's, so there's no
 * user to report to, and being wrong forgets a past tip rather than any workout data.
 *
 * Read with the YAML loader, as `parsePreferences` is: RN wrote it with `JSON.stringify`, and that
 * output is YAML 1.2. All three keys are required, null or not, as zod's `nullable` requires them.
 */
fun parseSupporterState(text: String): SupporterState {
    val fields = (loadYaml(text) as? YamlLoad.Loaded)?.value as? Map<*, *>
    return fields?.let(::supporterStateOf) ?: SupporterState.EMPTY
}

private fun supporterStateOf(fields: Map<*, *>): SupporterState? {
    val count = (fields[COUNT] as? Number)?.toDouble()
    val at = fields[AT]
    val tier = fields[TIER]
    val valid =
        fields.keys.containsAll(listOf(COUNT, AT, TIER)) &&
            count != null &&
            isSafeWhole(count) &&
            count >= 0 &&
            (at == null || (at is String && at.isNotEmpty())) &&
            (tier == null || TIERS.containsKey(tier))
    return if (valid) SupporterState(count!!.toLong(), at as String?, TIERS[tier]) else null
}

/** The file as RN's `JSON.stringify` wrote it, key for key, so either side reads what the other wrote. */
fun serializeSupporterState(state: SupporterState): String {
    val at = state.lastTipAt?.let(::jsonString) ?: "null"
    val tier = state.lastTier?.let { jsonString(it.name.lowercase()) } ?: "null"
    return """{"$COUNT":${state.tipCount},"$AT":$at,"$TIER":$tier}"""
}

private fun jsonString(value: String): String =
    buildString {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char == '\b' -> append("\\b")
                char == '\u000C' -> append("\\f")
                char < ' ' -> append("\\u").append(char.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
                else -> append(char)
            }
        }
        append('"')
    }

/**
 * `Date.toISOString` for [epochMillis]: UTC, always with milliseconds. By days from the civil
 * calendar, since `:shared`'s common code has no date library and this is its one timestamp. Its
 * numbers are the algorithm's own (days per 4, 100 and 400 years, March-based month lengths), which
 * read worse given names than set beside the published algorithm they come from.
 */
@Suppress("MagicNumber")
internal fun isoInstant(epochMillis: Long): String {
    val days = epochMillis.floorDiv(MILLIS_PER_DAY)
    val millisOfDay = epochMillis.mod(MILLIS_PER_DAY)
    // Howard Hinnant's civil_from_days, with eras of 400 years counted from 0000-03-01.
    val shifted = days + DAYS_TO_ERA_START
    val era = shifted.floorDiv(DAYS_PER_ERA)
    val dayOfEra = shifted - era * DAYS_PER_ERA
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val marchMonth = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * marchMonth + 2) / 5 + 1
    val month = if (marchMonth < 10) marchMonth + 3 else marchMonth - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0

    fun pad(
        value: Long,
        width: Int = 2,
    ) = value.toString().padStart(width, '0')
    val seconds = millisOfDay / 1000
    return "${pad(year, 4)}-${pad(month)}-${pad(day)}T${pad(seconds / 3600)}:${pad(seconds / 60 % 60)}:" +
        "${pad(seconds % 60)}.${pad(millisOfDay % 1000, 3)}Z"
}

private const val COUNT = "tipCount"
private const val AT = "lastTipAt"
private const val TIER = "lastTier"
private const val HEX = 16
private const val UNICODE_DIGITS = 4
private const val MILLIS_PER_DAY = 86_400_000L
private const val DAYS_TO_ERA_START = 719_468L
private const val DAYS_PER_ERA = 146_097L

private val TIERS = TipTier.entries.associateBy { it.name.lowercase() }
