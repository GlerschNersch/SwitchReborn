package dev.switchreborn.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.provider.Settings
import java.text.Collator

/** What opening something does. */
sealed interface Launch {
    data class App(val component: ComponentName, val user: UserHandle) : Launch
    data class Action(val intent: Intent) : Launch
    /** Opens the launcher's own All Software grid. */
    data object AllSoftware : Launch
    /** Pulls down the notification shade. */
    data object Notifications : Launch
    /** Shows the power options popup. */
    data object Power : Launch
}

/** A software tile. [art] is a full-bleed square, the way console icons fill their tile. */
class Tile(
    val id: String,
    val title: String,
    val launch: Launch,
    var art: Bitmap? = null,
    val packageName: String? = null,
    val info: LauncherActivityInfo? = null,
)

class RoundButton(val label: String, val glyph: Glyph, val launch: Launch, val solid: Boolean = false)

class Sources(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val pm = context.packageManager

    /** Every launchable app, A-Z, without art (see [renderArt]). */
    fun loadApps(): List<Tile> {
        val self = context.packageName
        val collator = Collator.getInstance()
        val out = ArrayList<Tile>()
        for (user in launcherApps.profiles) {
            for (info in launcherApps.getActivityList(null, user)) {
                val component = info.componentName
                if (component.packageName == self) continue
                val id = "app:${component.flattenToShortString()}#${user.hashCode()}"
                out.add(Tile(id, info.label.toString(), Launch.App(component, user), packageName = component.packageName, info = info))
            }
        }
        out.sortWith { a, b -> collator.compare(a.title, b.title) }
        return out
    }

    /** The HOME row: recently opened first, topped up A-Z, capped at the console's 12. */
    fun buildRow(apps: List<Tile>, artPx: Int): List<Tile> {
        val byId = apps.associateBy { it.id }
        val recent = recents().mapNotNull { byId[it] }
        val row = (recent + apps.filter { it !in recent }).take(Metrics.ROW_APPS)
        row.forEach { if (it.art == null) it.art = renderArt(it, artPx) }
        return row
    }

    fun recents(): List<String> = prefs.getString(KEY_RECENTS, "")!!.split('\n').filter { it.isNotEmpty() }

    fun markOpened(id: String) {
        val list = listOf(id) + recents().filter { it != id }
        prefs.edit().putString(KEY_RECENTS, list.take(Metrics.ROW_APPS).joinToString("\n")).apply()
    }

    /** The round buttons in firmware-11 order; Online only when there's an online hub to open. */
    fun buttons(): List<RoundButton> {
        val online = pm.getLaunchIntentForPackage(PLAY_GAMES)
        val store = pm.getLaunchIntentForPackage(PLAY_STORE)
        return listOfNotNull(
            online?.let { RoundButton("Online", Glyph.ONLINE, Launch.Action(it), solid = true) },
            RoundButton("News", Glyph.NEWS, Launch.Notifications),
            RoundButton("Shop", Glyph.SHOP, store?.let { Launch.Action(it) }
                ?: Launch.Action(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MARKET))),
            RoundButton("Album", Glyph.ALBUM, Launch.Action(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))),
            RoundButton("Controllers", Glyph.CONTROLLERS, Launch.Action(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))),
            RoundButton("System Settings", Glyph.SETTINGS, Launch.Action(Intent(Settings.ACTION_SETTINGS))),
            RoundButton("Sleep Mode", Glyph.POWER, Launch.Power),
        )
    }

    fun renderArt(t: Tile, px: Int): Bitmap? = t.info?.let { renderArt(it.getIcon(0), px) }

    private fun renderArt(icon: Drawable, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (icon is AdaptiveIconDrawable) {
            // Unmasked: the 108dp layers overflow the 72dp safe zone by 1/6 each side.
            val pad = px / 4
            icon.background?.apply { setBounds(-pad, -pad, px + pad, px + pad); draw(c) }
            icon.foreground?.apply { setBounds(-pad, -pad, px + pad, px + pad); draw(c) }
        } else {
            c.drawColor(0xFFF4F4F4.toInt())
            val inset = px / 5
            icon.setBounds(inset, inset, px - inset, px - inset)
            icon.draw(c)
        }
        return bmp
    }

    companion object {
        const val PLAY_STORE = "com.android.vending"
        const val PLAY_GAMES = "com.google.android.play.games"
        private const val KEY_RECENTS = "recents"
    }
}
