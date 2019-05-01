package dk.cs.aau.envue

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.*
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player.EventListener
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.SimpleExoPlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.google.gson.Gson
import dk.cs.aau.envue.communication.*
import dk.cs.aau.envue.communication.packets.MessagePacket
import dk.cs.aau.envue.communication.packets.ReactionPacket
import okhttp3.WebSocket


class PlayerActivity : AppCompatActivity(), EventListener, MessageListener, ReactionListener {
    override fun onMessage(message: Message) {
        runOnUiThread {
            addMessage(message)
            this.chatAdapter?.notifyDataSetChanged()
            scrollToBottom()
        }
    }

    override fun onReaction(reaction: String) {
        runOnUiThread {
            emojiFragment?.begin(reaction,this@PlayerActivity)
        }
    }

    private fun scrollToBottom() {
        this.chatAdapter?.itemCount?.let { this.chatList?.smoothScrollToPosition(it) }
    }

    private lateinit var broadcastId: String

    private var playerView: SimpleExoPlayerView? = null
    private var player: SimpleExoPlayer? = null
    private var editMessageView: EditText? = null
    private var chatList: RecyclerView? = null
    private var reactionList: RecyclerView? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private var loading: ProgressBar? = null
    private var chatAdapter: MessageListAdapter? = null
    private var reactionAdapter: ReactionListAdapter? = null
    private var socket: WebSocket? = null
    private var messages: ArrayList<Message> = ArrayList()
    private var emojiFragment: EmojiFragment? = null
    private var lastReactionAt: Long = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        broadcastId = savedInstanceState!!["broadcastId"] as String
        setContentView(R.layout.activity_player)
        // Prevent dimming
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create reaction adapter
        // TODO: Move to resources
        reactionAdapter = ReactionListAdapter(::addReaction, listOf("👍", "👎", "❤", "\uD83D\uDD25", "\uD83D\uDE02", "\uD83C\uDF46", "\uD83D\uDE20"))

        // Create chat adapter
        chatAdapter = MessageListAdapter(this, messages)

        // Initialize chat listener
        socket = StreamCommunicationListener.buildSocket(this, this)

        // Initialize player
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory(DefaultBandwidthMeter())
        player = ExoPlayerFactory.newSimpleInstance(
            DefaultRenderersFactory(this),
            DefaultTrackSelector(adaptiveTrackSelection)
        )

        val defaultBandwidthMeter = DefaultBandwidthMeter()
        // Produces DataSource instances through which media data is loaded.
        val dataSourceFactory = DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, "Exo2"), defaultBandwidthMeter
        )

        // Create media source
        val hlsUrl = "https://envue.me/relay/$broadcastId"  //TODO: Set this before onCreate()!
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

        bindContentView()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindContentView() {
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.video_view)
        loading = findViewById(R.id.loading)
        editMessageView = findViewById(R.id.editText)
        chatList = findViewById(R.id.chat_view)
        reactionList = findViewById(R.id.reaction_view)

        // Assign reaction adapter and layout manager
        val reactionLayoutManager = LinearLayoutManager(this).apply { orientation = 0}
        reactionList?.apply {
            adapter = reactionAdapter
            layoutManager = reactionLayoutManager
        }

        // Assign chat adapter and layout manager
        val chatLayoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatList?.apply {
            adapter = chatAdapter
            layoutManager = chatLayoutManager
        }
        //When in horizontal we want to be able to click through the recycler
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            exoPlayerViewOnTouch()
        }


        // Assign send button
        findViewById<Button>(R.id.button_chatbox_send)?.setOnClickListener {
            addLocalMessage()
        }

        // Creates fragments for EmojiReactionsFragment
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        emojiFragment = EmojiFragment()
        emojiFragment?.let {
            fragmentTransaction.replace(R.id.fragment_container, it)
            fragmentTransaction.commit()
        }

        // Assign player view
        player?.let { playerView?.player = it }

        // Update player state
        player?.let { onPlayerStateChanged(it.playWhenReady, it.playbackState) }

        // Ensure chat is scrolled to bottom
        this.scrollToBottom()
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun exoPlayerViewOnTouch() {
        var isPressed = true
        var startX = 0F
        var startY = 0F
        val exoPlayer = playerView
        val chatView = chatList
        exoPlayer?.setOnClickListener { }
        chatView?.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                //Log.e("Touch", "ACTION DOWN")
                startX = event.x
                startY = event.y

            } else if (event?.action == MotionEvent.ACTION_UP) {
                val endX = event.x
                val endY = event.y

                if (Math.abs(startX - endX) < 5 || Math.abs(startY- endY) < 5) {

                    if (isPressed) {
                        //Log.e("Press", "Pause")
                        exoPlayer?.controllerHideOnTouch = false
                        player?.playWhenReady = false
                        player?.playbackState
                        isPressed = false
                    } else {
                        //Log.e("Press", "Play")
                        exoPlayer?.controllerHideOnTouch = true
                        player?.playWhenReady = true
                        player?.playbackState
                        isPressed = true
                    }
                }
            }
            false
        }
    }

    private fun addReaction(reaction: String) {
        val timeSinceReaction = System.currentTimeMillis() - lastReactionAt

        if (timeSinceReaction >= 250) {
            onReaction(reaction)
            socket?.send(Gson().toJson(ReactionPacket(reaction)))
            lastReactionAt = System.currentTimeMillis()
        }
    }

    private fun addLocalMessage() {
        val messageView = findViewById<EditText>(R.id.editText)
        val text = messageView?.text.toString()
        if (!text.isEmpty()) {
            socket?.send(Gson().toJson(MessagePacket(text)))
            onMessage(Message(text))
            messageView.text.clear()
        }
    }

    private fun addMessage(message: Message) {
        this.messages.add(message)
    }

    override fun onPause() {
        super.onPause()
        player?.playWhenReady = false
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        socket?.close(StreamCommunicationListener.NORMAL_CLOSURE_STATUS, "Activity stopped")
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        bindContentView()
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            ExoPlayer.STATE_READY -> loading?.visibility = View.GONE
            ExoPlayer.STATE_BUFFERING -> loading?.visibility = View.VISIBLE
        }
    }
}
