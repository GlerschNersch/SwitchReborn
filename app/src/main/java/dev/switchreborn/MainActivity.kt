package dev.switchreborn

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import dev.switchreborn.home.HomeView
import dev.switchreborn.home.Launch
import dev.switchreborn.home.Metrics
import dev.switchreborn.home.Option
import dev.switchreborn.home.Palette
import dev.switchreborn.home.Sfx
import dev.switchreborn.home.Sources
import dev.switchreborn.home.Tile
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var view: HomeView
    private lateinit var sources: Sources
    private lateinit var sfx: Sfx
    private val loader = Executors.newSingleThreadExecutor()
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }

    private val appsChanged = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0) view.battery = level * 100 / scale
            view.charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            view.invalidate()
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = setWifi()
        override fun onLost(network: Network) = setWifi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sources = Sources(this)
        sfx = Sfx(this)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view = HomeView(this, if (dark) Palette.DARK else Palette.LIGHT, sfx, ::launch, ::optionsFor, sources::renderArt)
        view.userName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: "User"
        view.userLaunch = Launch.Action(Intent(ACTION_USER_SETTINGS))
        setContentView(view)
        // The clock and battery are drawn by HOME itself, so the status bar stays hidden until swiped.
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view.requestFocus()
        // targetSdk 36 uses predictive back, which never calls onBackPressed.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { view.back() }
        launcherApps.registerCallback(appsChanged)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        connectivity.registerDefaultNetworkCallback(wifiCallback)
        reload()
    }

    override fun onResume() {
        super.onResume()
        view.resume()
    }

    override fun onPause() {
        view.pause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action && hasWindowFocus()) view.home()
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(appsChanged)
        unregisterReceiver(batteryReceiver)
        connectivity.unregisterNetworkCallback(wifiCallback)
        loader.shutdownNow()
        sfx.release()
        super.onDestroy()
    }

    private fun setWifi() {
        val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        // The console only has Wi-Fi; on a phone the glyph stands for "online" over any transport.
        val on = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        view.post { view.wifi = on; view.invalidate() }
    }

    private fun reload() {
        val artPx = (Metrics.TILE * resources.displayMetrics.run { minOf(widthPixels, heightPixels) } / Metrics.MIN_W).toInt()
        loader.execute {
            val apps = runCatching { sources.loadApps() }
                .onFailure { Log.e(TAG, "Loading apps failed", it) }
                .getOrNull() ?: return@execute
            val row = sources.buildRow(apps, artPx)
            val buttons = sources.buttons()
            view.post { view.setContent(row, apps, buttons) }
        }
    }

    private fun launch(launch: Launch, tile: Tile?) {
        try {
            when (launch) {
                is Launch.App -> {
                    launcherApps.startMainActivity(launch.component, launch.user, null, null)
                    tile?.let { sources.markOpened(it.id); reloadRowSoon() }
                }
                is Launch.Action -> startActivity(Intent(launch.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Launch.Notifications -> expandNotifications()
                Launch.Power -> view.showOptions("Power Options", powerOptions())
                Launch.AllSoftware -> Unit
            }
        } catch (e: ActivityNotFoundException) {
            if (launch is Launch.Action && launch.intent.action == ACTION_USER_SETTINGS) {
                startActivity(Intent(Settings.ACTION_SETTINGS)); return
            }
            Toast.makeText(this, "Nothing on this phone can open that", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Can't open ${tile?.title ?: "that"}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Recents reorder the row, but only once the launched app has covered us. */
    private fun reloadRowSoon() = view.postDelayed({ reload() }, 600)

    private fun optionsFor(tile: Tile): List<Option> {
        val pkg = tile.packageName ?: return emptyList()
        return listOf(
            Option("Software Information") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            },
            Option("Delete Software") {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
            },
        )
    }

    private fun powerOptions(): List<Option> = listOf(
        Option("Power Saving Settings") { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
        Option("Choose HOME App") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
        Option("Display Settings") { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
    )

    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        // StatusBarManager.expandNotificationsPanel is not public API; launchers have long called
        // it reflectively under EXPAND_STATUS_BAR. Fall back quietly if the platform refuses.
        runCatching {
            val sbm = getSystemService("statusbar")
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        }.onFailure {
            Log.w(TAG, "Couldn't open the notification shade", it)
            Toast.makeText(this, "Swipe down from the top for notifications", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "SwitchReborn"
        const val ACTION_USER_SETTINGS = "android.settings.USER_SETTINGS"
    }
}
