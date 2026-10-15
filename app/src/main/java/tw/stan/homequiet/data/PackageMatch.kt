package tw.stan.homequiet.data

/**
 * Grok Bot (SpaceXAI) is [BOT]. v0.3.6 and earlier pinned a row labeled "Grok Bot"
 * onto [LEGACY], which is the separate xAI Grok app, so the selected set never
 * matched the notifications Stan actually receives.
 */
object PackageMatch {
    const val LEGACY = "ai.x.grok"
    const val BOT = "ai.x.grok.bot"

    fun isGrokBot(packageName: String, label: String? = null): Boolean {
        val pkg = packageName.lowercase()
        if (pkg == BOT || pkg.endsWith(".grok.bot") || pkg.contains("grok.bot") || pkg.contains("grokbot")) {
            return true
        }
        val title = label?.trim().orEmpty()
        if (title.isEmpty()) return false
        return title.equals("Grok Bot", ignoreCase = true) ||
            (title.contains("grok", ignoreCase = true) && title.contains("bot", ignoreCase = true))
    }

    fun blocks(posted: String, selected: Set<String>, aliasLegacy: Boolean, label: String? = null): Boolean {
        if (posted in selected) return true
        if (!aliasLegacy || !isGrokBot(posted, label)) return false
        return LEGACY in selected || selected.any { isGrokBot(it) }
    }

    /** Quiet mode on, and at least one home signal was valid before or after the refresh. */
    fun shouldClear(
        paused: Boolean,
        atHome: Boolean,
        wasHome: Boolean,
        postedPackage: String,
        selected: Set<String>,
        aliasLegacy: Boolean,
        postedLabel: String? = null,
    ): Boolean {
        if (paused || postedPackage.isBlank()) return false
        if (!atHome && !wasHome) return false
        return blocks(postedPackage, selected, aliasLegacy, postedLabel)
    }
}
