package com.salarydance.app

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

/** 通知栏“结束摸鱼”按钮的跨组件事件 */
object BreakEvents {
    @Volatile var onEndedRemotely: (() -> Unit)? = null
}

/**
 * 摸鱼计时前台服务：App 退到后台/被杀/锁屏时，计时器照常走，
 * 通知栏显示系统秒表（系统 UI 自走，零 CPU），并可一键结束。
 */
class TimerService : Service() {
    companion object {
        const val CHANNEL_TIMER = "break_timer"
        private const val NOTIF_ID = 1
        private const val ACTION_START = "com.salarydance.app.timer.START"
        private const val ACTION_END = "com.salarydance.app.timer.END"
        private const val EXTRA_START = "start_ms"

        fun start(ctx: Context, startMs: Long) {
            val i = Intent(ctx, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_START, startMs)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
        }
    }

    private var startMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_TIMER, "摸鱼计时器", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            endBreak()
            return START_NOT_STICKY
        }
        startMs = intent?.getLongExtra(EXTRA_START, 0L) ?: 0L
        if (startMs <= 0L) {
            startMs = Store.state.activeBreak ?: System.currentTimeMillis()
        }
        startInForeground()
        return START_STICKY
    }

    private fun startInForeground() {
        val n = buildTimerNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildTimerNotification(): Notification {
        val b = Notification.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("摸鱼计时中 🐟")
            .setContentText("工资照常在涨，安心摸")
            .setWhen(startMs)
            .setShowWhen(true)
            .setUsesChronometer(true)          // 系统秒表自走，不经 App 刷新
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 31) b.setCategory(Notification.CATEGORY_PROGRESS)
        val endPi = PendingIntent.getService(
            this, 0,
            Intent(this, TimerService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        b.addAction(Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_timer),
            "结束摸鱼", endPi).build())
        return b.build()
    }

    private fun endBreak() {
        val st = Store.state
        synchronized(st) {
            val s = st.activeBreak
            if (s != null) {
                st.breaks.add(BreakRec(s, System.currentTimeMillis()))
                st.activeBreak = null
                Store.save(this)
            }
        }
        BreakEvents.onEndedRemotely?.invoke()
        stopSelf()
    }
}
