package com.a17.assist.ffvn.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.a17.assist.ffvn.data.ConfigRepository
import com.a17.assist.ffvn.data.DeviceCalibration
import com.a17.assist.ffvn.data.DisplayScaleMetrics
import com.a17.assist.ffvn.detect.FireGateDetector
import com.a17.assist.ffvn.detect.GameWatcher
import com.a17.assist.ffvn.engine.AimEngine
import com.a17.assist.ffvn.engine.EngineTelemetry
import com.a17.assist.ffvn.engine.HapticCtl
import com.a17.assist.ffvn.engine.RamGuard
import com.a17.assist.ffvn.engine.ThermalGuard
import com.a17.assist.ffvn.inject.A11yInjector
import com.a17.assist.ffvn.inject.Injector
import com.a17.assist.ffvn.inject.InjectorStatus
import com.a17.assist.ffvn.inject.ShizukuInjector
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shared runtime state observable by MainActivity & Fragments.
 */
object A17RuntimeBus {
    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    private val _injectorStatus = MutableStateFlow(InjectorStatus.NO_PERMISSION)
    val injectorStatus: StateFlow<InjectorStatus> = _injectorStatus.asStateFlow()

    private val _telemetry = MutableStateFlow(EngineTelemetry())
    val telemetry: StateFlow<EngineTelemetry> = _telemetry.asStateFlow()

    private val _gameForeground = MutableStateFlow(false)
    val gameForeground: StateFlow<Boolean> = _gameForeground.asStateFlow()

    @Volatile
    var activeService: OverlayService? = null

    fun setOverlayRunning(running: Boolean) {
        _overlayRunning.value = running
    }

    fun setInjectorStatus(status: InjectorStatus) {
        _injectorStatus.value = status
    }

    fun setTelemetry(t: EngineTelemetry) {
        _telemetry.value = t
    }

    fun setGameForeground(fg: Boolean) {
        _gameForeground.value = fg
    }
}

