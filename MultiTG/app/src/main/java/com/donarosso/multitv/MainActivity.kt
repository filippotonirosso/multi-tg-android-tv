package com.donarosso.multitv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

data class Channel(var name: String, var url: String)

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : Activity() {

    companion object {
        const val MAX_CH = 4
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        val DEFAULT_CHANNELS = listOf(
            Channel("Rai News 24", "INSERISCI_URL_M3U8"),
            Channel("Sky TG24", "INSERISCI_URL_M3U8"),
            Channel("TGCom24", "INSERISCI_URL_M3U8"),
            Channel("Rai 1", "INSERISCI_URL_M3U8"),
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var grid: LinearLayout
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var fullscreenView: PlayerView
    private lateinit var timerText: TextView
    private lateinit var clockText: TextView
    private val clockFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY)
    private val clockTick = object : Runnable {
        override fun run() {
            clockText.text = clockFormat.format(java.util.Date())
            handler.postDelayed(this, 1000)
        }
    }

    private val players = mutableListOf<ExoPlayer?>()
    private val tileViews = mutableListOf<PlayerView>()
    private val tiles = mutableListOf<FrameLayout>()
    private val audioBadges = mutableListOf<TextView>()
    private val statusTexts = mutableListOf<TextView>()
    private val retryRunnables = arrayOfNulls<Runnable>(MAX_CH)

    private var channels = mutableListOf<Channel>()
    private var count = 4
    private var audioIndex = 0
    private var fullscreenIndex = -1
    private var sleepTimer: CountDownTimer? = null
    private var quality = 1 // 0 = bassa 360p, 1 = media 540p, 2 = alta 720p

    private val qualityWidth = intArrayOf(640, 960, 1280)
    private val qualityHeight = intArrayOf(360, 540, 720)
    private val qualityBitrate = intArrayOf(1_200_000, 2_500_000, 4_000_000)
    private val qualityNames = arrayOf("Bassa (360p)", "Media (540p)", "Alta (720p)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfig()

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.keepScreenOn = true

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        root.addView(grid, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        fullscreenContainer = FrameLayout(this)
        fullscreenContainer.setBackgroundColor(Color.BLACK)
        fullscreenContainer.visibility = View.GONE
        fullscreenContainer.isFocusable = true
        fullscreenContainer.isClickable = true
        fullscreenContainer.isLongClickable = true
        fullscreenContainer.setOnClickListener { exitFullscreen() }
        fullscreenContainer.setOnLongClickListener { showMenu(); true }
        fullscreenView = PlayerView(this)
        fullscreenView.useController = false
        fullscreenView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        fullscreenContainer.addView(fullscreenView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(fullscreenContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        clockText = TextView(this)
        clockText.setTextColor(Color.WHITE)
        clockText.textSize = 20f
        clockText.setBackgroundColor(0x88000000.toInt())
        clockText.setPadding(24, 8, 24, 8)
        val clp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        clp.setMargins(0, 12, 0, 0)
        root.addView(clockText, clp)

        timerText = TextView(this)
        timerText.setTextColor(Color.WHITE)
        timerText.textSize = 16f
        timerText.setBackgroundColor(0x88000000.toInt())
        timerText.setPadding(16, 8, 16, 8)
        timerText.visibility = View.GONE
        val tlp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tlp.gravity = Gravity.BOTTOM or Gravity.END
        tlp.setMargins(0, 0, 24, 24)
        root.addView(timerText, tlp)

        setContentView(root)
        buildGrid()
        WakeService.sync(this, prefs().getBoolean("autostart", true))
    }

    // ---------- configurazione ----------

    private fun prefs() = getSharedPreferences("multitg", Context.MODE_PRIVATE)

    private fun loadConfig() {
        val p = prefs()
        count = p.getInt("count", 4)
        quality = p.getInt("quality", 1)
        channels.clear()
        for (i in 0 until MAX_CH) {
            val def = DEFAULT_CHANNELS[i]
            channels.add(Channel(
                p.getString("name$i", def.name) ?: def.name,
                p.getString("url$i", def.url) ?: def.url))
        }
        audioIndex = p.getInt("audio", 0)
    }

    private fun saveConfig() {
        val e = prefs().edit()
        e.putInt("count", count)
        e.putInt("quality", quality)
        e.putInt("audio", audioIndex)
        for (i in 0 until MAX_CH) {
            e.putString("name$i", channels[i].name)
            e.putString("url$i", channels[i].url)
        }
        e.apply()
    }

    // ---------- costruzione griglia e player ----------

    private fun buildGrid() {
        releaseAll()
        grid.removeAllViews()
        tiles.clear(); tileViews.clear(); audioBadges.clear(); statusTexts.clear()
        fullscreenIndex = -1
        fullscreenContainer.visibility = View.GONE
        grid.visibility = View.VISIBLE
        if (audioIndex >= count) audioIndex = 0

        val rows = if (count <= 2) 1 else 2
        val cols = 2
        var idx = 0
        for (r in 0 until rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            grid.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            for (c in 0 until cols) {
                if (idx >= count) break
                row.addView(buildTile(idx), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                idx++
            }
        }
        for (i in 0 until count) startPlayer(i)
        applyAudio()
        tiles.firstOrNull()?.requestFocus()
    }

    private fun buildTile(i: Int): FrameLayout {
        val tile = FrameLayout(this)
        tile.isFocusable = true
        tile.isClickable = true
        tile.isLongClickable = true
        tile.foreground = getDrawable(R.drawable.tile_focus)
        tile.setOnClickListener {
            if (audioIndex != i) selectAudio(i) else enterFullscreen(i)
        }
        tile.setOnLongClickListener { showMenu(); true }

        val pv = layoutInflater.inflate(R.layout.tile_player, tile, false) as PlayerView
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        tile.addView(pv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val label = TextView(this)
        label.text = channels[i].name
        label.setTextColor(Color.WHITE)
        label.textSize = 14f
        label.setBackgroundColor(0x88000000.toInt())
        label.setPadding(14, 6, 14, 6)
        val llp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        llp.gravity = Gravity.TOP or Gravity.START
        llp.setMargins(12, 12, 0, 0)
        tile.addView(label, llp)

        val badge = TextView(this)
        badge.text = "🔊"
        badge.textSize = 20f
        badge.setBackgroundColor(0x88000000.toInt())
        badge.setPadding(10, 4, 10, 4)
        badge.visibility = View.GONE
        val blp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.gravity = Gravity.TOP or Gravity.END
        blp.setMargins(0, 12, 12, 0)
        tile.addView(badge, blp)

        val status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 15f
        status.setBackgroundColor(0xAA000000.toInt())
        status.setPadding(20, 10, 20, 10)
        status.visibility = View.GONE
        val slp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.gravity = Gravity.CENTER
        tile.addView(status, slp)

        tiles.add(tile)
        tileViews.add(pv)
        audioBadges.add(badge)
        statusTexts.add(status)
        return tile
    }

    // Nei riquadri piccoli basta 540p: 4 stream HD contemporanei mettono in
    // ginocchio le TV meno potenti (scatti e buffering continui).
    private fun capQuality(p: ExoPlayer, capped: Boolean) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
            if (capped) {
                setMaxVideoSize(qualityWidth[quality], qualityHeight[quality])
                setMaxVideoBitrate(qualityBitrate[quality])
            } else {
                setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                setMaxVideoBitrate(Int.MAX_VALUE)
            }
        }.build()
    }

    private fun hlsItem(url: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

    private fun startPlayer(i: Int) {
        val url = channels[i].url.trim()
        if (url.isEmpty()) {
            setStatus(i, "Nessun URL configurato\n(tieni premuto OK per il menu)")
            players.add(null)
            return
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(20_000, 60_000, 3_000, 6_000)
                .build())
            .build()
        capQuality(player, capped = true)
        player.setMediaItem(hlsItem(url))
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                setStatus(i, "Connessione persa, riprovo…")
                scheduleRetry(i, 4000)
            }
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> setStatus(i, null)
                    Player.STATE_BUFFERING -> setStatus(i, "Caricamento…")
                    Player.STATE_ENDED -> scheduleRetry(i, 1000)
                    else -> {}
                }
            }
        })
        player.prepare()
        player.playWhenReady = true
        while (players.size <= i) players.add(null)
        players[i] = player
        tileViews[i].player = player
    }

    private fun scheduleRetry(i: Int, delayMs: Long) {
        retryRunnables[i]?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            val p = players.getOrNull(i) ?: return@Runnable
            p.setMediaItem(hlsItem(channels[i].url.trim()))
            p.prepare()
            p.playWhenReady = true
        }
        retryRunnables[i] = r
        handler.postDelayed(r, delayMs)
    }

    private fun setStatus(i: Int, msg: String?) {
        if (i >= statusTexts.size) return
        val t = statusTexts[i]
        if (msg == null) t.visibility = View.GONE
        else { t.text = msg; t.visibility = View.VISIBLE }
    }

    private fun releaseAll() {
        for (i in 0 until MAX_CH) retryRunnables[i]?.let { handler.removeCallbacks(it) }
        players.forEach { it?.release() }
        players.clear()
    }

    // ---------- audio e fullscreen ----------

    private fun selectAudio(i: Int) {
        audioIndex = i
        applyAudio()
        saveConfig()
    }

    private fun applyAudio() {
        for (i in 0 until count) {
            players.getOrNull(i)?.let { p ->
                p.volume = if (i == audioIndex) 1f else 0f
                // Non solo mutare: disabilitare proprio la traccia audio dei canali
                // non selezionati evita 3 decodifiche AAC inutili sulle TV lente.
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, i != audioIndex)
                    .build()
            }
            if (i < audioBadges.size)
                audioBadges[i].visibility = if (i == audioIndex) View.VISIBLE else View.GONE
        }
    }

    private fun enterFullscreen(i: Int) {
        val p = players.getOrNull(i) ?: return
        fullscreenIndex = i
        selectAudio(i)
        capQuality(p, capped = false)
        players.forEachIndexed { idx, other ->
            if (idx != i && idx < count) other?.playWhenReady = false
        }
        PlayerView.switchTargetView(p, tileViews[i], fullscreenView)
        grid.visibility = View.GONE
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.requestFocus()
    }

    private fun exitFullscreen() {
        val i = fullscreenIndex
        if (i < 0) return
        players.getOrNull(i)?.let { p ->
            capQuality(p, capped = true)
            PlayerView.switchTargetView(p, fullscreenView, tileViews[i])
        }
        players.forEachIndexed { idx, other ->
            if (idx != i && idx < count && other != null) {
                other.playWhenReady = true
                scheduleRetry(idx, 300)
            }
        }
        fullscreenContainer.visibility = View.GONE
        grid.visibility = View.VISIBLE
        fullscreenIndex = -1
        if (i < tiles.size) tiles[i].requestFocus()
    }

    // ---------- menu ----------

    private fun showMenu() {
        val layoutLabel = if (count == 4) "Layout: passa a 2 finestre" else "Layout: passa a 4 finestre"
        val timerLabel = if (sleepTimer == null) "Timer di spegnimento" else "Timer di spegnimento (attivo — annulla/cambia)"
        val autostart = prefs().getBoolean("autostart", true)
        val items = arrayOf(layoutLabel, timerLabel,
            "Qualità riquadri: ${qualityNames[quality]}",
            "Avvio automatico all'accensione: ${if (autostart) "Sì" else "No"}",
            "Modifica canali", "Riavvia tutti gli stream", "Esci dall'app")
        AlertDialog.Builder(this)
            .setTitle("Multi TG — Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { count = if (count == 4) 2 else 4; saveConfig(); buildGrid() }
                    1 -> showTimerDialog()
                    2 -> showQualityDialog()
                    3 -> {
                        prefs().edit().putBoolean("autostart", !autostart).apply()
                        WakeService.sync(this, !autostart)
                        showMenu()
                    }
                    4 -> showChannelEditor()
                    5 -> buildGrid()
                    6 -> confirmExit()
                }
            }
            .show()
    }

    private fun showQualityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Qualità dei riquadri (più bassa = TV più fluida)")
            .setSingleChoiceItems(qualityNames, quality) { dialog, which ->
                quality = which
                saveConfig()
                for (i in 0 until count) {
                    if (i != fullscreenIndex) players.getOrNull(i)?.let { capQuality(it, true) }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showTimerDialog() {
        val options = arrayOf("Disattiva timer", "15 minuti", "30 minuti", "60 minuti", "90 minuti", "120 minuti")
        val minutes = intArrayOf(0, 15, 30, 60, 90, 120)
        AlertDialog.Builder(this)
            .setTitle("Spegni l'app tra…")
            .setItems(options) { _, which -> setSleepTimer(minutes[which]) }
            .show()
    }

    private fun setSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        sleepTimer = null
        if (minutes == 0) {
            timerText.visibility = View.GONE
            return
        }
        timerText.visibility = View.VISIBLE
        sleepTimer = object : CountDownTimer(minutes * 60_000L, 30_000L) {
            override fun onTick(remaining: Long) {
                val min = (remaining + 59_999) / 60_000
                timerText.text = "⏾ spegnimento tra $min min"
            }
            override fun onFinish() {
                releaseAll()
                finishAffinity()
            }
        }.also { it.start() }
    }

    private fun showChannelEditor() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(40, 20, 40, 20)
        scroll.addView(box)
        val nameEdits = mutableListOf<EditText>()
        val urlEdits = mutableListOf<EditText>()
        for (i in 0 until MAX_CH) {
            val name = EditText(this)
            name.hint = "Nome canale ${i + 1}"
            name.setText(channels[i].name)
            name.inputType = InputType.TYPE_CLASS_TEXT
            box.addView(name)
            val url = EditText(this)
            url.hint = "URL m3u8 canale ${i + 1}"
            url.setText(channels[i].url)
            url.inputType = InputType.TYPE_TEXT_VARIATION_URI
            box.addView(url)
            nameEdits.add(name); urlEdits.add(url)
        }
        AlertDialog.Builder(this)
            .setTitle("Modifica canali")
            .setView(scroll)
            .setPositiveButton("Salva") { _, _ ->
                for (i in 0 until MAX_CH) {
                    channels[i].name = nameEdits[i].text.toString().trim()
                    channels[i].url = urlEdits[i].text.toString().trim()
                }
                saveConfig()
                buildGrid()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("Uscire da Multi TG?")
            .setPositiveButton("Esci") { _, _ -> releaseAll(); finishAffinity() }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---------- tasti ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showMenu(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullscreenIndex >= 0) exitFullscreen() else confirmExit()
    }

    // ---------- ciclo di vita ----------

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(clockTick)
        players.forEach { it?.playWhenReady = false }
    }

    override fun onStart() {
        super.onStart()
        handler.post(clockTick)
        players.forEachIndexed { i, p ->
            if (p != null) { p.playWhenReady = true; scheduleRetry(i, 200) }
        }
    }

    override fun onDestroy() {
        sleepTimer?.cancel()
        releaseAll()
        super.onDestroy()
    }
}
