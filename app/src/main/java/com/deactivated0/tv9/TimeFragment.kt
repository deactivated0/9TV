package com.deactivated0.tv9

import MainViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deactivated0.tv9.databinding.TimeBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimeFragment : Fragment() {
    private var _binding: TimeBinding? = null
    private val binding get() = _binding!!

    private var job: Job? = null
    private var lastDisplayedTime: String = ""

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TimeBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        binding.time.layoutParams.width = application.px2Px(binding.time.layoutParams.width)
        binding.time.layoutParams.height = application.px2Px(binding.time.layoutParams.height)

        val layoutParams = binding.time.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = application.px2Px(binding.time.marginTop)
        val marginEndPx = application.px2Px(layoutParams.rightMargin)
        layoutParams.rightMargin = marginEndPx
        layoutParams.marginEnd = marginEndPx
        binding.time.layoutParams = layoutParams

        binding.content.textSize = application.px2PxFont(binding.content.textSize)
        binding.channel.textSize = application.px2PxFont(binding.channel.textSize)

        binding.main.layoutParams.width = application.shouldWidthPx()
        binding.main.layoutParams.height = application.shouldHeightPx()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        startTimer()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            job?.cancel()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        job?.cancel()
        if (_binding == null || isHidden) return
        job = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                if (::viewModel.isInitialized) {
                    val currentTime = viewModel.getTime()
                    if (currentTime != lastDisplayedTime) {
                        lastDisplayedTime = currentTime
                        _binding?.content?.text = currentTime
                    }
                }
                // If seconds are displayed, align to next 1s boundary; else align to next 60s minute boundary
                val now = System.currentTimeMillis()
                val sleepMs = if (SP.displaySeconds) {
                    1000L - (now % 1000L)
                } else {
                    60_000L - (now % 60_000L)
                }
                delay(sleepMs.coerceIn(100L, 60_000L))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job?.cancel()
        _binding = null
    }

    companion object {
        private const val TAG = "TimeFragment"
    }
}