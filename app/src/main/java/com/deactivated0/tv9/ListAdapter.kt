package com.deactivated0.tv9

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setPadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.deactivated0.tv9.databinding.ListItemBinding
import com.deactivated0.tv9.models.TVListModel
import com.deactivated0.tv9.models.TVModel


class ListAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    var listTVModel: TVListModel,
) :
    RecyclerView.Adapter<ListAdapter.ViewHolder>() {
    private var listener: ItemListener? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1

    var visiable = false

    val application = context.applicationContext as MyTVApplication

    private val drawableHeart: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_heart)
    private val drawableHeartEmptyDark: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_heart_empty)
    private val drawableHeartEmptyLight: Drawable? =
        AppCompatResources.getDrawable(context, R.drawable.ic_heart_empty)?.mutate()?.apply {
            setColorFilter(0xFF111111.toInt(), PorterDuff.Mode.SRC_IN)
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return listTVModel.getTVModel(position)?.tv?.id?.toLong() ?: position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = ListItemBinding.inflate(inflater, parent, false)

        binding.icon.layoutParams.width = application.px2Px(binding.icon.layoutParams.width)
        binding.icon.layoutParams.height = application.px2Px(binding.icon.layoutParams.height)
        binding.icon.setPadding(application.px2Px(binding.icon.paddingTop))

        val layoutParams = binding.title.layoutParams as ViewGroup.MarginLayoutParams
        val titleMarginStart = application.px2Px(layoutParams.leftMargin)
        layoutParams.leftMargin = titleMarginStart
        layoutParams.marginStart = titleMarginStart
        binding.title.layoutParams = layoutParams

        binding.heart.layoutParams.width = application.px2Px(binding.heart.layoutParams.width)
        binding.heart.layoutParams.height = application.px2Px(binding.heart.layoutParams.height)

        binding.title.textSize = application.px2PxFont(binding.title.textSize)

        val layoutParamsHeart = binding.heart.layoutParams as ViewGroup.MarginLayoutParams
        val heartMarginStart = application.px2Px(layoutParamsHeart.leftMargin)
        layoutParamsHeart.leftMargin = heartMarginStart
        layoutParamsHeart.marginStart = heartMarginStart
        binding.heart.layoutParams = layoutParamsHeart

        binding.description.textSize = application.px2PxFont(binding.description.textSize)

        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true

        return ViewHolder(binding)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun update(listTVModel: TVListModel) {
        this.listTVModel = listTVModel
        recyclerView.post {
            notifyDataSetChanged()
        }
    }

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val tvModel = listTVModel.getTVModel(position) ?: return
        val view = viewHolder.itemView

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        viewHolder.bind(tvModel, position)
    }

    override fun getItemCount() = listTVModel.size()

    inner class ViewHolder(val binding: ListItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        View.OnFocusChangeListener,
        View.OnClickListener,
        View.OnKeyListener {

        private var currentModel: TVModel? = null
        private var currentPos: Int = 0

        init {
            binding.root.onFocusChangeListener = this
            binding.root.setOnClickListener(this)
            binding.root.setOnKeyListener(this)
            binding.heart.setOnClickListener {
                val model = currentModel ?: return@setOnClickListener
                val newLiked = !(model.like.value ?: false)
                model.setLike(newLiked)
                like(newLiked)
            }
        }

        fun bind(tvModel: TVModel, position: Int) {
            currentModel = tvModel
            currentPos = position
            like(tvModel.like.value ?: false)
            focus(binding.root.hasFocus())
            bindTitle(tvModel.tv.title)
            bindImage(tvModel.tv.logo, tvModel.tv.id)
        }

        fun bindTitle(text: String) {
            binding.title.text = text
        }

        fun bindImage(url: String?, id: Int) {
            val badgeDrawable = getChannelNumberDrawable(context, id, ThemeManager.titleColor)
            if (url.isNullOrBlank()) {
                Glide.with(context).clear(binding.icon)
                binding.icon.setImageDrawable(badgeDrawable)
            } else {
                Glide.with(context)
                    .load(url)
                    .centerInside()
                    .error(badgeDrawable)
                    .into(binding.icon)
            }
        }

        fun focus(hasFocus: Boolean) {
            if (hasFocus) {
                binding.title.setTextColor(ThemeManager.focusItemTextColor)
                binding.description.setTextColor(ThemeManager.focusItemTextColor)
                binding.root.background = ThemeManager.focusItemDrawable
            } else {
                binding.title.setTextColor(ThemeManager.titleColor)
                binding.description.setTextColor(ThemeManager.descriptionColor)
                binding.root.background = ThemeManager.surfaceDrawable
            }
        }

        fun like(liked: Boolean) {
            if (liked) {
                binding.heart.setImageDrawable(drawableHeart)
            } else {
                binding.heart.setImageDrawable(
                    if (ThemeManager.isDarkMode) drawableHeartEmptyDark else drawableHeartEmptyLight
                )
            }
        }

        override fun onFocusChange(v: View?, hasFocus: Boolean) {
            val model = currentModel ?: return
            listener?.onItemFocusChange(model, hasFocus)

            if (hasFocus) {
                focus(true)
                focused = itemView
                if (visiable) {
                    if (currentPos != listTVModel.positionValue) {
                        listTVModel.setPosition(currentPos)
                    }
                } else {
                    visiable = true
                }
            } else {
                focus(false)
            }
        }

        override fun onClick(v: View?) {
            listener?.onItemClicked(currentPos)
        }

        override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
            val pos = currentPos
            val model = currentModel ?: return false
            if (event?.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                    val p = itemCount - 1
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )
                    recyclerView.postDelayed({
                        val vh = recyclerView.findViewHolderForAdapterPosition(p)
                        vh?.itemView?.isSelected = true
                        vh?.itemView?.requestFocus()
                    }, 0)
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos == itemCount - 1) {
                    val p = 0
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        p,
                        0
                    )
                    recyclerView.postDelayed({
                        val vh = recyclerView.findViewHolderForAdapterPosition(p)
                        vh?.itemView?.isSelected = true
                        vh?.itemView?.requestFocus()
                    }, 0)
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val newLiked = !(model.like.value ?: false)
                    model.setLike(newLiked)
                    like(newLiked)
                }

                return listener?.onKey(this@ListAdapter, keyCode) ?: false
            }
            return false
        }
    }

    fun toPosition(position: Int) {
        Log.i(TAG, "position $position")
        recyclerView.post {
            (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                position,
                0
            )

            recyclerView.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.isSelected = true
                viewHolder?.itemView?.requestFocus()
            }, 0)
        }
    }

    interface ItemListener {
        fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean)
        fun onItemClicked(position: Int, type: String = "list")
        fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }

    companion object {
        private const val TAG = "ListAdapter"

        private val badgeWidth by lazy { Utils.dpToPx(40) }
        private val badgeHeight by lazy { Utils.dpToPx(40) }
        private val badgeCache = LruCache<Int, Drawable>(128)

        fun getChannelNumberDrawable(context: Context, id: Int, textColor: Int): Drawable {
            // Key combines channel ID and dark/light mode bit
            val cacheKey = if (textColor == Color.WHITE || textColor == 0xFFEEEEEE.toInt()) id else (id or (1 shl 24))
            badgeCache.get(cacheKey)?.let { return it }
            val num = id + 1
            val text = when {
                num < 10 -> "  $num"
                num < 100 -> " $num"
                else -> num.toString()
            }
            val drawable = Utils.TextBadgeDrawable(text, textColor, 32f, badgeWidth, badgeHeight)
            badgeCache.put(cacheKey, drawable)
            return drawable
        }
    }
}
