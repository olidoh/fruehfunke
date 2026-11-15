package de.fruehfunke.app.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import de.fruehfunke.app.data.Alarm
import de.fruehfunke.app.data.AlarmRepository
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.GestureAction
import de.fruehfunke.app.ui.RingingActivity
import de.fruehfunke.app.util.Sounds
import de.fruehfunke.app.util.SpeechText
import de.fruehfunke.app.util.StreamResolver
import de.fruehfunke.app.util.Weather
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.media3.common.AudioAttributes as Media3AudioAttributes

/** Spielt Weckton oder Radio, vibriert und hält den Wecker im Vordergrund. */
class AlarmService : Service() {
    companion object {
        const val ACTION_RING = "de.fruehfunke.app.action.RING"
        const val ACTION_SILENCE = "de.fruehfunke.app.action.SILENCE"

        /** Spätestens dann muss das Radio laufen, sonst kommt der Ersatzton. */
        private const val STREAM_DEADLINE_MS = 25_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var stream: ExoPlayer? = null
    private var streamJob: Job? = null
    private var streamRunning = false
    private var usingFallback = false
    private var vibrator: Vibrator? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var savedAlarmVolume = -1
    private var currentId = -1
    private var currentVolume = 1f
    private var gestures: AlarmGestures? = null
    private var tts: TextToSpeech? = null
    private var speakJob: Job? = null
    /** Weckton-Lautstärke während einer Ansage absenken */
    private var duck = 1f
    private var screenOffReceiver: BroadcastReceiver? = null
    private var gestureHandled = false
    private var hapticOnShutdown = false

    private val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
            ACTION_SILENCE -> shutdown()
            else -> if (currentId < 0) shutdown()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Int) {
        val alarm = AlarmRepository.get(id)
        // Muss sofort passieren, sonst beendet Android den Dienst.
        startForeground(
            Notifications.RINGING_ID,
            Notifications.ringing(this, alarm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (alarm == null) {
            if (currentId < 0) shutdown()
            return
        }

        // Klingelt schon ein anderer Wecker, wird dieser beendet.
        if (currentId >= 0 && currentId != id) {
            val previous = currentId
            stopPlayback()
            timeoutJob?.cancel()
            currentId = -1
            RingingState.current.value = null
            AlarmActions.stop(this, previous)
        }

        // Einmalige Wecker gelten ab jetzt als verbraucht; Schlummern funktioniert trotzdem.
        val ringing = AlarmRepository.update(id) {
            if (!it.isRepeating && !it.isCountdown) it.copy(enabled = false, snoozeUntil = 0L)
            else it.copy(snoozeUntil = 0L)
        } ?: alarm
        AlarmScheduler.rescheduleAll(this)
        Notifications.cancelSnoozed(this, id)

        currentId = id
        RingingState.current.value = RingInfo(
            alarmId = id,
            label = ringing.label,
            canSnooze = ringing.canSnoozeAgain,
            snoozeMinutes = ringing.snoozeMinutes,
            snoozeCount = ringing.snoozeCount,
            source = ringing.streamName?.let { "Radio: $it · verbinde …" } ?: "",
        )
        startForeground(
            Notifications.RINGING_ID,
            Notifications.ringing(this, ringing),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        val pm = getSystemService(PowerManager::class.java)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Fruehfunke:ring").apply {
            acquire(ringing.ringMinutes * 60_000L + 60_000L)
        }

        startPlayback(ringing)
        if (ringing.vibrate) startVibration()
        startControls(id)
        startSpeaking(ringing)

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(ringing.ringMinutes * 60_000L)
            if (ringing.snoozeAfterTimeout && ringing.canSnoozeAgain) {
                AlarmActions.snooze(this@AlarmService, id)
            } else {
                AlarmActions.stop(this@AlarmService, id)
            }
        }

        try {
            startActivity(
                Intent(this, RingingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            )
        } catch (_: Exception) {
            // Ohne Erlaubnis übernimmt die Vollbild-Benachrichtigung.
        }
    }

    // ---------------------------------------------------------------- Sprechende Uhr

    private fun startSpeaking(alarm: Alarm) {
        if (!alarm.speak) return
        speakJob?.cancel()
        speakJob = scope.launch {
            val s = AppSettings.state.value
            // Wetter schon während der Verzögerung laden
            val weather = async {
                if (SpeechText.needsWeather(s.speakText)) Weather.refresh(maxAgeMs = 20 * 60_000L) else null
            }
            delay(s.speakDelaySec * 1000L)
            val w = withTimeoutOrNull(12_000L) { weather.await() }
            val engine = initTts() ?: return@launch
            tts = engine
            while (isActive) {
                val text = SpeechText.build(AppSettings.state.value, alarm, w)
                duck = 0.15f
                pushVolume()
                engine.speakAndWait(text)
                duck = 1f
                pushVolume()
                val repeat = AppSettings.state.value.speakRepeatMin
                if (repeat <= 0) break
                delay(repeat * 60_000L)
            }
        }
    }

    private suspend fun initTts(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        var engine: TextToSpeech? = null
        engine = TextToSpeech(this) { status ->
            val e = engine
            if (status == TextToSpeech.SUCCESS && e != null) {
                e.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val r = e.setLanguage(Locale.GERMANY)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) e.setLanguage(Locale.getDefault())
                if (cont.isActive) cont.resume(e)
            } else {
                e?.shutdown()
                if (cont.isActive) cont.resume(null)
            }
        }
        cont.invokeOnCancellation { engine?.shutdown() }
    }

    private suspend fun TextToSpeech.speakAndWait(text: String) {
        val id = UUID.randomUUID().toString()
        withTimeoutOrNull(60_000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == id && cont.isActive) cont.resume(Unit)
                    }
                })
                speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                cont.invokeOnCancellation { stop() }
            }
        }
    }

    private fun stopSpeaking() {
        speakJob?.cancel()
        speakJob = null
        tts?.let {
            runCatching { it.stop() }
            it.shutdown()
        }
        tts = null
        duck = 1f
    }

    // ---------------------------------------------------------------- Steuerung

    /** Sensoren und Power-Knopf je nach Einstellung überwachen. */
    private fun startControls(id: Int) {
        stopControls()
        gestureHandled = false
        val s = AppSettings.state.value
        val useMotion = s.flipAction != GestureAction.NOTHING || s.shakeAction != GestureAction.NOTHING
        val useProximity = s.proximityAction != GestureAction.NOTHING
        if (useMotion || useProximity) {
            gestures = AlarmGestures(this, s.shakeThreshold) { g ->
                val action = when (g) {
                    Gesture.FLIP -> AppSettings.state.value.flipAction
                    Gesture.SHAKE -> AppSettings.state.value.shakeAction
                    Gesture.PROXIMITY -> AppSettings.state.value.proximityAction
                }
                perform(id, action)
            }.also { it.start(useMotion, useProximity) }
        }
        if (s.powerAction != GestureAction.NOTHING) {
            // Bildschirm aus während des Klingelns = Power-Knopf gedrückt.
            // Die ersten Sekunden ignorieren, solange der Weckbildschirm erst angeht.
            val armedAt = SystemClock.elapsedRealtime() + 3_000L
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (SystemClock.elapsedRealtime() >= armedAt) {
                        perform(id, AppSettings.state.value.powerAction)
                    }
                }
            }
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            screenOffReceiver = receiver
        }
    }

    private fun perform(id: Int, action: GestureAction) {
        if (currentId != id || action == GestureAction.NOTHING || gestureHandled) return
        gestureHandled = true
        hapticOnShutdown = AppSettings.state.value.gestureHaptics
        when (action) {
            GestureAction.SNOOZE -> AlarmActions.snooze(this, id)
            GestureAction.STOP -> AlarmActions.stop(this, id)
            GestureAction.NOTHING -> {}
        }
    }

    private fun stopControls() {
        gestures?.stop()
        gestures = null
        screenOffReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenOffReceiver = null
    }

    // ---------------------------------------------------------------- Wiedergabe

    private fun startPlayback(alarm: Alarm) {
        val am = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAttributes)
            .build()
            .also { am.requestAudioFocus(it) }

        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                max(1, (maxVol * alarm.volume / 100f).roundToInt()),
                0,
            )
        }

        startVolumeRamp(alarm.rampSeconds)
        usingFallback = false
        streamRunning = false
        val url = alarm.streamUrl
        if (url != null) startStream(alarm, url) else startRingtone(alarm)
    }

    private fun startVolumeRamp(rampSeconds: Int) {
        rampJob?.cancel()
        if (rampSeconds <= 0) {
            applyVolume(1f)
            return
        }
        applyVolume(0.02f)
        val total = rampSeconds * 1000f
        val start = SystemClock.elapsedRealtime()
        rampJob = scope.launch {
            while (isActive) {
                val f = ((SystemClock.elapsedRealtime() - start) / total).coerceIn(0f, 1f)
                applyVolume((f * f).coerceAtLeast(0.02f))
                if (f >= 1f) break
                delay(200)
            }
        }
    }

    private fun applyVolume(v: Float) {
        currentVolume = v
        pushVolume()
    }

    private fun pushVolume() {
        val eff = currentVolume * duck
        player?.setVolume(eff, eff)
        stream?.volume = eff
    }

    private fun startRingtone(alarm: Alarm) {
        val wanted = alarm.ringtoneUri?.let { Uri.parse(it) }
        val mp = (wanted?.let { createPlayer(it) }) ?: createPlayer(Sounds.defaultUri()) ?: return
        player = mp
        mp.setVolume(currentVolume * duck, currentVolume * duck)
        mp.start()
    }

    private fun createPlayer(uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttributes)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp
        } catch (e: Exception) {
            mp.release()
            null
        }
    }

    private fun startStream(alarm: Alarm, url: String) {
        acquireWifiLock()
        streamJob = scope.launch {
            // Sicherheitsnetz: läuft das Radio nicht rechtzeitig, klingelt der Ersatzton.
            launch {
                delay(STREAM_DEADLINE_MS)
                if (!streamRunning) fallbackToRingtone(alarm, "Radio nicht erreichbar")
            }
            // Nach dem Aufwachen braucht das WLAN oft ein paar Sekunden.
            val cm = getSystemService(ConnectivityManager::class.java)
            var waited = 0L
            while (cm.activeNetwork == null && waited < 15_000L) {
                delay(500)
                waited += 500
            }
            if (cm.activeNetwork == null) {
                fallbackToRingtone(alarm, "Kein Netzwerk")
                return@launch
            }
            val resolved = withContext(Dispatchers.IO) { StreamResolver.resolve(url) }
            if (usingFallback || currentId != alarm.id) return@launch

            val exo = ExoPlayer.Builder(this@AlarmService).build()
            exo.setAudioAttributes(
                Media3AudioAttributes.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            exo.volume = currentVolume * duck
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && !streamRunning) {
                        streamRunning = true
                        updateSource("Radio: " + (alarm.streamName ?: "Internetradio"))
                    }
                    if (state == Player.STATE_ENDED) fallbackToRingtone(alarm, "Radio unterbrochen")
                }

                override fun onPlayerError(error: PlaybackException) {
                    fallbackToRingtone(alarm, if (streamRunning) "Radio unterbrochen" else "Radio nicht erreichbar")
                }
            })
            exo.setMediaItem(MediaItem.fromUri(resolved))
            exo.prepare()
            exo.playWhenReady = true
            stream = exo
        }
    }

    private fun fallbackToRingtone(alarm: Alarm, reason: String) {
        if (usingFallback || currentId != alarm.id) return
        usingFallback = true
        streamJob?.cancel()
        streamJob = null
        stream?.release()
        stream = null
        releaseWifiLock()
        startRingtone(alarm)
        updateSource("$reason – Ersatzton")
    }

    private fun updateSource(text: String) {
        RingingState.current.value = RingingState.current.value?.copy(source = text)
    }

    private fun acquireWifiLock() {
        val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock = runCatching {
            wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Fruehfunke:radio").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun startVibration() {
        val v = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator = v
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun stopPlayback() {
        stopControls()
        stopSpeaking()
        rampJob?.cancel()
        rampJob = null
        streamJob?.cancel()
        streamJob = null
        stream?.release()
        stream = null
        streamRunning = false
        releaseWifiLock()
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        val am = getSystemService(AudioManager::class.java)
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (savedAlarmVolume >= 0) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }
            savedAlarmVolume = -1
        }
    }

    private fun shutdown() {
        timeoutJob?.cancel()
        stopPlayback()
        if (hapticOnShutdown) {
            hapticOnShutdown = false
            // Kurze Rückmeldung, dass die Geste erkannt wurde
            de.fruehfunke.app.util.Haptics.pulse(this)
        }
        currentId = -1
        RingingState.current.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        timeoutJob?.cancel()
        stopPlayback()
        RingingState.current.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }
}
