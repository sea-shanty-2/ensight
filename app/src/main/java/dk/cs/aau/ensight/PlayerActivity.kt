package dk.cs.aau.ensight

import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.EventListener
import com.google.android.exoplayer2.ui.SimpleExoPlayerView
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.ExoPlayer
import dk.cs.aau.ensight.chat.ChatAdapter
import dk.cs.aau.ensight.chat.ChatListener
import dk.cs.aau.ensight.chat.Message
import dk.cs.aau.ensight.chat.MessageListener
import kotlinx.android.synthetic.main.activity_player.view.*
import okhttp3.WebSocket

class PlayerActivity : AppCompatActivity(), EventListener, MessageListener {
    override fun onMessage(message: String) {
        addMessage(Message(message, false))
    }

    private var playerView: SimpleExoPlayerView? = null
    private var player: SimpleExoPlayer? = null
    private var editMessageView: EditText? = null
    private var chatList: ListView? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var loading: ProgressBar? = null
    private var chatAdapter: ChatAdapter? = null
    private var socket: WebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        playerView = findViewById(R.id.video_view)
        loading = findViewById(R.id.loading)
        editMessageView = findViewById(R.id.editText)
        chatList = findViewById(R.id.chat_view)

        // Assign chat adapter
        chatAdapter = ChatAdapter(this)
        chatList?.adapter = chatAdapter

        // Listen for local messages
        findViewById<ImageButton>(R.id.sendMessage)?.setOnClickListener { addLocalMessage() }

        // Initialize chat listener
        socket = ChatListener.buildSocket(this)
    }

    private fun addLocalMessage() {
        val messageView = findViewById<EditText>(R.id.editText)
        val text = messageView?.text.toString()
        if (!text.isEmpty()) {
            socket?.send(text)
            addMessage(Message(text, true))
            messageView.text.clear()
        }
    }

    private fun addMessage(message: Message) {
        chatAdapter?.add(message)
        chatList?.setSelection(chatList?.let { it.count - 1 } ?: 0)
    }

    public override fun onStart() {
        super.onStart()

        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory(DefaultBandwidthMeter())
        player = ExoPlayerFactory.newSimpleInstance(DefaultRenderersFactory(this),
            DefaultTrackSelector(adaptiveTrackSelection))

        // Init the player
        player?.let { playerView?.player = it }

        val defaultBandwidthMeter = DefaultBandwidthMeter()
        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory = DefaultDataSourceFactory(this,
            Util.getUserAgent(this, "Exo2"), defaultBandwidthMeter)

        // Create media source
        val hlsUrl = "http://envue.me/live/ThomasAndersen.m3u8"
        val uri = Uri.parse(hlsUrl)
        val mainHandler = Handler()
        val mediaSource = HlsMediaSource(uri, dataSourceFactory, mainHandler, null)

        val listener = this
        player?.apply {
            seekTo(currentWindow, playbackPosition)
            prepare(mediaSource, true, false)
            addListener(listener)
            playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
    private fun releasePlayer() {
        player?.let {
            playbackPosition = it.currentPosition
            currentWindow = it.currentWindowIndex
            playWhenReady = it.playWhenReady
            it.release()
        }

        player = null
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            ExoPlayer.STATE_READY -> loading?.visibility = View.GONE
            ExoPlayer.STATE_BUFFERING -> loading?.visibility = View.VISIBLE
        }
    }
}