class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var configRepo: ConfigRepository
    private lateinit var thermalGuard: ThermalGuard
    private lateinit var ramGuard: RamGuard
    private lateinit var hapticCtl: HapticCtl
    private lateinit var a11yInjector: A11yInjector
    private lateinit var shizukuInjector: ShizukuInjector
    private lateinit var aimEngine: AimEngine
    private lateinit var fireGateDetector: FireGateDetector
    private lateinit var gameWatcher: GameWatcher

    private lateinit var bubbleController: OverlayBubbleController
    private lateinit var panelController: OverlayPanelController
    private lateinit var hudController: HudOverlayController

    private val shizukuStateFlow = MutableStateFlow(ShizukuInjector.State.UNAVAILABLE)
    private var cachedMetrics: DisplayScaleMetrics? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        configRepo = ConfigRepository.getInstance(this)
        thermalGuard = ThermalGuard(this)
        ramGuard = RamGuard(this)
        hapticCtl = HapticCtl(this)
        a11yInjector = A11yInjector(this)
        shizukuInjector = ShizukuInjector(this) { state ->
            shizukuStateFlow.value = state
        }

        aimEngine = AimEngine(
            configRepo = configRepo,
            injectorProvider = { activeInjector() },
            metricsProvider = { getMetrics() },
            thermalGuard = thermalGuard,
            ramGuard = ramGuard,
            hapticCtl = hapticCtl
        )

        fireGateDetector = FireGateDetector(
            configRepo = configRepo,
            metricsProvider = { getMetrics() },
            aimEngine = aimEngine
        )

        gameWatcher = GameWatcher(this) { isFg ->
            A17RuntimeBus.setGameForeground(isFg)
            aimEngine.setGameForeground(isFg)
            updateNotification(isFg)
            refreshHud()
        }

        panelController = OverlayPanelController(
            context = this,
            windowManager = windowManager,
            configRepo = configRepo,
            onPanelVisibilityChanged = { open ->
                aimEngine.setPanelOpen(open)
            },
            onEmergencyKill = {
                emergencyKill()
            }
        )

        bubbleController = OverlayBubbleController(
            context = this,
            windowManager = windowManager,
            configRepo = configRepo,
            onBubbleTapped = {
                panelController.toggle()
            }
        )

        hudController = HudOverlayController(this, windowManager)

        createNotificationChannel()
        startForegroundCompat()

        shizukuInjector.attach()
        fireGateDetector.attach()
        aimEngine.start()
        gameWatcher.start(configRepo.current().antiCheatStealth.gameExitSleepMs)

        if (Settings.canDrawOverlays(this)) {
            bubbleController.show(resolveInjectorStatus())
        }

        A17RuntimeBus.activeService = this
        A17RuntimeBus.setOverlayRunning(true)

        observeStreams()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                emergencyKill()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EMERGENCY_KILL -> {
                emergencyKill()
            }
            ACTION_GAME_LAUNCHED -> {
                gameWatcher.notifyGameLaunched()
                shizukuInjector.refresh()
            }
        }
        if (Settings.canDrawOverlays(this)) {
            bubbleController.show(resolveInjectorStatus())
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        A17RuntimeBus.activeService = null
        A17RuntimeBus.setOverlayRunning(false)
        A17RuntimeBus.setGameForeground(false)
        fireGateDetector.detach()
        gameWatcher.stop()
        aimEngine.stop()
        shizukuInjector.detach()
        a11yInjector.shutdown()
        panelController.close()
        bubbleController.hide()
        hudController.hide()
        scope.cancel()
        super.onDestroy()
    }

    fun emergencyKill() {
        configRepo.setMaster(false)
        fireGateDetector.closeGate()
        aimEngine.forceStopPull()
    }

    fun resetWeaponTiming(weaponTag: String) {
        aimEngine.timingLearner.onWeaponChanged(weaponTag)
    }

    fun triggerTestGate(open: Boolean) {
        val cfg = configRepo.current()
        val (fx, fy) = DeviceCalibration.getFireButtonCenter(getMetrics(), cfg.calibration)
        if (open) {
            // Allow interactive test even outside game foreground temporarily
            aimEngine.setGameForeground(true)
            fireGateDetector.openGate(fx, fy)
        } else {
            fireGateDetector.closeGate()
            aimEngine.setGameForeground(gameWatcher.isGameForeground.value)
        }
    }

    private fun observeStreams() {
        scope.launch {
            shizukuStateFlow.collectLatest {
                val status = resolveInjectorStatus()
                A17RuntimeBus.setInjectorStatus(status)
                bubbleController.updateStatus(status)
                refreshHud()
            }
        }
        scope.launch {
            aimEngine.telemetry.collectLatest { t ->
                val status = resolveInjectorStatus()
                A17RuntimeBus.setInjectorStatus(status)
                bubbleController.updateStatus(status)
                A17RuntimeBus.setTelemetry(t)
                refreshHud()
            }
        }
        scope.launch {
            configRepo.configFlow.collectLatest { cfg ->
                cachedMetrics = null
                panelController.renderConfig(cfg)
                refreshHud()
            }
        }
    }

    private fun activeInjector(): Injector {
        return if (shizukuInjector.isReady) shizukuInjector else a11yInjector
    }

    private fun resolveInjectorStatus(): InjectorStatus {
        return when {
            shizukuStateFlow.value == ShizukuInjector.State.READY && shizukuInjector.isReady -> {
                InjectorStatus.SHIZUKU_OK
            }
            a11yInjector.isReady -> {
                InjectorStatus.A11Y_FALLBACK
            }
            else -> {
                InjectorStatus.NO_PERMISSION
            }
        }
    }

    private fun refreshHud() {
        val cfg = configRepo.current()
        hudController.update(
            enabled = cfg.fingerFeedback.hud,
            gameForeground = gameWatcher.isGameForeground.value,
            telemetry = aimEngine.telemetry.value,
            injectorStatus = resolveInjectorStatus()
        )
    }

    private fun getMetrics(): DisplayScaleMetrics {
        val existing = cachedMetrics
        if (existing != null) return existing
        val computed = DeviceCalibration.computeMetrics(this, configRepo.current().calibration)
        cachedMetrics = computed
        return computed
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(gameFg: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (gameFg) {
            getString(R.string.notif_text_active)
        } else {
            getString(R.string.notif_text_waiting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_crosshair)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(contentPending)
            .addAction(R.drawable.ic_close, getString(R.string.notif_action_stop), stopPending)
            .build()
    }

    private fun startForegroundCompat() {
        val notif = buildNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun updateNotification(gameFg: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(gameFg))
    }

    companion object {
        private const val CHANNEL_ID = "a17_assist_overlay_channel"
        private const val NOTIF_ID = 1701
        const val ACTION_STOP_SERVICE = "com.a17.assist.ffvn.STOP_OVERLAY"
        const val ACTION_EMERGENCY_KILL = "com.a17.assist.ffvn.EMERGENCY_KILL"
        const val ACTION_GAME_LAUNCHED = "com.a17.assist.ffvn.GAME_LAUNCHED"

        fun start(context: Context, gameLaunched: Boolean = false) {
            val intent = Intent(context, OverlayService::class.java).apply {
                if (gameLaunched) action = ACTION_GAME_LAUNCHED
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            runCatching { context.startService(intent) }
        }
    }
}
