package tw.stan.homequiet.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale

object AppCatalog {
    const val GROK_LEGACY_PACKAGE = PackageMatch.LEGACY
    const val GROK_BOT_PACKAGE = PackageMatch.BOT
    /** Default pin when nothing is installed yet: the real Grok Bot id, not the xAI chat app. */
    const val GROK_PACKAGE = GROK_BOT_PACKAGE
    const val GROK_LABEL = "Grok Bot"

    private const val MIGRATIONS = "migrations"
    private const val KEY_LINKED = "grok_bot_linked"

    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val found = LinkedHashMap<String, String>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || found.containsKey(pkg)) continue
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
            found[pkg] = label
        }

        rememberInstalled(pm, found, GROK_BOT_PACKAGE)
        rememberInstalled(pm, found, GROK_LEGACY_PACKAGE)

        val pin = pinPackage(found.keys, pm)
        val pinInstalled = found.containsKey(pin) || isInstalled(pm, pin)
        val pinSystemLabel = found[pin]

        val collator = Collator.getInstance(Locale.TAIWAN)
        val items = ArrayList<AppInfo>(found.size + 1)
        items += AppInfo(
            packageName = pin,
            label = GROK_LABEL,
            subtitle = when {
                pinInstalled && !pinSystemLabel.isNullOrBlank() && pinSystemLabel != GROK_LABEL ->
                    "$pinSystemLabel · $pin"
                pinInstalled -> pin
                else -> "尚未安裝"
            },
            installed = pinInstalled,
            pinned = true,
        )
        found.forEach { (pkg, label) ->
            if (pkg == pin) return@forEach
            items += AppInfo(
                packageName = pkg,
                label = label,
                subtitle = pkg,
                installed = true,
                pinned = false,
            )
        }
        items.sortWith { a, b ->
            if (a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                collator.compare(a.label, b.label)
            }
        }
        return items
    }

    fun labelOf(context: Context, packageName: String): String {
        if (PackageMatch.isGrokBot(packageName)) return GROK_LABEL
        return applicationLabel(context.packageManager, packageName) ?: packageName
    }

    fun pinPackage(context: Context): String = pinPackage(launcherPackages(context), context.packageManager)

    /** True until the real Grok Bot id has been written into the selected set (or the user chose). */
    fun aliasLegacyGrok(context: Context): Boolean {
        return !context.getSharedPreferences(MIGRATIONS, Context.MODE_PRIVATE).getBoolean(KEY_LINKED, false)
    }

    fun markGrokLinked(context: Context) {
        context.getSharedPreferences(MIGRATIONS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LINKED, true).commit()
    }

    /**
     * If the saved selection is the old "Grok Bot" pin (ai.x.grok) and the real bot is installed,
     * select ai.x.grok.bot. Drop the legacy id when that app is not installed so it doesn't linger.
     */
    fun linkGrokBot(context: Context) {
        if (!aliasLegacyGrok(context)) return
        val pm = context.packageManager
        val launchers = launcherPackages(context)
        val botInstalled = GROK_BOT_PACKAGE in launchers || isInstalled(pm, GROK_BOT_PACKAGE)
        if (!botInstalled) return
        val store = QuietStore.get(context)
        val selected = store.snapshot.value.selectedPackages
        val legacyInstalled = GROK_LEGACY_PACKAGE in launchers || isInstalled(pm, GROK_LEGACY_PACKAGE)
        if (GROK_LEGACY_PACKAGE in selected || selected.any { PackageMatch.isGrokBot(it) }) {
            val next = selected.toMutableSet()
            next.add(GROK_BOT_PACKAGE)
            if (!legacyInstalled) next.remove(GROK_LEGACY_PACKAGE)
            store.setSelected(next)
        }
        markGrokLinked(context)
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return isInstalled(context.packageManager, packageName) || packageName in launcherPackages(context)
    }

    private fun pinPackage(found: Set<String>, pm: PackageManager): String {
        return when {
            GROK_BOT_PACKAGE in found || isInstalled(pm, GROK_BOT_PACKAGE) -> GROK_BOT_PACKAGE
            GROK_LEGACY_PACKAGE in found || isInstalled(pm, GROK_LEGACY_PACKAGE) -> GROK_LEGACY_PACKAGE
            else -> GROK_BOT_PACKAGE
        }
    }

    private fun rememberInstalled(pm: PackageManager, found: MutableMap<String, String>, packageName: String) {
        if (found.containsKey(packageName) || !isInstalled(pm, packageName)) return
        found[packageName] = applicationLabel(pm, packageName) ?: packageName
    }

    private fun launcherPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        return resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
    }

    private fun applicationLabel(pm: PackageManager, packageName: String): String? {
        return runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrNull()
    }
}
