package com.lordcb.homelauncher.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

class AppsRepository(private val context: Context) {

    /** All launchable apps. TV (leanback) apps take priority over phone-style entries. */
    suspend fun load(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = LinkedHashMap<String, LauncherApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                val banner = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm)
                result[pkg] = LauncherApp(
                    packageName = pkg,
                    label = ri.loadLabel(pm)?.toString() ?: pkg,
                    banner = banner?.render(320, 180),
                    icon = info.loadIcon(pm)?.render(160, 160),
                )
            }
        }
        result.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(packageName: String): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun Drawable.render(width: Int, height: Int): ImageBitmap? =
        try { toBitmap(width, height).asImageBitmap() } catch (_: Exception) { null }
}
