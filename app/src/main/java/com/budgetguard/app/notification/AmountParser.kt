package com.budgetguard.app.notification

/**
 * Extracts a spend amount (in whole yen) from a notification's title/text, or returns null if
 * the notification doesn't look like a purchase (e.g. a login alert, a points campaign push).
 *
 * This is the main extension point for accuracy: notification wording differs a lot between
 * apps ("ご利用のお知らせ" style for card apps, "お支払いが完了しました" for PayPay, etc). Register
 * an app-specific implementation in [AmountParserRegistry] once you've looked at real samples in
 * the in-app 通知ログ screen (backed by NotificationLogEntity) for that package.
 */
fun interface AmountParser {
    fun parse(title: String?, text: String?): Long?
}

/**
 * Generic fallback parser: looks for a yen amount written as "¥1,234" / "￥1,234" / "1,234円",
 * and skips notifications containing obvious non-purchase keywords (points campaigns, login
 * alerts, etc). This will not be perfectly accurate for every app -- it's a reasonable starting
 * point meant to be refined per-app using real notification samples.
 */
object DefaultAmountParser : AmountParser {

    private val YEN_PATTERN = Regex(
        """[¥￥]\s?([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)|([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)\s?円"""
    )

    /** Notifications containing any of these are very unlikely to be an actual charge. */
    private val IGNORE_KEYWORDS = listOf(
        "ポイント付与", "キャンペーン", "抽選", "クーポン", "ログイン", "お知らせ配信",
        "アプリを更新", "メンテナンス",
    )

    override fun parse(title: String?, text: String?): Long? {
        val combined = listOfNotNull(title, text).joinToString(" ")
        if (combined.isBlank()) return null
        if (IGNORE_KEYWORDS.any { combined.contains(it) }) return null

        val match = YEN_PATTERN.find(combined) ?: return null
        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return raw.replace(",", "").toLongOrNull()?.takeIf { it > 0 }
    }
}

/**
 * Maps a package name to a custom [AmountParser]. Falls back to [DefaultAmountParser] for any
 * app without a specific rule registered. Add entries here as you tune parsing for real apps,
 * e.g.:
 *
 * ```
 * register("jp.ne.paypay.android.app", AmountParser { title, text ->
 *     Regex("""([0-9,]+)円""").find(text.orEmpty())?.groupValues?.get(1)
 *         ?.replace(",", "")?.toLongOrNull()
 * })
 * ```
 */
class AmountParserRegistry {

    private val perAppParsers = mutableMapOf<String, AmountParser>()

    fun register(packageName: String, parser: AmountParser) {
        perAppParsers[packageName] = parser
    }

    fun parserFor(packageName: String): AmountParser =
        perAppParsers[packageName] ?: DefaultAmountParser
}
