package ru.relsokhod.spb.live

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.relsokhod.spb.MainActivity
import ru.relsokhod.spb.R
import ru.relsokhod.spb.model.RouteSegment
import ru.relsokhod.spb.model.SegmentKind
import kotlin.math.roundToInt

data class LiveAlertSnapshot(
    val routeBadge: String,
    val modeLabel: String,
    val exitStopName: String,
    val minutesRemaining: Int,
    val stopsRemaining: Int,
    val progressFraction: Float,
    val brandColorArgb: Int,
    val isGpsLocked: Boolean
)

/**
 * Coordinates Android 16/17 Live Updates (ColorOS 17 / Realme UI 7.0 Dynamic Island & Live Alerts capsule)
 * alongside optional background GPS tracking via LiveTripNotificationService.
 */
object LiveTripLiveAlertManager {
    const val CHANNEL_ID = "relsokhod_live_trip_v1"
    const val NOTIFICATION_ID = 42077
    const val ACTION_STOP_LIVE_TRIP = "ru.relsokhod.spb.ACTION_STOP_LIVE_TRIP"
    const val EXTRA_OPEN_LIVE_TRIP = "open_live_trip"

    @Volatile
    var lastSnapshot: LiveAlertSnapshot? = null

    @Volatile
    var latestGeoPoint: LiveGeoPoint? = null

    @Volatile
    var stopRequestedFromNotification: Boolean = false

    @Volatile
    var activeSegment: RouteSegment? = null

    @Volatile
    var activeStopsById: Map<String, ru.relsokhod.spb.model.TramStop> = emptyMap()

    @Volatile
    var activeElapsedBaseSeconds: Int = 0

    @Volatile
    var activeUpdatedAtMs: Long = 0L

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun canPostPromotedLiveAlerts(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    fun openPromotedLiveAlertsSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 36) {
            Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_DEFAULT) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Активная поездка (Dynamic Island / Live Alerts)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Отображает текущий шаг маршрута, оставшиеся остановки и время прибытия в статус-баре и Live Alerts."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun buildSnapshot(
        seg: RouteSegment,
        progress: SegmentLiveProgress
    ): LiveAlertSnapshot {
        val badge = when (seg.kind) {
            SegmentKind.WALK -> "Пешком"
            SegmentKind.FALLBACK_TRANSIT -> "Автобус"
            SegmentKind.TRAM -> seg.route?.number?.removePrefix("А-") ?: "Т"
        }
        val mode = when (seg.kind) {
            SegmentKind.WALK -> "Пешком"
            SegmentKind.FALLBACK_TRANSIT -> seg.fallbackModeName ?: "Автобус"
            SegmentKind.TRAM -> when (seg.route?.type) {
                "bus" -> "Автобус $badge"
                "trolleybus" -> "Троллейбус ${badge.removePrefix("Тр-")}"
                "metro" -> "Метро $badge"
                "chizhik" -> "«Чижик» $badge"
                else -> "Трамвай № $badge"
            }
        }
        val colorArgb = when {
            seg.kind == SegmentKind.WALK -> Color.parseColor("#6366F1")
            seg.kind == SegmentKind.FALLBACK_TRANSIT -> Color.parseColor("#16A34A")
            !seg.route?.colorHex.isNullOrBlank() -> runCatching {
                Color.parseColor(seg.route!!.colorHex)
            }.getOrDefault(Color.parseColor("#16A34A"))
            else -> Color.parseColor("#16A34A")
        }
        return LiveAlertSnapshot(
            routeBadge = badge,
            modeLabel = mode,
            exitStopName = seg.toStop.name.substringBefore("(").trim(),
            minutesRemaining = progress.minutesRemaining,
            stopsRemaining = progress.stopsRemaining,
            progressFraction = progress.progressFraction,
            brandColorArgb = colorArgb,
            isGpsLocked = progress.isGpsLocked
        )
    }

    fun buildNotification(context: Context, snapshot: LiveAlertSnapshot): Notification {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_LIVE_TRIP, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, LiveTripNotificationService::class.java).apply {
            action = ACTION_STOP_LIVE_TRIP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Выходите: ${snapshot.exitStopName}"
        val stopsWord = LiveTripProgressMath.pluralizeStopsRu(snapshot.stopsRemaining)
        val contentText = buildString {
            append(snapshot.modeLabel)
            append(" · ")
            append(snapshot.minutesRemaining)
            append(" мин")
            if (snapshot.stopsRemaining > 0) {
                append(" · ")
                append(snapshot.stopsRemaining)
                append(" ")
                append(stopsWord)
            }
            if (snapshot.isGpsLocked) {
                append(" (GPS)")
            }
        }

        if (Build.VERSION.SDK_INT >= 36) {
            return buildAndroid16LiveUpdateNotification(
                context = context,
                snapshot = snapshot,
                title = title,
                contentText = contentText,
                contentPendingIntent = contentPendingIntent,
                stopPendingIntent = stopPendingIntent
            )
        }

        val progressInt = (snapshot.progressFraction * 1000f).roundToInt().coerceIn(0, 1000)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tram_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(snapshot.routeBadge)
            .setColor(snapshot.brandColorArgb)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(1000, progressInt, false)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Свернуть", stopPendingIntent)
            .build()
    }

    @RequiresApi(36)
    private fun buildAndroid16LiveUpdateNotification(
        context: Context,
        snapshot: LiveAlertSnapshot,
        title: String,
        contentText: String,
        contentPendingIntent: PendingIntent,
        stopPendingIntent: PendingIntent
    ): Notification {
        // Concise critical chip text (<= 7 chars) for ColorOS Live Alerts / Realme Dynamic Island capsule
        val mins = snapshot.minutesRemaining.coerceAtLeast(0)
        val cleanNum = snapshot.routeBadge.trim()
        val shortChip = when {
            cleanNum == "Пешком" -> "${mins}м пеш"
            cleanNum.length <= 3 -> "$cleanNum·${mins}м"
            else -> "$mins мин"
        }.take(7)

        val completedUnits = (snapshot.progressFraction * 1000f).roundToInt().coerceIn(1, 999)
        val remainingUnits = (1000 - completedUnits).coerceAtLeast(1)

        // Pure ProgressStyle with NO RemoteViews so ColorOS / Android 16 Live Alerts promotion succeeds
        val progressStyle = Notification.ProgressStyle()
            .setProgressSegments(
                listOf(
                    Notification.ProgressStyle.Segment(completedUnits).setColor(snapshot.brandColorArgb),
                    Notification.ProgressStyle.Segment(remainingUnits).setColor(Color.parseColor("#475569"))
                )
            )
            .setProgress(completedUnits)

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tram_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setColor(snapshot.brandColorArgb)
            .setColorized(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortChip)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Завершить",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    fun startOrUpdate(
        context: Context,
        snapshot: LiveAlertSnapshot,
        segment: RouteSegment? = null,
        stopsById: Map<String, ru.relsokhod.spb.model.TramStop>? = null,
        elapsedSeconds: Int? = null
    ) {
        stopRequestedFromNotification = false
        lastSnapshot = snapshot
        if (segment != null) activeSegment = segment
        if (stopsById != null) activeStopsById = stopsById
        if (elapsedSeconds != null) {
            activeElapsedBaseSeconds = elapsedSeconds
            activeUpdatedAtMs = android.os.SystemClock.elapsedRealtime()
        }
        if (!hasNotificationPermission(context)) return

        val appCtx = context.applicationContext
        if (hasLocationPermission(appCtx)) {
            val intent = Intent(appCtx, LiveTripNotificationService::class.java)
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure {
                postDirect(appCtx, snapshot)
            }
        } else {
            postDirect(appCtx, snapshot)
        }
    }

    fun updateNotificationOnly(context: Context, snapshot: LiveAlertSnapshot) {
        lastSnapshot = snapshot
        if (!hasNotificationPermission(context)) return
        postDirect(context.applicationContext, snapshot)
    }

    private fun postDirect(context: Context, snapshot: LiveAlertSnapshot) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            nm.notify(NOTIFICATION_ID, buildNotification(context, snapshot))
        }
    }

    fun stop(context: Context) {
        lastSnapshot = null
        activeSegment = null
        activeStopsById = emptyMap()
        val appCtx = context.applicationContext
        runCatching {
            appCtx.stopService(Intent(appCtx, LiveTripNotificationService::class.java))
        }
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { nm?.cancel(NOTIFICATION_ID) }
    }
}

