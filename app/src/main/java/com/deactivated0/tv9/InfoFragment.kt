package com.deactivated0.tv9

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.deactivated0.tv9.databinding.InfoBinding
import com.deactivated0.tv9.models.TVModel


class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val delay: Long = 5000

    private var currentTvModel: TVModel? = null
    private var currentEpgTitle: String = "Featured Program"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        binding.info.layoutParams.width = application.px2Px(binding.info.layoutParams.width)
        binding.info.layoutParams.height = application.px2Px(binding.info.layoutParams.height)

        val layoutParams = binding.info.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = application.px2Px(binding.info.marginBottom)
        binding.info.layoutParams = layoutParams

        binding.logo.layoutParams.width = application.px2Px(binding.logo.layoutParams.width)
        var padding = application.px2Px(binding.logo.paddingTop)
        binding.logo.setPadding(padding, padding, padding, padding)
        binding.main.layoutParams.width = application.px2Px(binding.main.layoutParams.width)
        padding = application.px2Px(binding.main.paddingTop)
        binding.main.setPadding(padding, padding, padding, padding)

        val layoutParamsMain = binding.main.layoutParams as ViewGroup.MarginLayoutParams
        val mainMarginStart = application.px2Px(layoutParamsMain.leftMargin)
        layoutParamsMain.leftMargin = mainMarginStart
        layoutParamsMain.marginStart = mainMarginStart
        binding.main.layoutParams = layoutParamsMain

        val layoutParamsDesc = binding.desc.layoutParams as ViewGroup.MarginLayoutParams
        layoutParamsDesc.topMargin = application.px2Px(binding.desc.marginTop)
        binding.desc.layoutParams = layoutParamsDesc

        binding.title.textSize = application.px2PxFont(binding.title.textSize)
        binding.desc.textSize = application.px2PxFont(binding.desc.textSize)

        binding.container.layoutParams.width = application.shouldWidthPx()
        binding.container.layoutParams.height = application.shouldHeightPx()

        applyTheme()

        _binding!!.root.visibility = View.GONE
        return binding.root
    }

    fun applyTheme() {
        val b = _binding ?: return
        b.main.background = ThemeManager.createInfoRightDrawable()
        b.title.setTextColor(ThemeManager.titleColor)
        b.desc.setTextColor(ThemeManager.descriptionColor)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).ready(TAG)
    }

    private fun getLogoFallbackDrawable(id: Int): Drawable {
        logoBadgeCache.get(id)?.let { return it }
        val width = Utils.dpToPx(100)
        val height = Utils.dpToPx(60)
        val text = "${id + 1}"
        val textSize = if (id > 999) 70f else 80f
        val drawable = Utils.TextBadgeDrawable(text, Color.WHITE, textSize, width, height)
        logoBadgeCache.put(id, drawable)
        return drawable
    }

    fun show(tvViewModel: TVModel, metadata: PlayerFragment.Metadata? = null) {
        currentTvModel = tvViewModel
        applyTheme()
        val url = tvViewModel.getVideoUrl()
        val ipv6Text = if (url != null && url.isIPv6()) " | IPV6" else ""
        val lineText = if (tvViewModel.getUriCount() > 1) " [${tvViewModel.getSourceLineLabel()}]" else ""
        binding.title.text = "${tvViewModel.tv.title}$lineText$ipv6Text"

        val fallbackDrawable = getLogoFallbackDrawable(tvViewModel.tv.id)
        val logoUrl = tvViewModel.tv.logo
        if (logoUrl.isNullOrBlank()) {
            Glide.with(this).clear(binding.logo)
            binding.logo.setImageDrawable(fallbackDrawable)
        } else {
            Glide.with(this)
                .load(logoUrl)
                .error(fallbackDrawable)
                .into(binding.logo)
        }

        val now = Utils.getDateTimestamp()
        val epgList = tvViewModel.epg.value
        var foundEpg: String? = null
        if (!epgList.isNullOrEmpty()) {
            for (i in epgList.size - 1 downTo 0) {
                if (epgList[i].beginTime < now) {
                    foundEpg = epgList[i].title
                    break
                }
            }
        }
        currentEpgTitle = foundEpg ?: "Featured Program"
        updateDescText(metadata)

        handler.removeCallbacks(removeRunnable)
        view?.visibility = View.VISIBLE
        handler.postDelayed(removeRunnable, delay)
    }

    fun updateMetadata(metadata: PlayerFragment.Metadata) {
        if (_binding == null || view?.visibility != View.VISIBLE) return
        updateDescText(metadata)
    }

    private fun updateDescText(metadata: PlayerFragment.Metadata?) {
        val b = _binding ?: return
        if (SP.showStreamHud && metadata != null) {
            val hud = metadata.toCompactHudString()
            if (hud.isNotEmpty()) {
                b.desc.text = "$currentEpgTitle  •  $hud"
                return
            }
        }
        b.desc.text = currentEpgTitle
    }

    override fun onResume() {
        super.onResume()
        if (view?.visibility == View.VISIBLE) {
            handler.postDelayed(removeRunnable, delay)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(removeRunnable)
    }

    private val removeRunnable = Runnable {
        view?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(removeRunnable)
        _binding = null
    }

    companion object {
        private const val TAG = "InfoFragment"
        private val logoBadgeCache = LruCache<Int, Drawable>(64)
    }
}