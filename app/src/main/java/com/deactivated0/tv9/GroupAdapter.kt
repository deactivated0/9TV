package com.deactivated0.tv9

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deactivated0.tv9.databinding.GroupItemBinding
import com.deactivated0.tv9.models.TVGroupModel
import com.deactivated0.tv9.models.TVListModel


class GroupAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private var tvGroupModel: TVGroupModel,
) :
    RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    private var listener: ItemListener? = null
    private var focused: View? = null
    private var defaultFocused = false
    private var defaultFocus: Int = -1

    var visiable = false

    private var first = true

    val application = context.applicationContext as MyTVApplication

    private val strMyFavorites = context.getString(R.string.my_favorites)
    private val strAllChannels = context.getString(R.string.all_channels)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = GroupItemBinding.inflate(inflater, parent, false)

        binding.root.isFocusable = true
        binding.root.isFocusableInTouchMode = true
        return ViewHolder(binding)
    }

    fun focusable(able: Boolean) {
        recyclerView.isFocusable = able
        recyclerView.isFocusableInTouchMode = able
        if (able) {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        } else {
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun clear() {
        focused?.clearFocus()
        recyclerView.invalidate()
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val listTVModel = tvGroupModel.getTVListModel(position) ?: return
        val view = viewHolder.itemView

        if (!defaultFocused && position == defaultFocus) {
            view.requestFocus()
            defaultFocused = true
        }

        viewHolder.bind(listTVModel, position)
    }

    override fun getItemCount() = tvGroupModel.size()

    inner class ViewHolder(private val binding: GroupItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        View.OnFocusChangeListener,
        View.OnClickListener,
        View.OnKeyListener {

        private var currentModel: TVListModel? = null
        private var currentPos: Int = 0

        init {
            binding.root.onFocusChangeListener = this
            binding.root.setOnClickListener(this)
            binding.root.setOnKeyListener(this)
        }

        fun bind(model: TVListModel, position: Int) {
            currentModel = model
            currentPos = position
            bindTitle(model.getName())
            focus(binding.root.hasFocus())
        }

        fun bindTitle(text: String) {
            val displayName = when (text) {
                "My Favorites" -> strMyFavorites
                "All Channels" -> strAllChannels
                else -> text
            }
            binding.title.text = displayName
            val iconText = when {
                displayName == strMyFavorites -> "★"
                displayName.startsWith("📺") -> ""
                displayName == strAllChannels -> "📁"
                else -> "•"
            }
            if (iconText.isNotEmpty()) {
                binding.icon.text = iconText
                binding.icon.visibility = View.VISIBLE
            } else {
                binding.icon.visibility = View.GONE
            }
        }

        fun focus(hasFocus: Boolean) {
            if (hasFocus) {
                binding.root.background = ThemeManager.focusItemDrawable
                binding.title.setTextColor(ThemeManager.focusItemTextColor)
                binding.icon.setTextColor(ThemeManager.focusItemTextColor)
            } else {
                binding.root.background = null
                binding.title.setTextColor(ThemeManager.titleColor)
                binding.icon.setTextColor(ThemeManager.descriptionColor)
            }
        }

        override fun onFocusChange(v: View?, hasFocus: Boolean) {
            val model = currentModel ?: return
            listener?.onItemFocusChange(model, hasFocus)

            if (hasFocus) {
                focus(true)
                focused = itemView
                if (visiable) {
                    val p = model.getGroupIndex()
                    if (p != tvGroupModel.positionValue) {
                        tvGroupModel.setPosition(p)
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
            if (event?.action == KeyEvent.ACTION_UP) {
                recyclerView.postDelayed({
                    tvGroupModel.isInLikeMode = pos == 0
                }, 500)
            }
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

                return listener?.onKey(keyCode) ?: false
            }
            return false
        }
    }

    fun scrollToPositionAndSelect(position: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.let {
            val delay = if (first) {
                100L
            } else {
                first = false
                0L
            }

            recyclerView.postDelayed({
                val groupPosition =
                    if (SP.showAllChannels || position == 0) position else position - 1
                it.scrollToPositionWithOffset(groupPosition, 0)

                val viewHolder = recyclerView.findViewHolderForAdapterPosition(groupPosition)
                viewHolder?.itemView?.apply {
                    isSelected = true
                    requestFocus()
                }
            }, delay)
        }
    }

    interface ItemListener {
        fun onItemFocusChange(listTVModel: TVListModel, hasFocus: Boolean)
        fun onItemClicked(position: Int)
        fun onKey(keyCode: Int): Boolean
    }

    fun setItemListener(listener: ItemListener) {
        this.listener = listener
    }

    fun update(tvGroupModel: TVGroupModel) {
        this.tvGroupModel = tvGroupModel
        recyclerView.post {
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val TAG = "GroupAdapter"
    }
}
