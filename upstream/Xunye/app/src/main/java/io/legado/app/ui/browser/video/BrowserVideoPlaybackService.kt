package io.legado.app.ui.browser.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.browser.engine.BrowserVideoMedia
import io.legado.app.browser.engine.BrowserVideoOffer
import io.legado.app.browser.engine.BrowserVideoOfferBus

/**
 * SPEC-132/135/137/188: foreground media notification
 * (native VideoView, headless audio ExoPlayer, or in-page HTML5 via WebMediaBridge).
 * SPEC-137: MediaSession progress scrubber + seek ±15s actions.
 */
class BrowserVideoPlaybackService : Service() {

    private var audioPlayer: ExoPlayer? = null
    private var audioOnly: Boolean = false
    private var pageMedia: Boolean = false
    private var titleText: String = "视频播放中"
    private var mediaSession: MediaSessionCompat? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (pageMedia) {
                BrowserWebMediaBridge.refreshStateFromPage()
            }
            publishSessionState()
            startAsForeground(BrowserVideoPlayerBridge.isPlaying())
            if (BrowserVideoPlayerBridge.isPlaying() || audioPlayer != null ||
                (pageMedia && BrowserWebMediaBridge.active)
            ) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "browsesth_video").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    BrowserVideoPlayerBridge.play()
                    startAsForeground(true)
                    scheduleTicks()
                }

                override fun onPause() {
                    BrowserVideoPlayerBridge.pause()
                    startAsForeground(false)
                }

                override fun onSeekTo(pos: Long) {
                    BrowserVideoPlayerBridge.seekTo(pos)
                    publishSessionState()
                    startAsForeground(BrowserVideoPlayerBridge.isPlaying())
                }

                override fun onFastForward() {
                    BrowserVideoPlayerBridge.seekBy(SEEK_STEP_MS)
                    publishSessionState()
                    startAsForeground(BrowserVideoPlayerBridge.isPlaying())
                }

                override fun onRewind() {
                    BrowserVideoPlayerBridge.seekBy(-SEEK_STEP_MS)
                    publishSessionState()
                    startAsForeground(BrowserVideoPlayerBridge.isPlaying())
                }

                override fun onStop() {
                    BrowserVideoPlayerBridge.pause()
                    releaseAudioPlayer()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // SPEC-254: after startForegroundService the platform requires startForeground
        // within a few seconds — promote first, before ExoPlayer / early returns / stop.
        val action = intent?.action
        val pendingTitle = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank {
            when (action) {
                ACTION_START_AUDIO -> "音频播放中"
                ACTION_START_PAGE -> BrowserWebMediaBridge.title.ifBlank { "视频播放中" }
                else -> titleText.ifBlank { "视频播放中" }
            }
        }
        startAsForeground(
            playing = when (action) {
                ACTION_PAUSE, ACTION_STOP -> false
                ACTION_START_PAGE -> BrowserWebMediaBridge.isPlaying()
                else -> true
            },
            title = pendingTitle,
        )
        when (action) {
            ACTION_STOP -> {
                BrowserVideoPlayerBridge.pause()
                releaseAudioPlayer()
                mainHandler.removeCallbacks(tickRunnable)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                BrowserVideoPlayerBridge.pause()
                startAsForeground(playing = false)
                return START_STICKY
            }
            ACTION_PLAY -> {
                BrowserVideoPlayerBridge.play()
                startAsForeground(playing = true)
                scheduleTicks()
                return START_STICKY
            }
            ACTION_SEEK_BACK -> {
                BrowserVideoPlayerBridge.seekBy(-SEEK_STEP_MS)
                startAsForeground(BrowserVideoPlayerBridge.isPlaying())
                return START_STICKY
            }
            ACTION_SEEK_FWD -> {
                BrowserVideoPlayerBridge.seekBy(SEEK_STEP_MS)
                startAsForeground(BrowserVideoPlayerBridge.isPlaying())
                return START_STICKY
            }
            ACTION_START_AUDIO -> {
                val offer = readOffer(intent) ?: run {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                titleText = offer.title.ifBlank { "音频播放中" }
                audioOnly = true
                pageMedia = false
                audioOnlyActive = true
                startAsForeground(playing = true, title = titleText)
                startAudioPlayer(offer)
                scheduleTicks()
                return START_STICKY
            }
            ACTION_START_PAGE -> {
                audioOnly = false
                pageMedia = true
                audioOnlyActive = false
                titleText = pendingTitle
                startAsForeground(playing = BrowserWebMediaBridge.isPlaying(), title = titleText)
                scheduleTicks()
                return START_STICKY
            }
            else -> {
                audioOnly = false
                pageMedia = false
                audioOnlyActive = false
                titleText = pendingTitle
                startAsForeground(playing = true, title = titleText)
                scheduleTicks()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickRunnable)
        releaseAudioPlayer()
        pageMedia = false
        audioOnlyActive = false
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun scheduleTicks() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    private fun startAudioPlayer(offer: BrowserVideoOffer) {
        releaseAudioPlayer()
        val player = ExoPlayer.Builder(this).build().also { p ->
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            // SPEC-224: 先当前 UA；失败后桌面 UA 重试一次。
            var triedDesktopUa = false
            fun attachSource(forceUa: String?) {
                p.setMediaSource(
                    BrowserVideoMedia.buildSource(this, offer, forceUserAgent = forceUa),
                )
                p.prepare()
                p.playWhenReady = true
            }
            attachSource(null)
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (audioOnly) {
                        startAsForeground(playing = isPlaying, title = titleText)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // SPEC-225: peek CDN response before desktop UA retry / stop.
                    BrowserVideoMedia.peekLastBuildFailure(error.message)
                    if (!triedDesktopUa) {
                        triedDesktopUa = true
                        attachSource(io.legado.app.browser.engine.BrowserUa.DESKTOP)
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            })
        }
        audioPlayer = player
        BrowserVideoPlayerBridge.attachAudio(player)
    }

    private fun releaseAudioPlayer() {
        audioPlayer?.let { p ->
            BrowserVideoPlayerBridge.detachAudio(p)
            runCatching {
                p.stop()
                p.release()
            }
        }
        audioPlayer = null
        if (audioOnly) {
            BrowserVideoPlayerBridge.stopAudio()
        }
        audioOnly = false
    }

    private fun publishSessionState() {
        val session = mediaSession ?: return
        val playing = BrowserVideoPlayerBridge.isPlaying()
        val pos = BrowserVideoPlayerBridge.positionMs()
        val dur = BrowserVideoPlayerBridge.durationMs()
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur.coerceAtLeast(0L))
                .build(),
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_STOP
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    pos,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
    }

    private fun startAsForeground(playing: Boolean, title: String = titleText) {
        titleText = title
        publishSessionState()
        ensureChannel()
        val notification = buildNotification(playing, title)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(playing: Boolean, title: String): Notification {
        val openIntent = if (audioOnly || pageMedia) {
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, BrowserVideoPlayerActivity::class.java)
        } else {
            Intent(this, BrowserVideoPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val seekBack = PendingIntent.getService(
            this,
            1,
            Intent(this, BrowserVideoPlaybackService::class.java).setAction(ACTION_SEEK_BACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPause = PendingIntent.getService(
            this,
            2,
            Intent(this, BrowserVideoPlaybackService::class.java)
                .setAction(if (playing) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val seekFwd = PendingIntent.getService(
            this,
            3,
            Intent(this, BrowserVideoPlaybackService::class.java).setAction(ACTION_SEEK_FWD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { style.setMediaSession(it) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(
                when {
                    audioOnly && playing -> "后台听音频"
                    audioOnly -> "已暂停"
                    pageMedia && playing -> "网页播放中"
                    pageMedia -> "已暂停"
                    playing -> "播放中"
                    else -> "已暂停"
                },
            )
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(0, "-15s", seekBack)
            .addAction(0, if (playing) "暂停" else "播放", playPause)
            .addAction(0, "+15s", seekFwd)
            .setStyle(style)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "视频播放",
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "browsesth_video_playback"
        private const val NOTIFICATION_ID = 13201
        private const val SEEK_STEP_MS = 15_000L
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "audio_url"
        const val EXTRA_HEADERS_JSON = "headers_json"
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_IS_HLS = "is_hls"
        const val ACTION_PAUSE = "io.legado.app.video.PAUSE"
        const val ACTION_PLAY = "io.legado.app.video.PLAY"
        const val ACTION_STOP = "io.legado.app.video.STOP"
        const val ACTION_START_AUDIO = "io.legado.app.video.START_AUDIO"
        const val ACTION_START_PAGE = "io.legado.app.video.START_PAGE"
        const val ACTION_SEEK_BACK = "io.legado.app.video.SEEK_BACK"
        const val ACTION_SEEK_FWD = "io.legado.app.video.SEEK_FWD"

        @Volatile
        private var audioOnlyActive: Boolean = false

        /** SPEC-188: leave-to-stop must not kill 「听音频」. */
        fun isAudioOnlyActive(): Boolean = audioOnlyActive

        fun start(context: Context, title: String) {
            audioOnlyActive = false
            val intent = Intent(context, BrowserVideoPlaybackService::class.java)
                .putExtra(EXTRA_TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** SPEC-188: notification for in-page HTML5 media. */
        fun startPage(context: Context, title: String) {
            audioOnlyActive = false
            val intent = Intent(context, BrowserVideoPlaybackService::class.java)
                .setAction(ACTION_START_PAGE)
                .putExtra(EXTRA_TITLE, title)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startAudio(context: Context, offer: BrowserVideoOffer) {
            audioOnlyActive = true
            val intent = Intent(context, BrowserVideoPlaybackService::class.java)
                .setAction(ACTION_START_AUDIO)
                .putExtra(EXTRA_URL, offer.url)
                .putExtra(EXTRA_TITLE, offer.title)
                .putExtra(EXTRA_PAGE_URL, offer.pageUrl)
                .putExtra(EXTRA_IS_HLS, offer.isHls)
            if (offer.headers.isNotEmpty()) {
                intent.putExtra(
                    EXTRA_HEADERS_JSON,
                    org.json.JSONObject(offer.headers).toString(),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * SPEC-254: do not [Context.stopService] while a pending
         * [Context.startForegroundService] may not have promoted yet — that races into
         * ForegroundServiceDidNotStartInTimeException. Route stop through onStartCommand.
         */
        fun stop(context: Context) {
            audioOnlyActive = false
            val intent = Intent(context, BrowserVideoPlaybackService::class.java)
                .setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure {
                        runCatching {
                            context.stopService(
                                Intent(context, BrowserVideoPlaybackService::class.java),
                            )
                        }
                    }
            } else {
                context.startService(intent)
            }
        }

        private fun readOffer(intent: Intent?): BrowserVideoOffer? {
            val i = intent ?: return null
            val url = i.getStringExtra(EXTRA_URL)?.trim().orEmpty()
            if (url.isEmpty()) return null
            return BrowserVideoOffer(
                url = url,
                headers = BrowserVideoOfferBus.parseHeaders(i.getStringExtra(EXTRA_HEADERS_JSON)),
                pageUrl = i.getStringExtra(EXTRA_PAGE_URL).orEmpty(),
                title = i.getStringExtra(EXTRA_TITLE).orEmpty(),
                isHls = i.getBooleanExtra(EXTRA_IS_HLS, url.contains(".m3u8", true)),
            )
        }
    }
}