class LiveTripNotificationService : Service() {

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == LiveTripLiveAlertManager.ACTION_STOP_LIVE_TRIP) {
            LiveTripLiveAlertManager.stopRequestedFromNotification = true
            LiveTripLiveAlertManager.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val snapshot = LiveTripLiveAlertManager.lastSnapshot
        if (snapshot == null || !LiveTripLiveAlertManager.hasNotificationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = LiveTripLiveAlertManager.buildNotification(this, snapshot)
        if (LiveTripLiveAlertManager.hasLocationPermission(this)) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        LiveTripLiveAlertManager.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(LiveTripLiveAlertManager.NOTIFICATION_ID, notification)
                }
            }.onFailure {
                LiveTripLiveAlertManager.updateNotificationOnly(this, snapshot)
            }
            startGpsTrackingIfNeeded()
        } else {
            LiveTripLiveAlertManager.updateNotificationOnly(this, snapshot)
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startGpsTrackingIfNeeded() {
        if (locationListener != null) return
        if (!LiveTripLiveAlertManager.hasLocationPermission(this)) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val geo = LiveGeoPoint(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                )
                LiveTripLiveAlertManager.latestGeoPoint = geo
                val seg = LiveTripLiveAlertManager.activeSegment
                val stopsMap = LiveTripLiveAlertManager.activeStopsById
                if (seg != null && stopsMap.isNotEmpty()) {
                    val deltaSec = if (LiveTripLiveAlertManager.activeUpdatedAtMs > 0L) {
                        ((android.os.SystemClock.elapsedRealtime() - LiveTripLiveAlertManager.activeUpdatedAtMs) / 1000L).toInt().coerceAtLeast(0)
                    } else 0
                    val progress = LiveTripProgressMath.computeSegmentLiveProgress(
                        segment = seg,
                        stopsById = stopsMap,
                        elapsedSeconds = LiveTripLiveAlertManager.activeElapsedBaseSeconds + deltaSec,
                        userCoords = geo
                    )
                    val updatedSnapshot = LiveTripLiveAlertManager.buildSnapshot(seg, progress)
                    LiveTripLiveAlertManager.updateNotificationOnly(this@LiveTripNotificationService, updatedSnapshot)
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        locationListener = listener

        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 5f, listener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 10f, listener, Looper.getMainLooper())
            }
        }
    }

    override fun onDestroy() {
        locationListener?.let { listener ->
            runCatching { locationManager?.removeUpdates(listener) }
        }
        locationListener = null
        super.onDestroy()
    }
}
