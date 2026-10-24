package com.deactivated0.tv9

import MainViewModel
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private var ok = 0
    private var playerFragment = PlayerFragment()
    private val errorFragment = ErrorFragment()
    private val loadingFragment = LoadingFragment()
    private var infoFragment = InfoFragment()
    private var channelFragment = ChannelFragment()
    private var timeFragment = TimeFragment()
    private var menuFragment = MenuFragment()
    private var settingFragment = SettingFragment()

    private val handler = Handler(Looper.getMainLooper())
    private val delayHideMenu = 10 * 1000L
    private val delayHideSetting = 3 * 60 * 1000L

    private var doubleBackToExitPressedOnce = false
    private val resetBackRunnable = Runnable {
        doubleBackToExitPressedOnce = false
    }

    private lateinit var gestureDetector: GestureDetector

    private var server: SimpleServer? = null
    private var playFirstOnNextChannelLoad = false

    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Ensure edge-to-edge fullscreen across Android 4.0 (API 14) through Android 15/16/17 (API 35-37)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            windowInsetsController.let { controller ->
                controller.isAppearanceLightNavigationBars = !SP.darkMode
                controller.isAppearanceLightStatusBars = !SP.darkMode
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            window.navigationBarDividerColor = Color.TRANSPARENT
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) {
                // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS (value 3) is required for edge-to-edge on Android 15-17
                3
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.attributes = lp
        }

        @Suppress("DEPRECATION")
        window.decorView.apply {
            systemUiVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE
            } else {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        initChannelListeners()
        viewModel.init(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, playerFragment)
                .add(R.id.main_browse_fragment, errorFragment)
                .add(R.id.main_browse_fragment, loadingFragment)
                .add(R.id.main_browse_fragment, timeFragment)
                .add(R.id.main_browse_fragment, infoFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .add(R.id.main_browse_fragment, menuFragment)
                .add(R.id.main_browse_fragment, settingFragment)
                .hide(menuFragment)
                .hide(settingFragment)
                .hide(errorFragment)
                .hide(loadingFragment)
                .hide(timeFragment)
                .commitNow()
        }

        gestureDetector = GestureDetector(this, GestureListener(this))

        showTime()
    }

    fun applyTheme() {
        menuFragment.applyTheme()
        settingFragment.applyTheme()
        infoFragment.applyTheme()
        errorFragment.applyTheme()
    }

    fun applyAspectRatio() {
        playerFragment.applyAspectRatio()
    }

    fun applyResolutionConstraint() {
        playerFragment.applyResolutionConstraint()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null && intent.hasExtra("mode")) {
            val mode = intent.getIntExtra("mode", 0)
            SP.resolutionMode = mode
            playerFragment.recreatePlayerAndRetryCurrent(isForced = true)
        }
    }

    fun cycleAspectRatio(): String {
        return playerFragment.cycleAspectRatio()
    }

    fun cycleResolution(): String {
        return playerFragment.cycleResolution()
    }

    fun cycleSourceLine(): String {
        val current = viewModel.groupModel.getCurrent() ?: return "1/1"
        return if (current.nextVideoUri()) {
            val label = current.getSourceLineLabel()
            "Switched to Source Line $label".showToast()
            current.setReady()
            label
        } else {
            val label = current.getSourceLineLabel()
            "Only 1 source line ($label)".showToast()
            label
        }
    }

    fun cycleAudioTrack(): String {
        return playerFragment.cycleAudioTrack()
    }

    fun cycleSubtitleTrack(): String {
        return playerFragment.cycleSubtitleTrack()
    }

    fun updateDecoderMode() {
        playerFragment.updateDecoderMode()
    }

    fun onMetadataUpdated(metadata: PlayerFragment.Metadata) {
        infoFragment.updateMetadata(metadata)
    }

    fun update() {
        menuFragment.update()
    }

    fun updateMenuSize() {
        menuFragment.updateSize()
    }

    fun ready(tag: String) {
        Log.i(TAG, "ready $tag")
        ok++
        if (ok == 5) { // must keep it
            Log.i(TAG, "watch")
            viewModel.groupModel.change.observe(this) { _ ->
                Log.i(TAG, "groupModel changed")
                if (viewModel.groupModel.tvGroup.value != null) {
                    watch()
                    Log.i(TAG, "menuFragment update")
                    menuFragment.update()
                    if (playFirstOnNextChannelLoad) {
                        playFirstOnNextChannelLoad = false
                        playFirstAfterChannelLoad()
                    }
                }
            }

            viewModel.channelsOk.observe(this) { it ->
                if (it) {
                    val prevGroup = viewModel.groupModel.positionValue
                    Log.i(TAG, "SP.channel ${SP.channel}")
                    val tvModel = if (SP.defaultLike &&
                        (viewModel.groupModel.getFavoritesList()?.size() ?: 0) > 0
                    ) {
                        viewModel.groupModel.setPosition(0)
                        viewModel.groupModel.getFavoritesList()?.getTVModel(0)
                    } else if (SP.channel > 0) {
                        val position = if (SP.channel < viewModel.listModel.size) {
                            SP.channel - 1
                        } else {
                            SP.channel = 0
                            0
                        }
                        viewModel.groupModel.getPosition(position)
                    } else {
                        Log.i(TAG, "group ${viewModel.groupModel.positionValue}")
                        viewModel.groupModel.getCurrent()
                    }
                    tvModel?.setReady()
                    viewModel.groupModel.setPlaying()
                    viewModel.groupModel.getCurrentList()
                        ?.let {
                            Log.i(TAG, "list name ${it.getName()}")
                            it.setPlaying()
                        }

                    val currentGroup = viewModel.groupModel.positionValue
                    if (currentGroup != prevGroup) {
                        Log.i(TAG, "group change")
                        menuFragment.updateList(currentGroup)
                    }

                    viewModel.groupModel.isInLikeMode =
                        SP.defaultLike && viewModel.groupModel.positionValue == 0

                    viewModel.updateEPG()
                }
            }

            server = SimpleServer(this, viewModel)

            viewModel.updateConfig()
        }
    }

    fun setServer(server: String) {
        settingFragment.setServer(server)
    }

    private fun initChannelListeners() {
        com.deactivated0.tv9.models.TVModel.onChannelReady = { tvModel ->
            Log.i(TAG, "loading ${tvModel.tv.title}")
            hideFragment(errorFragment)
            showFragment(loadingFragment)
            playerFragment.play(tvModel)
            infoFragment.show(tvModel, playerFragment.metadata)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }

        com.deactivated0.tv9.models.TVModel.onChannelError = { tvModel, err ->
            hideFragment(loadingFragment)
            if (err == "") {
                Log.i(TAG, "${tvModel.tv.title} playing")
                hideFragment(errorFragment)
                showFragment(playerFragment)
            } else {
                Log.i(TAG, "${tvModel.tv.title} $err")
                hideFragment(playerFragment)
                errorFragment.setMsg(err)
                showFragment(errorFragment)
            }
        }

        com.deactivated0.tv9.models.TVModel.onLikeChanged = { tvModel, liked ->
            if (tvModel.tv.id != -1) {
                if (liked) {
                    viewModel.groupModel.getFavoritesList()?.replaceTVModel(tvModel)
                } else {
                    viewModel.groupModel.getFavoritesList()?.removeTVModel(tvModel.tv.id)
                }
                SP.setLike(tvModel.tv.id, liked)
            }
        }
    }

    private fun watch() {
        // Observers replaced by zero-allocation O(1) callbacks in TVModel
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private inner class GestureListener(private val context: Context) :
        GestureDetector.SimpleOnGestureListener() {

        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val screenWidth = resources.displayMetrics.widthPixels
        private val screenHeight = resources.displayMetrics.heightPixels

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            showFragment(menuFragment)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showSetting()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            Log.i(TAG, "onLongPress")
            cycleAspectRatio()
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val x = e1?.x ?: 0f
            if (x > screenWidth / 3 && x < screenWidth * 2 / 3) {
                if (velocityY > 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        prev()
                    }
                }
                if (velocityY < 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        next()
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

        private fun adjustVolume(deltaY: Float) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val deltaVolume = deltaY / 1000 * maxVolume / screenHeight

            var newVolume = currentVolume + deltaVolume
            if (newVolume < 0) {
                newVolume = 0F
            } else if (newVolume > maxVolume) {
                newVolume = maxVolume.toFloat()
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)

            Toast.makeText(context, "Volume: $newVolume / $maxVolume", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPlayEnd() {
        val tvModel = viewModel.groupModel.getCurrent() ?: return
        if (SP.repeatInfo) {
            infoFragment.show(tvModel, playerFragment.metadata)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }
    }

    fun playNextAfterPlaybackError(failedTvModel: com.deactivated0.tv9.models.TVModel?) {
        val list = getPlaybackLoopList() ?: return
        failedTvModel?.let { list.setPositionByTvId(it.tv.id) }
        val tvModel = list.getNext() ?: return
        playFromList(list, tvModel)
    }

    fun playFirstOnNextChannelLoad() {
        playFirstOnNextChannelLoad = true
    }

    fun playFirstAfterChannelLoad() {
        val list = getPlaybackLoopList() ?: return
        val tvModel = list.getTVModel(0) ?: return
        playFromList(list, tvModel)
    }

    private fun getPlaybackLoopList(): com.deactivated0.tv9.models.TVListModel? {
        val favorites = viewModel.groupModel.getFavoritesList()
        return if (SP.defaultLike && favorites != null && favorites.size() > 0) {
            favorites
        } else {
            viewModel.groupModel.getAllList()
        }
    }

    private fun playFromList(
        list: com.deactivated0.tv9.models.TVListModel,
        tvModel: com.deactivated0.tv9.models.TVModel
    ) {
        val prevGroup = viewModel.groupModel.positionValue
        viewModel.groupModel.setPosition(list.getGroupIndex())
        viewModel.groupModel.setPlaying()
        list.setPlaying()
        tvModel.setReady()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
        viewModel.groupModel.isInLikeMode = SP.defaultLike && currentGroup == 0
    }

    fun play(position: Int) {
        val allList = viewModel.groupModel.getAllList() ?: return
        if (position > -1 && position < allList.size()) {
            val prevGroup = viewModel.groupModel.positionValue
            val tvModel = viewModel.groupModel.getPosition(position)

            tvModel?.setReady()
            viewModel.groupModel.setPlaying()
            viewModel.groupModel.getCurrentList()?.setPlaying()

            val currentGroup = viewModel.groupModel.positionValue
            if (currentGroup != prevGroup) {
                menuFragment.updateList(currentGroup)
            }
        } else {
            R.string.channel_not_exist.showToast()
        }
    }

    fun prev() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel =
            if (SP.defaultLike && viewModel.groupModel.isInLikeMode && viewModel.groupModel.getFavoritesList() != null
            ) {
                viewModel.groupModel.getPrev(true)
            } else {
                viewModel.groupModel.getPrev()
            }

        tvModel?.setReady()
        viewModel.groupModel.setPlaying()
        viewModel.groupModel.getCurrentList()?.setPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    fun next() {
        val prevGroup = viewModel.groupModel.positionValue
        val tvModel =
            if (SP.defaultLike && viewModel.groupModel.isInLikeMode && viewModel.groupModel.getFavoritesList() != null
            ) {
                viewModel.groupModel.getNext(true)
            } else {
                viewModel.groupModel.getNext()
            }

        tvModel?.setReady()
        viewModel.groupModel.setPlaying()
        viewModel.groupModel.getCurrentList()?.setPlaying()

        val currentGroup = viewModel.groupModel.positionValue
        if (currentGroup != prevGroup) {
            menuFragment.updateList(currentGroup)
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitNow()
    }

    private fun hideFragment(fragment: Fragment) {
        if (fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitNow()
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!menuFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(menuFragment).commit()
            }
        }
    }

    fun settingActive() {
        handler.removeCallbacks(hideSetting)
        handler.postDelayed(hideSetting, delayHideSetting)
    }

    private val hideSetting = Runnable {
        val destroyed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
        if (!isFinishing && !destroyed && !supportFragmentManager.isDestroyed) {
            if (settingFragment.isAdded && !settingFragment.isHidden) {
                try {
                    supportFragmentManager.beginTransaction()
                        .hide(settingFragment)
                        .commitNow()
                    showTime()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun showTime() {
        if (SP.time) {
            showFragment(timeFragment)
        } else {
            hideFragment(timeFragment)
        }
    }

    private fun showChannel(channel: String) {
        if (!menuFragment.isHidden) {
            return
        }

        if (settingFragment.isVisible) {
            return
        }

        channelFragment.show(channel)
    }


    private fun channelUp() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                next()
                return
            }
            prev()
        }
    }

    private fun channelDown() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                prev()
                return
            }
            next()
        }
    }

    private fun back() {
        if (!menuFragment.isHidden) {
            hideMenuFragment()
            return
        }

        if (!settingFragment.isHidden) {
            hideSettingFragment()
            return
        }

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }

        doubleBackToExitPressedOnce = true
        R.string.press_again_to_exit.showToast()

        handler.removeCallbacks(resetBackRunnable)
        handler.postDelayed(resetBackRunnable, 2000)
    }

    private fun showSetting() {
        if (!menuFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(settingFragment)
            .commit()
        settingActive()
    }

    fun hideMenuFragment() {
        supportFragmentManager.beginTransaction()
            .hide(menuFragment)
            .commit()
    }

    private fun hideSettingFragment() {
        supportFragmentManager.beginTransaction()
            .hide(settingFragment)
            .commit()
        showTime()
    }

    fun onKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                showChannel("0")
                return true
            }

            KeyEvent.KEYCODE_1 -> {
                showChannel("1")
                return true
            }

            KeyEvent.KEYCODE_2 -> {
                showChannel("2")
                return true
            }

            KeyEvent.KEYCODE_3 -> {
                showChannel("3")
                return true
            }

            KeyEvent.KEYCODE_4 -> {
                showChannel("4")
                return true
            }

            KeyEvent.KEYCODE_5 -> {
                showChannel("5")
                return true
            }

            KeyEvent.KEYCODE_6 -> {
                showChannel("6")
                return true
            }

            KeyEvent.KEYCODE_7 -> {
                showChannel("7")
                return true
            }

            KeyEvent.KEYCODE_8 -> {
                showChannel("8")
                return true
            }

            KeyEvent.KEYCODE_9 -> {
                showChannel("9")
                return true
            }

            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BACK -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BOOKMARK,
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_HELP,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_MENU -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_GUIDE -> {
                viewModel.groupModel.getCurrent()?.let {
                    infoFragment.show(it, playerFragment.metadata)
                }
                return true
            }

            KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                cycleSubtitleTrack()
                return true
            }

            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
            KeyEvent.KEYCODE_PROG_GREEN -> {
                cycleAudioTrack()
                return true
            }

            KeyEvent.KEYCODE_TV_ZOOM_MODE,
            KeyEvent.KEYCODE_PROG_RED -> {
                cycleAspectRatio()
                return true
            }

            KeyEvent.KEYCODE_PROG_BLUE -> {
                cycleSourceLine()
                return true
            }

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                channelUp()
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                channelDown()
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (settingFragment.isHidden) {
                    showFragment(menuFragment)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showSetting()
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onKey(keyCode)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppLogger.w(TAG, "onTrimMemory level=$level")
        try {
            com.bumptech.glide.Glide.get(this).onTrimMemory(level)
            playerFragment.onTrimMemory(level)
        } catch (ignored: Throwable) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLogger.w(TAG, "onLowMemory triggered")
        try {
            com.bumptech.glide.Glide.get(this).onLowMemory()
            playerFragment.onLowMemory()
        } catch (ignored: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        server?.stop()
    }

    override fun attachBaseContext(base: Context) {
        val locale = Locale.ENGLISH
        val context = LocaleContextWrapper.wrap(base, locale)
        super.attachBaseContext(context)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
