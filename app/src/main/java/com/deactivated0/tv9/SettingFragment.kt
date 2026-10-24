package com.deactivated0.tv9

import MainViewModel
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.deactivated0.tv9.databinding.SettingBinding
import com.deactivated0.tv9.models.PlaylistEntry
import com.deactivated0.tv9.models.SourceType
import com.deactivated0.tv9.models.TV
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min


class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var uri: Uri

    private lateinit var updateManager: UpdateManager

    private var server = ""

    private lateinit var viewModel: MainViewModel

    private var btnAspectRatio: AppCompatButton? = null
    private var btnResolution: AppCompatButton? = null
    private var btnSourceLine: AppCompatButton? = null
    private var btnAudioTrack: AppCompatButton? = null
    private var btnSubtitleTrack: AppCompatButton? = null
    private var btnSearchKeypad: AppCompatButton? = null
    private var btnPlaylists: AppCompatButton? = null
    private var switchDarkMode: SwitchCompat? = null
    private var switchPreferSwDecoder: SwitchCompat? = null
    private var switchStreamHud: SwitchCompat? = null

    private val allButtons = ArrayList<AppCompatButton>(14)
    private val allSwitches = ArrayList<SwitchCompat>(14)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val application = requireActivity().applicationContext as MyTVApplication
        val context = requireContext()
        val mainActivity = (activity as MainActivity)

        _binding = SettingBinding.inflate(inflater, container, false)
        allButtons.clear()
        allSwitches.clear()

        binding.versionName.text = "v${context.appVersionName} by @deactivated0"
        binding.version.text = "https://github.com/deactivated0"

        val switchChannelReversal = _binding?.switchChannelReversal
        switchChannelReversal?.isChecked = SP.channelReversal
        switchChannelReversal?.setOnCheckedChangeListener { _, isChecked ->
            SP.channelReversal = isChecked
            mainActivity.settingActive()
        }

        val switchChannelNum = _binding?.switchChannelNum
        switchChannelNum?.isChecked = SP.channelNum
        switchChannelNum?.setOnCheckedChangeListener { _, isChecked ->
            SP.channelNum = isChecked
            mainActivity.settingActive()
        }

        val switchTime = _binding?.switchTime
        switchTime?.isChecked = SP.time
        switchTime?.setOnCheckedChangeListener { _, isChecked ->
            SP.time = isChecked
            mainActivity.settingActive()
        }

        val switchBootStartup = _binding?.switchBootStartup
        switchBootStartup?.isChecked = SP.bootStartup
        switchBootStartup?.setOnCheckedChangeListener { _, isChecked ->
            SP.bootStartup = isChecked
            mainActivity.settingActive()
        }

        val switchRepeatInfo = _binding?.switchRepeatInfo
        switchRepeatInfo?.isChecked = SP.repeatInfo
        switchRepeatInfo?.setOnCheckedChangeListener { _, isChecked ->
            SP.repeatInfo = isChecked
            mainActivity.settingActive()
        }

        val switchConfigAutoLoad = _binding?.switchConfigAutoLoad
        switchConfigAutoLoad?.isChecked = SP.configAutoLoad
        switchConfigAutoLoad?.setOnCheckedChangeListener { _, isChecked ->
            SP.configAutoLoad = isChecked
            mainActivity.settingActive()
        }

        val switchDefaultLike = _binding?.switchDefaultLike
        switchDefaultLike?.isChecked = SP.defaultLike
        switchDefaultLike?.setOnCheckedChangeListener { _, isChecked ->
            SP.defaultLike = isChecked
            mainActivity.settingActive()
        }

        val switchShowAllChannels = _binding?.switchShowAllChannels
        switchShowAllChannels?.isChecked = SP.showAllChannels

        val switchCompactMenu = _binding?.switchCompactMenu
        switchCompactMenu?.isChecked = SP.compactMenu
        switchCompactMenu?.setOnCheckedChangeListener { _, isChecked ->
            SP.compactMenu = isChecked
            mainActivity.updateMenuSize()
            mainActivity.settingActive()
        }

        val switchDisplaySeconds = _binding?.switchDisplaySeconds
        switchDisplaySeconds?.isChecked = SP.displaySeconds

        binding.qrcode.setOnClickListener {
            val imageModalFragment = ModalFragment()
            val metrics = resources.displayMetrics
            val size = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.6f).toInt()
                .coerceIn(Utils.dpToPx(160), Utils.dpToPx(260))
            val img = QrCodeUtil().createQRCodeBitmap(server, size, size)
            val args = Bundle()
            args.putParcelable(ModalFragment.KEY_BITMAP, img)
            args.putString(ModalFragment.KEY_TEXT, server)
            imageModalFragment.arguments = args

            imageModalFragment.show(requireFragmentManager(), ModalFragment.TAG)
            mainActivity.settingActive()
        }

        binding.checkVersion.setOnClickListener {
            requestInstallPermissions()
            mainActivity.settingActive()
        }

        binding.confirmConfig.setOnClickListener {
            showPlaylistManagerDialog()
            mainActivity.settingActive()
        }

        binding.setting.setOnClickListener {
            hideSelf()
        }

        binding.exit.setOnClickListener {
            ActivityCompat.finishAffinity(requireActivity())
        }

        val txtTextSize = application.px2PxFont(binding.versionName.textSize)

        binding.content.layoutParams.width =
            application.px2Px(binding.content.layoutParams.width)
        binding.content.setPadding(
            application.px2Px(binding.content.paddingLeft),
            application.px2Px(binding.content.paddingTop),
            application.px2Px(binding.content.paddingRight),
            application.px2Px(binding.content.paddingBottom)
        )

        binding.name.textSize = application.px2PxFont(binding.name.textSize)
        binding.version.textSize = txtTextSize
        val layoutParamsVersion = binding.version.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsVersion.topMargin = application.px2Px(binding.version.marginTop)
        layoutParamsVersion.bottomMargin = application.px2Px(binding.version.marginBottom)
        binding.version.layoutParams = layoutParamsVersion

        binding.versionName.textSize = txtTextSize

        // Wire action button rows for Playlists, Aspect Ratio, Source Line, Audio Track, Subtitles, and Search/Keypad
        btnAspectRatio = binding.btnAspectRatio.apply {
            text = "Ratio: ${SP.getAspectRatioLabel()}"
            isAllCaps = false
            setOnClickListener {
                val newLabel = mainActivity.cycleAspectRatio()
                text = "Ratio: $newLabel"
                mainActivity.settingActive()
            }
        }
        btnResolution = binding.btnResolution.apply {
            text = "Res: ${SP.getResolutionLabel()}"
            isAllCaps = false
            setOnClickListener {
                val newLabel = mainActivity.cycleResolution()
                text = "Res: $newLabel"
                mainActivity.settingActive()
            }
        }
        btnSourceLine = binding.btnSourceLine.apply {
            text = getString(R.string.switch_source)
            isAllCaps = false
            setOnClickListener {
                val lineLabel = mainActivity.cycleSourceLine()
                text = "Line: $lineLabel"
                mainActivity.settingActive()
            }
        }
        btnSearchKeypad = binding.btnSearchKeypad.apply {
            text = getString(R.string.search_channel)
            isAllCaps = false
            setOnClickListener {
                showSearchAndKeypadDialog()
                mainActivity.settingActive()
            }
        }

        btnPlaylists = binding.btnPlaylists.apply {
            text = getString(R.string.playlists_manager)
            isAllCaps = false
            setOnClickListener {
                showPlaylistManagerDialog()
                mainActivity.settingActive()
            }
        }
        btnAudioTrack = binding.btnAudioTrack.apply {
            text = getString(R.string.audio_track)
            isAllCaps = false
            setOnClickListener {
                mainActivity.cycleAudioTrack()
                mainActivity.settingActive()
            }
        }
        btnSubtitleTrack = binding.btnSubtitleTrack.apply {
            text = getString(R.string.subtitle_track)
            isAllCaps = false
            setOnClickListener {
                mainActivity.cycleSubtitleTrack()
                mainActivity.settingActive()
            }
        }

        switchDarkMode = binding.switchDarkMode.apply {
            isChecked = SP.darkMode
            setOnCheckedChangeListener { _, isChecked ->
                SP.darkMode = isChecked
                mainActivity.applyTheme()
                mainActivity.settingActive()
            }
        }

        switchPreferSwDecoder = binding.switchPreferSwDecoder.apply {
            isChecked = SP.preferSoftwareDecoder
            setOnCheckedChangeListener { _, isChecked ->
                SP.preferSoftwareDecoder = isChecked
                mainActivity.updateDecoderMode()
                mainActivity.settingActive()
            }
        }

        switchStreamHud = binding.switchStreamHud.apply {
            isChecked = SP.showStreamHud
            setOnCheckedChangeListener { _, isChecked ->
                SP.showStreamHud = isChecked
                mainActivity.settingActive()
            }
        }

        allButtons.clear()
        allButtons.addAll(
            listOf(
                binding.qrcode,
                binding.confirmConfig,
                binding.clear,
                binding.checkVersion,
                binding.btnSearchKeypad,
                binding.exit,
                binding.btnAspectRatio,
                binding.btnResolution,
                binding.btnSourceLine,
                binding.btnPlaylists,
                binding.btnAudioTrack,
                binding.btnSubtitleTrack,
            )
        )
        val btnPadH = application.px2Px(Utils.dpToPx(2))
        for (btn in allButtons) {
            btn.isAllCaps = false
            btn.background = ThemeManager.buttonNormalDrawable
            btn.setTextColor(ThemeManager.buttonNormalTextColor)
            btn.minWidth = 0
            btn.minHeight = 0
            btn.setPadding(btnPadH, 0, btnPadH, 0)
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            btn.setOnFocusChangeListener { v, hasFocus ->
                val b = v as AppCompatButton
                if (hasFocus) {
                    b.background = ThemeManager.focusDrawable
                    b.setTextColor(ThemeManager.focusTextColor)
                } else {
                    b.background = ThemeManager.buttonNormalDrawable
                    b.setTextColor(ThemeManager.buttonNormalTextColor)
                }
            }
        }

        val textSizeSwitch = application.px2PxFont(binding.switchChannelReversal.textSize)
        val switchTopMargin = application.px2Px(binding.switchChannelReversal.marginTop)
        val switchPadV = application.px2Px(Utils.dpToPx(4))

        val thumbStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val thumbColors = intArrayOf(ThemeManager.COLOR_WHITE, 0xFF888888.toInt())
        val thumbTintList = ColorStateList(thumbStates, thumbColors)

        val trackStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val trackColors = intArrayOf(0x99FFFFFF.toInt(), 0x33FFFFFF.toInt())
        val trackTintList = ColorStateList(trackStates, trackColors)

        allSwitches.clear()
        allSwitches.addAll(
            listOf(
                binding.switchChannelReversal,
                binding.switchChannelNum,
                binding.switchTime,
                binding.switchBootStartup,
                binding.switchRepeatInfo,
                binding.switchConfigAutoLoad,
                binding.switchDefaultLike,
                binding.switchShowAllChannels,
                binding.switchCompactMenu,
                binding.switchDisplaySeconds,
                binding.switchDarkMode,
                binding.switchPreferSwDecoder,
                binding.switchStreamHud,
            )
        )
        for (sw in allSwitches) {
            val lp = (sw.layoutParams as? ViewGroup.MarginLayoutParams)
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.topMargin = switchTopMargin
            sw.layoutParams = lp
            sw.textSize = textSizeSwitch
            sw.setPadding(0, switchPadV, 0, switchPadV)
            sw.setThumbTintList(thumbTintList)
            sw.setTrackTintList(trackTintList)
            sw.setTextColor(ThemeManager.titleColor)
            sw.setOnFocusChangeListener { v, hasFocus ->
                (v as SwitchCompat).setTextColor(
                    if (hasFocus) ThemeManager.focusColor else ThemeManager.titleColor
                )
            }
        }

        applyTheme()
        updateManager = UpdateManager(context, context.appVersionCode)

        return binding.root
    }

    fun applyTheme() {
        val b = _binding ?: return
        b.content.background = ThemeManager.surfaceDrawable
        b.name.setTextColor(ThemeManager.titleColor)
        b.versionName.setTextColor(ThemeManager.descriptionColor)
        b.version.setTextColor(ThemeManager.titleColor)

        val thumbStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val thumbColors = intArrayOf(ThemeManager.COLOR_WHITE, 0xFF888888.toInt())
        val currentThumbTint = ColorStateList(thumbStates, thumbColors)

        val trackStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val trackColors = if (SP.darkMode) {
            intArrayOf(0x99FFFFFF.toInt(), 0x33FFFFFF.toInt())
        } else {
            intArrayOf(0xBB000000.toInt(), 0x33000000.toInt())
        }
        val currentTrackTint = ColorStateList(trackStates, trackColors)

        for (btn in allButtons) {
            if (btn.hasFocus()) {
                btn.background = ThemeManager.focusDrawable
                btn.setTextColor(ThemeManager.focusTextColor)
            } else {
                btn.background = ThemeManager.buttonNormalDrawable
                btn.setTextColor(ThemeManager.buttonNormalTextColor)
            }
        }
        for (sw in allSwitches) {
            sw.setThumbTintList(currentThumbTint)
            sw.setTrackTintList(currentTrackTint)
            sw.setTextColor(
                if (sw.hasFocus()) ThemeManager.focusColor else ThemeManager.titleColor
            )
        }
        switchDarkMode?.let {
            if (it.isChecked != SP.darkMode) {
                it.isChecked = SP.darkMode
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireActivity()
        val mainActivity = (activity as MainActivity)
        viewModel = ViewModelProvider(context)[MainViewModel::class.java]

        binding.switchDisplaySeconds.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDisplaySeconds(isChecked)
        }

        binding.clear.setOnClickListener {
            SP.positionGroup = SP.DEFAULT_POSITION_GROUP
            viewModel.groupModel.setPosition(SP.DEFAULT_POSITION_GROUP)
            viewModel.groupModel.setPositionPlaying(SP.DEFAULT_POSITION_GROUP)

            SP.position = SP.DEFAULT_POSITION
            val tvListModel = viewModel.groupModel.getCurrentList()
            tvListModel?.setPosition(SP.DEFAULT_POSITION)
            tvListModel?.setPositionPlaying(SP.DEFAULT_POSITION)

            SP.config = null
            SP.channelListJson = ""
            context.deleteFile(viewModel.FILE_NAME)
            viewModel.reset(context)

            SP.channel = SP.DEFAULT_CHANNEL
            confirmChannel()

            SP.deleteLike()
            SP.position = 0
            SP.showAllChannels = SP.DEFAULT_SHOW_ALL_CHANNELS
            SP.compactMenu = SP.DEFAULT_COMPACT_MENU
            SP.darkMode = SP.DEFAULT_DARK_MODE
            SP.aspectRatioMode = SP.DEFAULT_ASPECT_RATIO_MODE
            SP.resolutionMode = SP.DEFAULT_RESOLUTION_MODE
            SP.preferSoftwareDecoder = SP.DEFAULT_PREFER_SOFTWARE_DECODER
            SP.showStreamHud = SP.DEFAULT_SHOW_STREAM_HUD

            switchDarkMode?.isChecked = SP.DEFAULT_DARK_MODE
            switchPreferSwDecoder?.isChecked = SP.DEFAULT_PREFER_SOFTWARE_DECODER
            switchStreamHud?.isChecked = SP.DEFAULT_SHOW_STREAM_HUD
            btnAspectRatio?.text = "Ratio: ${SP.getAspectRatioLabel()}"
            btnResolution?.text = "Res: ${SP.getResolutionLabel()}"
            mainActivity.applyTheme()
            mainActivity.applyAspectRatio()
            mainActivity.applyResolutionConstraint()

            viewModel.setDisplaySeconds(SP.DEFAULT_DISPLAY_SECONDS)

            SP.epg = SP.DEFAULT_EPG
            viewModel.updateEPG()

            R.string.config_restored.showToast()
        }

        binding.switchShowAllChannels.setOnCheckedChangeListener { _, isChecked ->
            SP.showAllChannels = isChecked
            viewModel.groupModel.tvGroup.value?.let { viewModel.groupModel.setTVListModelList(it) }
            mainActivity.update()
            mainActivity.settingActive()
        }
    }

    /**
     * Local On-Device Playlist & Channel Manager Dialog:
     * Allows users to Add, Edit, Delete, Switch/Load playlists (via URL, local file path/picker, or pasted M3U/TXT/JSON)
     * as well as add custom channels directly on the device without needing another phone/browser.
     */
    private fun applyDialogButtonFocus(btn: AppCompatButton) {
        btn.isFocusable = true
        btn.isFocusableInTouchMode = true
        btn.background = ThemeManager.buttonNormalDrawable
        btn.setTextColor(ThemeManager.buttonNormalTextColor)
        btn.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                btn.background = ThemeManager.focusDrawable
                btn.setTextColor(ThemeManager.focusTextColor)
            } else {
                btn.background = ThemeManager.buttonNormalDrawable
                btn.setTextColor(ThemeManager.buttonNormalTextColor)
            }
        }
    }

    fun showPlaylistManagerDialog() {
        val context = requireContext()
        val mainActivity = activity as? MainActivity ?: return
        val pad = Utils.dpToPx(12)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.surfaceColor)
        }

        val headerText = TextView(context).apply {
            text = "Playlists & Local Configuration"
            textSize = 16f
            setTextColor(ThemeManager.titleColor)
        }
        root.addView(headerText)

        // Top action buttons: [+ Add Playlist], [+ Add Channel], [Import File]
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = Utils.dpToPx(8)
            layoutParams = lp
        }

        var parentDialog: AlertDialog? = null

        val btnAddPlaylist = AppCompatButton(context).apply {
            text = "+ Add Playlist"
            textSize = 12f
            isAllCaps = false
            background = ThemeManager.buttonNormalDrawable
            setTextColor(ThemeManager.buttonNormalTextColor)
            setOnClickListener {
                parentDialog?.dismiss()
                showEditPlaylistDialog(null)
            }
        }
        val btnAddChannel = AppCompatButton(context).apply {
            text = "+ Add Channel"
            textSize = 12f
            isAllCaps = false
            background = ThemeManager.buttonNormalDrawable
            setTextColor(ThemeManager.buttonNormalTextColor)
            setOnClickListener {
                parentDialog?.dismiss()
                showAddChannelDialog()
            }
        }
        val btnPickFile = AppCompatButton(context).apply {
            text = "📁 Import Local File"
            textSize = 12f
            isAllCaps = false
            background = ThemeManager.buttonNormalDrawable
            setTextColor(ThemeManager.buttonNormalTextColor)
            setOnClickListener {
                parentDialog?.dismiss()
                showLocalFileChooserDialog { pickedFile ->
                    val entry = PlaylistEntry(
                        id = "pl_${System.currentTimeMillis()}",
                        name = pickedFile.nameWithoutExtension,
                        url = "file://${pickedFile.absolutePath}",
                        content = try { pickedFile.readText(Charsets.UTF_8) } catch (_: Exception) { "" },
                        enabled = true
                    )
                    SP.savePlaylist(entry)
                    mainActivity.playFirstOnNextChannelLoad()
                    viewModel.loadPlaylist(entry)
                }
            }
        }

        applyDialogButtonFocus(btnAddPlaylist)
        applyDialogButtonFocus(btnAddChannel)
        applyDialogButtonFocus(btnPickFile)

        val topBtnLp = LinearLayout.LayoutParams(0, Utils.dpToPx(38), 1f).apply {
            rightMargin = Utils.dpToPx(4)
        }
        val lastBtnLp = LinearLayout.LayoutParams(0, Utils.dpToPx(38), 1f)

        actionRow.addView(btnAddPlaylist, topBtnLp)
        actionRow.addView(btnAddChannel, topBtnLp)
        actionRow.addView(btnPickFile, lastBtnLp)
        root.addView(actionRow)

        val playlists = ArrayList(SP.getPlaylists())
        val labels = ArrayList<String>(playlists.size)
        val activeId = SP.activePlaylistId
        for (p in playlists) {
            val status = if (p.id == activeId) " [ACTIVE]" else ""
            val src = if (p.url.isNotBlank()) p.url else "Inline Content"
            labels.add("${p.name}$status — $src")
        }

        if (playlists.isEmpty()) {
            val emptyView = TextView(context).apply {
                text = "No saved playlists yet. Tap '+ Add Playlist' to add a URL or paste M3U/TXT/JSON content, '+ Add Channel' to add a single stream, or 'Import Local File' to load a file from device storage."
                textSize = 13f
                setTextColor(ThemeManager.descriptionColor)
                setPadding(0, Utils.dpToPx(12), 0, Utils.dpToPx(12))
            }
            root.addView(emptyView)
        } else {
            val hintView = TextView(context).apply {
                text = "Tap a playlist to Load, Edit, or Delete:"
                textSize = 12f
                setTextColor(ThemeManager.descriptionColor)
                setPadding(0, Utils.dpToPx(6), 0, Utils.dpToPx(4))
            }
            root.addView(hintView)

            val listView = ListView(context).apply {
                selector = ThemeManager.focusDrawable
            }
            val adapter = object : ArrayAdapter<String>(
                context,
                android.R.layout.simple_list_item_1,
                labels
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.apply {
                        setTextColor(ThemeManager.titleColor)
                        setPadding(Utils.dpToPx(8), Utils.dpToPx(8), Utils.dpToPx(8), Utils.dpToPx(8))
                    }
                    return v
                }
            }
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, position, _ ->
                if (position in 0 until playlists.size) {
                    val selected = playlists[position]
                    parentDialog?.dismiss()
                    AlertDialog.Builder(context)
                        .setTitle(selected.name)
                        .setItems(arrayOf("Load / Activate Playlist", "Edit Playlist", "Delete Playlist")) { _, which ->
                            when (which) {
                                0 -> {
                                    mainActivity.playFirstOnNextChannelLoad()
                                    viewModel.loadPlaylist(selected)
                                }
                                1 -> showEditPlaylistDialog(selected)
                                2 -> {
                                    val wasActive = SP.activePlaylistId == selected.id
                                    SP.deletePlaylist(selected.id)
                                    if (wasActive) {
                                        val nextActive = SP.getPlaylistById(SP.activePlaylistId)
                                        if (nextActive != null) {
                                            mainActivity.playFirstOnNextChannelLoad()
                                            viewModel.loadPlaylist(nextActive)
                                        } else {
                                            viewModel.reset(context)
                                        }
                                    }
                                    "Playlist deleted".showToast()
                                    showPlaylistManagerDialog()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            root.addView(
                listView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dpToPx(200)
                )
            )
        }

        parentDialog = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        parentDialog.setOnShowListener {
            (parentDialog?.getButton(AlertDialog.BUTTON_NEGATIVE) as? AppCompatButton)?.let { btn ->
                applyDialogButtonFocus(btn)
            }
        }
        parentDialog.show()
    }

    private fun showEditPlaylistDialog(existing: PlaylistEntry?) {
        val context = requireContext()
        val mainActivity = activity as? MainActivity ?: return
        val pad = Utils.dpToPx(12)

        val scroll = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.surfaceColor)
        }
        scroll.addView(layout)

        val nameInput = EditText(context).apply {
            hint = "Playlist Name (e.g. My IPTV)"
            setSingleLine(true)
            setText(existing?.name ?: "")
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }
        val urlInput = EditText(context).apply {
            hint = "Playlist URL (http://... or file:///sdcard/...)"
            setSingleLine(true)
            setText(existing?.url ?: "")
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }

        val contentInput = EditText(context).apply {
            hint = "Or Paste M3U / #genre# TXT / JSON Content..."
            minLines = 4
            maxLines = 8
            setText(existing?.content ?: "")
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }

        val btnFileRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = Utils.dpToPx(6)
                bottomMargin = Utils.dpToPx(6)
            }
            layoutParams = lp
        }

        val btnChooseFile = AppCompatButton(context).apply {
            text = "📁 Choose Local File (Browse Storage)"
            textSize = 12f
            isAllCaps = false
            setOnClickListener {
                showLocalFileChooserDialog { pickedFile ->
                    urlInput.setText("file://${pickedFile.absolutePath}")
                    if (nameInput.text.isNullOrBlank()) {
                        nameInput.setText(pickedFile.nameWithoutExtension)
                    }
                    try {
                        val text = pickedFile.readText(Charsets.UTF_8)
                        contentInput.setText(text)
                    } catch (_: Exception) {
                        try {
                            contentInput.setText(pickedFile.readText(Charsets.ISO_8859_1))
                        } catch (_: Exception) {}
                    }
                    "Selected: ${pickedFile.name}".showToast()
                }
            }
        }
        applyDialogButtonFocus(btnChooseFile)
        btnFileRow.addView(
            btnChooseFile,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dpToPx(38)
            )
        )

        layout.addView(nameInput)
        layout.addView(urlInput)
        layout.addView(btnFileRow)
        layout.addView(contentInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existing == null) "Add Playlist" else "Edit Playlist")
            .setView(scroll)
            .setPositiveButton("Save & Load") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val url = urlInput.text?.toString()?.trim().orEmpty()
                val content = contentInput.text?.toString()?.trim().orEmpty()
                if (url.isEmpty() && content.isEmpty()) {
                    R.string.invalid_config_address.showToast()
                    return@setPositiveButton
                }
                val saved = SP.savePlaylist(
                    PlaylistEntry(
                        id = existing?.id ?: "pl_${System.currentTimeMillis()}",
                        name = name.ifEmpty { url.substringAfterLast('/').ifEmpty { "Local Playlist" } },
                        url = url,
                        content = content,
                        enabled = true
                    )
                )
                mainActivity.playFirstOnNextChannelLoad()
                viewModel.loadPlaylist(saved)
            }
            .setNeutralButton("Save Only") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val url = urlInput.text?.toString()?.trim().orEmpty()
                val content = contentInput.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty() || content.isNotEmpty()) {
                    SP.savePlaylist(
                        PlaylistEntry(
                            id = existing?.id ?: "pl_${System.currentTimeMillis()}",
                            name = name.ifEmpty { url.substringAfterLast('/').ifEmpty { "Local Playlist" } },
                            url = url,
                            content = content,
                            enabled = true
                        )
                    )
                    "Playlist saved".showToast()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            listOf(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ).forEach { b ->
                if (b is AppCompatButton) applyDialogButtonFocus(b)
            }
        }
        dialog.show()
    }

    private fun showLocalFileChooserDialog(onFilePicked: (File) -> Unit) {
        val context = requireContext()
        val pad = Utils.dpToPx(12)

        val initialDir = sequenceOf(
            File("/mnt/Emulator"),
            Environment.getExternalStorageDirectory(),
            File("/sdcard"),
            File("/storage/emulated/0"),
            File("/mnt/sdcard")
        ).firstOrNull { it.exists() && it.canRead() } ?: File("/")

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.surfaceColor)
        }

        val pathHeader = TextView(context).apply {
            textSize = 12f
            setTextColor(ThemeManager.descriptionColor)
            setPadding(0, 0, 0, Utils.dpToPx(8))
        }
        dialogView.addView(pathHeader)

        val listView = ListView(context).apply {
            divider = null
            selector = ThemeManager.focusDrawable
        }
        dialogView.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dpToPx(240)
            )
        )

        val btnSaf = AppCompatButton(context).apply {
            text = "Use System File Picker (SAF)"
            textSize = 12f
            isAllCaps = false
        }
        applyDialogButtonFocus(btnSaf)
        dialogView.addView(
            btnSaf,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Utils.dpToPx(8) }
        )

        var chooserDialog: AlertDialog? = null

        fun refresh(dir: File) {
            pathHeader.text = "Dir: ${dir.absolutePath}"
            val filesList = ArrayList<File>()
            val labels = ArrayList<String>()

            val parent = dir.parentFile
            if (parent != null && parent.canRead()) {
                filesList.add(parent)
                labels.add("📁 .. [Up one level]")
            }

            val entries = dir.listFiles()?.sortedWith(
                compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
            ) ?: emptyList()

            for (f in entries) {
                if (f.name.startsWith(".")) continue
                filesList.add(f)
                val ext = f.extension.lowercase()
                val prefix = if (f.isDirectory) "📁 " else if (ext in listOf("m3u", "m3u8", "txt", "json")) "📺 " else "📄 "
                labels.add("$prefix${f.name}")
            }

            listView.adapter = object : ArrayAdapter<String>(
                context,
                android.R.layout.simple_list_item_1,
                labels
            ) {
                override fun getView(position: Int, convertView: View?, parentViewGroup: ViewGroup): View {
                    val v = super.getView(position, convertView, parentViewGroup)
                    (v as? TextView)?.apply {
                        setTextColor(ThemeManager.titleColor)
                        textSize = 13f
                    }
                    return v
                }
            }

            listView.setOnItemClickListener { _, _, position, _ ->
                if (position in 0 until filesList.size) {
                    val picked = filesList[position]
                    if (picked.isDirectory) {
                        refresh(picked)
                    } else {
                        chooserDialog?.dismiss()
                        onFilePicked(picked)
                    }
                }
            }
        }

        btnSaf.setOnClickListener {
            chooserDialog?.dismiss()
            openLocalFilePicker()
        }

        refresh(initialDir)

        chooserDialog = AlertDialog.Builder(context)
            .setTitle("Select Playlist File")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()
        chooserDialog?.setOnShowListener {
            (chooserDialog?.getButton(AlertDialog.BUTTON_NEGATIVE) as? AppCompatButton)?.let { b ->
                applyDialogButtonFocus(b)
            }
        }
        chooserDialog?.show()
    }

    private fun showAddChannelDialog() {
        val context = requireContext()
        val pad = Utils.dpToPx(12)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.surfaceColor)
        }
        val titleInput = EditText(context).apply {
            hint = "Channel Name (e.g. Sports 1)"
            setSingleLine(true)
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }
        val groupInput = EditText(context).apply {
            hint = "Group Name (e.g. Sports)"
            setSingleLine(true)
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }
        val uriInput = EditText(context).apply {
            hint = "Stream URL(s), comma-separated"
            setSingleLine(true)
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }
        layout.addView(titleInput)
        layout.addView(groupInput)
        layout.addView(uriInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Add Custom Channel")
            .setView(layout)
            .setPositiveButton("Add & Play") { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val group = groupInput.text?.toString()?.trim().orEmpty().ifEmpty { "Custom" }
                val uris = uriInput.text?.toString()?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                if (title.isEmpty() || uris.isEmpty()) {
                    R.string.invalid_config_address.showToast()
                    return@setPositiveButton
                }
                val currentList = viewModel.listModel.map { it.tv }.toMutableList()
                currentList.add(
                    TV(
                        id = currentList.size,
                        name = title,
                        title = title,
                        description = "",
                        logo = "",
                        image = "",
                        uris = uris,
                        headers = emptyMap(),
                        group = group,
                        sourceType = SourceType.UNKNOWN,
                        child = emptyList()
                    )
                )
                val json = Utils.gson.toJson(currentList)
                if (viewModel.str2List(json)) {
                    File(context.filesDir, viewModel.FILE_NAME).writeText(json)
                    (activity as? MainActivity)?.play(currentList.size - 1)
                    R.string.channel_import_success.showToast()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            listOf(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ).forEach { b ->
                if (b is AppCompatButton) applyDialogButtonFocus(b)
            }
        }
        dialog.show()
    }

    private fun openLocalFilePicker() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            } else {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            }
            startActivityForResult(intent, PICK_PLAYLIST_FILE_REQUEST_CODE)
        } catch (e: Exception) {
            // Fallback for minimal TV ROMs without DocumentsUI: prompt for local file path
            showEditPlaylistDialog(PlaylistEntry(name = "Local File", url = "file:///sdcard/playlist.m3u"))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PLAYLIST_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val fileUri = data?.data ?: return
            try {
                val content = requireContext().contentResolver.openInputStream(fileUri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val saved = SP.savePlaylist(
                        PlaylistEntry(
                            id = "pl_${System.currentTimeMillis()}",
                            name = fileUri.lastPathSegment?.substringAfterLast('/') ?: "Local File",
                            url = fileUri.toString(),
                            content = content,
                            enabled = true
                        )
                    )
                    (activity as? MainActivity)?.playFirstOnNextChannelLoad()
                    viewModel.loadPlaylist(saved)
                } else {
                    R.string.channel_read_error.showToast()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                R.string.channel_read_error.showToast()
            }
        }
    }

    /**
     * Opens a lightweight, zero-XML Quick Channel Search & On-Screen Keypad dialog
     * for touchscreens and mini-remotes without physical 0-9 number buttons.
     */
    private fun showSearchAndKeypadDialog() {
        val context = requireContext()
        val mainActivity = activity as? MainActivity ?: return
        val allChannels = viewModel.listModel

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = Utils.dpToPx(12)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ThemeManager.surfaceColor)
        }

        val searchInput = EditText(context).apply {
            hint = "Enter channel # or name..."
            setSingleLine(true)
            setTextColor(ThemeManager.titleColor)
            setHintTextColor(ThemeManager.descriptionColor)
        }
        root.addView(
            searchInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val keypadRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫")
        for (k in keys) {
            val kb = AppCompatButton(context).apply {
                text = k
                textSize = 12f
                isAllCaps = false
                background = ThemeManager.buttonNormalDrawable
                setTextColor(ThemeManager.buttonNormalTextColor)
                val p = Utils.dpToPx(2)
                setPadding(p, p, p, p)
                setOnClickListener {
                    val cur = searchInput.text?.toString() ?: ""
                    if (k == "⌫") {
                        if (cur.isNotEmpty()) {
                            searchInput.setText(cur.substring(0, cur.length - 1))
                            searchInput.setSelection(searchInput.text.length)
                        }
                    } else {
                        searchInput.append(k)
                    }
                }
            }
            keypadRow.addView(
                kb,
                LinearLayout.LayoutParams(0, Utils.dpToPx(36), 1f).apply {
                    rightMargin = Utils.dpToPx(2)
                }
            )
        }
        root.addView(
            keypadRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val filteredModels = ArrayList<com.deactivated0.tv9.models.TVModel>(allChannels.size)
        val displayLabels = ArrayList<String>(allChannels.size)
        for (m in allChannels) {
            filteredModels.add(m)
            displayLabels.add("${m.tv.id + 1}. ${m.tv.title} (${m.tv.group})")
        }

        val listView = ListView(context)
        val adapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_list_item_1,
            displayLabels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(ThemeManager.titleColor)
                return v
            }
        }
        listView.adapter = adapter
        root.addView(
            listView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dpToPx(220)
            )
        )

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setNegativeButton("Close", null)
            .create()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                filteredModels.clear()
                displayLabels.clear()
                for (m in allChannels) {
                    val numStr = (m.tv.id + 1).toString()
                    if (query.isEmpty() ||
                        numStr.startsWith(query) ||
                        m.tv.title.lowercase(Locale.ROOT).contains(query) ||
                        m.tv.group.lowercase(Locale.ROOT).contains(query)
                    ) {
                        filteredModels.add(m)
                        displayLabels.add("$numStr. ${m.tv.title} (${m.tv.group})")
                    }
                }
                adapter.notifyDataSetChanged()
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            if (position in 0 until filteredModels.size) {
                val selected = filteredModels[position]
                dialog.dismiss()
                hideSelf()
                mainActivity.play(selected.tv.id)
            }
        }

        dialog.show()
    }

    private fun confirmChannel() {
        SP.channel =
            min(max(SP.channel, 0), viewModel.groupModel.getAllList()!!.size())

        (activity as MainActivity).settingActive()
    }

    fun setServer(server: String) {
        this.server = "http://$server"
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this)
            .commit()
        (activity as MainActivity).showTime()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            applyTheme()
            btnAspectRatio?.text = "Ratio: ${SP.getAspectRatioLabel()}"
            btnResolution?.text = "Res: ${SP.getResolutionLabel()}"
            val cur = viewModel.groupModel.getCurrent()
            if (cur != null) {
                btnSourceLine?.text = "Line: ${cur.getSourceLineLabel()}"
            }
        }
    }

    private fun requestInstallPermissions() {
        val context = requireContext()
        val permissionsList: MutableList<String> = ArrayList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            permissionsList.add(Manifest.permission.REQUEST_INSTALL_PACKAGES)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsList.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                permissionsList.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            updateManager.checkAndUpdate()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_READ_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                (activity as MainActivity).playFirstOnNextChannelLoad()
                viewModel.parseUri(uri)
            } else {
                R.string.authorization_failed.showToast()
            }
        }
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                updateManager.checkAndUpdate()
            } else {
                R.string.authorization_failed.showToast()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::updateManager.isInitialized) {
            updateManager.destroy()
        }
        allButtons.clear()
        allSwitches.clear()
        btnAspectRatio = null
        btnSourceLine = null
        btnAudioTrack = null
        btnSubtitleTrack = null
        btnSearchKeypad = null
        btnPlaylists = null
        switchDarkMode = null
        switchPreferSwDecoder = null
        switchStreamHud = null
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
        const val PERMISSIONS_REQUEST_CODE = 1
        const val PERMISSION_READ_EXTERNAL_STORAGE_REQUEST_CODE = 2
        const val PICK_PLAYLIST_FILE_REQUEST_CODE = 3
    }
}
