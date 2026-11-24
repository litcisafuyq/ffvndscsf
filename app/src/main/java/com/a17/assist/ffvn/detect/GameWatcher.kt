package com.a17.assist.ffvn.detect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * GameWatcher:
 * Watches foreground package via UsageStatsManager (and A17AccessibilityService window events).
 * - Target packages: com.garena.ffvn (primary), com.dts.freefireth (secondary)
 * - When player exits game for >= 3000ms, sleeps engine and stops all injection.
 */
class GameWatcher(
    private val context: Context,
    private val onForegroundChanged: (Boolean) -> Unit
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    private val _currentPackage = MutableStateFlow<String?>(null)
    val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()

    private val _isGameForeground = MutableStateFlow(false)
    val isGameForeground: StateFlow<Boolean> = _isGameForeground.asStateFlow()

    private var lastGameSeenUptimeMs: Long = 0L
    private var lastKnownForegroundPkg: String? = null

    fun start(exitSleepMs: Long = 3000L) {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                val nowWall = System.currentTimeMillis()
                val nowUp = SystemClock.uptimeMillis()

                val detected = pollForegroundPackage(nowWall)

                if (!detected.isNullOrBlank()) {
                    lastKnownForegroundPkg = detected
                    _currentPackage.value = detected
                }

                val isTarget = lastKnownForegroundPkg in TARGET_PACKAGES
                if (isTarget) {
                    lastGameSeenUptimeMs = nowUp
                    if (!_isGameForeground.value) {
                        _isGameForeground.value = true
                        onForegroundChanged(true)
                    }
                } else {
                    if (_isGameForeground.value &&
                        (nowUp - lastGameSeenUptimeMs) >= exitSleepMs
                    ) {
                        _isGameForeground.value = false
                        onForegroundChanged(false)
                    }
                }

                delay(500L)
            }
        }
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        if (_isGameForeground.value) {
            _isGameForeground.value = false
            onForegroundChanged(false)
        }
    }

    fun notifyGameLaunched() {
        lastGameSeenUptimeMs = SystemClock.uptimeMillis()
    }

    private fun pollForegroundPackage(nowWallMs: Long): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = usageStatsManager ?: return null
        return runCatching {
            val events = usm.queryEvents(nowWallMs - 5000L, nowWallMs)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent =
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        @Suppress("DEPRECATION")
                        (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
                if (isForegroundEvent && event.timeStamp >= latestTime) {
                    latestTime = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            latestPkg
        }.getOrNull()
    }

    companion object {
        const val PKG_FFVN = "com.garena.ffvn"
        const val PKG_FFTH = "com.dts.freefireth"
        val TARGET_PACKAGES = setOf(PKG_FFVN, PKG_FFTH)

        fun hasUsageStatsPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun resolveInstalledPackage(context: Context): String? {
            val pm = context.packageManager
            if (isPackageInstalled(pm, PKG_FFVN)) return PKG_FFVN
            if (isPackageInstalled(pm, PKG_FFTH)) return PKG_FFTH
            return null
        }

        private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }

        fun launchFreeFireOrStore(context: Context): Boolean {
            val pm = context.packageManager
            val installedPkg = resolveInstalledPackage(context)
            if (installedPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(installedPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }

            // Not installed -> open Play Store market:// with https:// fallback
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$PKG_FFVN")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openedMarket = runCatching {
                context.startActivity(marketIntent)
                true
            }.getOrDefault(false)

            if (!openedMarket) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PKG_FFVN")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(webIntent) }
            }
            return false
        }
    }
}
