package com.deactivated0.tv9.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.deactivated0.tv9.ISP
import com.deactivated0.tv9.SP

class TVGroupModel : ViewModel() {
    var isInLikeMode = false
    private val _tvGroup = MutableLiveData<List<TVListModel>>()
    val tvGroup: LiveData<List<TVListModel>>
        get() = _tvGroup
    val tvGroupValue: List<TVListModel>
        get() = _tvGroup.value ?: emptyList()

    // Filtered
    private val _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position
    val positionValue: Int
        get() = _position.value ?: 0

    private val _positionPlaying = MutableLiveData<Int>()
    val positionPlaying: LiveData<Int>
        get() = _positionPlaying
    val positionPlayingValue: Int
        get() = _positionPlaying.value ?: 0

    private val _change = MutableLiveData<Boolean>()
    val change: LiveData<Boolean>
        get() = _change

    private var isp = ISP.UNKNOWN
    fun setISP(isp: ISP) {
        this.isp = isp
    }

    fun setPosition(position: Int) {
        if (_position.value != position) {
            _position.value = position
        }
    }

    fun setPositionPlaying(position: Int) {
        _positionPlaying.value = position
        SP.positionGroup = position
    }

    fun setPlaying() {
        val pos = positionValue
        _positionPlaying.value = pos
        SP.positionGroup = pos
    }

    fun setPrevPosition() {
        val s = size()
        if (s > 0) {
            _position.value = (positionValue - 1) % s
        }
    }

    fun setNextPosition() {
        val s = size()
        if (s > 0) {
            _position.value = (positionValue + 1) % s
        }
    }

    fun setChange() {
        _change.value = true
    }

    fun setTVListModelList(tvGroup: List<TVListModel>) {
        _tvGroup.value = if (tvGroup is MutableList) tvGroup else tvGroup.toMutableList()
    }

    fun addTVListModel(listTVModel: TVListModel) {
        val current = _tvGroup.value
        if (current == null) {
            _tvGroup.value = mutableListOf(listTVModel)
            return
        }

        if (current is MutableList<TVListModel>) {
            current.add(listTVModel)
            _tvGroup.value = current
        } else {
            val newList = current.toMutableList()
            newList.add(listTVModel)
            _tvGroup.value = newList
        }
    }

    fun initTVGroup() {
        val current = _tvGroup.value ?: return
        if (current.size >= 2) {
            val fav = current[0]
            val all = current[1]
            all.initTVList()
            _tvGroup.value = mutableListOf(fav, all)
        }
    }

    //API19-: clear() in ViewModel must rename!
    fun clear2() {
        if (SP.showAllChannels) {
            _tvGroup.value =
                mutableListOf(getFavoritesList()!!, getAllList()!!)
            setPosition(0)
            getAllList()?.clear2()
        } else {
            _tvGroup.value = mutableListOf(getFavoritesList()!!)
            setPosition(0)
        }
    }

    fun getTVListModel(): TVListModel? {
        return getTVListModel(positionValue)
    }

    /**
     * O(1) index lookup without allocating a filtered list on every call.
     * Index 0 is always "My Favorites", Index 1 is always "All Channels".
     */
    fun getTVListModel(idx: Int): TVListModel? {
        val group = _tvGroup.value ?: return null
        if (idx < 0 || idx >= size()) {
            return null
        }
        if (SP.showAllChannels || group.size <= 2) {
            return group.getOrNull(idx)
        }
        // When "All Channels" (index 1) is hidden, idx 0 -> 0, idx >= 1 -> idx + 1
        val actualIdx = if (idx == 0) 0 else idx + 1
        return group.getOrNull(actualIdx)
    }

    fun getTVListModelNotFilter(idx: Int): TVListModel? {
        val group = _tvGroup.value ?: return null
        if (idx < 0 || idx >= group.size) {
            return null
        }
        return group[idx]
    }

    // get & set
    fun getPosition(position: Int): TVModel? {
        val group = tvGroupValue
        if (group.size < 2 || group[1].size() == 0) {
            return null
        }

        var count = 0
        for ((index, i) in group.withIndex()) {
            val countBefore = count
            count += i.size()
            if (count > position) {
                setPosition(index)
                val listPosition = position - countBefore
                i.setPosition(listPosition)
                return i.getTVModel(listPosition)
            }
        }

        return null
    }

    fun getCurrent(): TVModel? {
        val group = tvGroupValue
        if (group.size < 2 || group[1].size() == 0) {
            return null
        }

        return getCurrentList()?.getCurrent()
    }

    fun getCurrentList(): TVListModel? {
        return getTVListModelNotFilter(positionValue)
    }

    fun getFavoritesList(): TVListModel? {
        return getTVListModelNotFilter(0)
    }

    fun getAllList(): TVListModel? {
        return getTVListModelNotFilter(1)
    }

    // get & set
    // keep: In the current list loop
    fun getPrev(keep: Boolean = false): TVModel? {
        val group = tvGroupValue
        if (group.size < 2 || group[1].size() == 0) {
            return null
        }

        var tvListModel = getCurrentList()!!
        if (keep) {
            return tvListModel.getPrev()
        }

        // Prev tvListModel
        if (tvListModel.positionValue == 0) {
            var p = (group.size + positionValue - 1) % group.size
            setPosition(p)
            if (p == 1) {
                p = (group.size + positionValue - 1) % group.size
                setPosition(p)
            }
            if (p == 0) {
                p = (group.size + positionValue - 1) % group.size
                setPosition(p)
            }

            tvListModel = getTVListModelNotFilter(p)!!
            return tvListModel.getTVModel(tvListModel.size() - 1)
        }

        return tvListModel.getPrev()
    }

    // get & set
    fun getNext(keep: Boolean = false): TVModel? {
        val group = tvGroupValue
        if (group.size < 2 || group[1].size() == 0) {
            return null
        }

        var tvListModel = getCurrentList()!!
        if (keep) {
            return tvListModel.getNext()
        }

        // Next tvListModel
        if (tvListModel.positionValue == tvListModel.size() - 1) {
            var p = (positionValue + 1) % group.size
            setPosition(p)
            if (p == 0) {
                p = (group.size + positionValue + 1) % group.size
                setPosition(p)
            }
            if (p == 1) {
                p = (group.size + positionValue + 1) % group.size
                setPosition(p)
            }

            tvListModel = getTVListModelNotFilter(p)!!
            return tvListModel.getTVModel(0)
        }

        return tvListModel.getNext()
    }

    init {
        _position.value = SP.positionGroup
        Log.i(TAG, "SP.positionGroup ${SP.positionGroup}")
        isInLikeMode = SP.defaultLike && _position.value == 0
    }

    fun size(): Int {
        val group = _tvGroup.value ?: return 0
        if (SP.showAllChannels || group.size <= 2) {
            return group.size
        }
        return group.size - 1
    }

    companion object {
        const val TAG = "TVGroupModel"
    }
}