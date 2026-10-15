package tw.stan.homequiet.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale

object AppCatalog {
    const val GROK_PACKAGE = "ai.x.grok"
    const val GROK_LABEL = "Grok Bot"

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

        val grokInstalled = isInstalled(pm, GROK_PACKAGE)
        if (!found.containsKey(GROK_PACKAGE) && grokInstalled) {
            found[GROK_PACKAGE] = applicationLabel(pm, GROK_PACKAGE) ?: GROK_LABEL
        }

        val collator = Collator.getInstance(Locale.TAIWAN)
        val items = ArrayList<AppInfo>(found.size + 1)
        val grokSystemLabel = found[GROK_PACKAGE]
        items += AppInfo(
            packageName = GROK_PACKAGE,
            label = GROK_LABEL,
            subtitle = when {
                grokInstalled && !grokSystemLabel.isNullOrBlank() && grokSystemLabel != GROK_LABEL ->
                    "$grokSystemLabel · $GROK_PACKAGE"
                grokInstalled -> GROK_PACKAGE
                else -> "尚未安裝"
            },
            installed = grokInstalled || found.containsKey(GROK_PACKAGE),
            pinned = true,
        )
        found.forEach { (pkg, label) ->
            if (pkg == GROK_PACKAGE) return@forEach
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
        if (packageName == GROK_PACKAGE) return GROK_LABEL
        return applicationLabel(context.packageManager, packageName) ?: packageName
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
