package com.deactivated0.tv9.models

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.deactivated0.tv9.SP

class TVListModel(private val name: String, private val groupIndex: Int) : ViewModel() {
    fun getName(): String {
        return name
    }

    // position in tvGroup. No filters
    fun getGroupIndex(): Int {
        return groupIndex
    }

    private val _tvList = MutableLiveData<List<TVModel>>()
    val tvList: LiveData<List<TVModel>>
        get() = _tvList
    private val tvListValue: List<TVModel>
        get() = _tvList.value ?: emptyList()

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

    fun setPosition(position: Int) {
        if (_position.value != position) {
            _position.value = position
        }
    }

    fun setPositionPlaying(position: Int) {
        _positionPlaying.value = position
        SP.position = position
    }

    fun setPlaying() {
        val pos = positionValue
        _positionPlaying.value = pos
        SP.position = pos
    }

    private val _change = MutableLiveData<Boolean>()
    val change: LiveData<Boolean>
        get() = _change

    fun setChange() {
        _change.value = true
    }

    fun setTVListModel(tvList: List<TVModel>) {
        _tvList.value = if (tvList is MutableList) tvList else tvList.toMutableList()
    }

    /**
     * Appends without copying the backing list on every insertion (reduces O(N^2) to O(N)).
     */
    fun addTVModel(tvModel: TVModel) {
        val current = _tvList.value
        if (current == null) {
            _tvList.value = mutableListOf(tvModel)
            return
        }

        if (current is MutableList<TVModel>) {
            current.add(tvModel)
        } else {
            val newList = current.toMutableList()
            newList.add(tvModel)
            _tvList.value = newList
        }
    }

    fun removeTVModel(id: Int) {
        val current = _tvList.value ?: return
        if (current is MutableList<TVModel>) {
            val iterator = current.iterator()
            var removed = false
            while (iterator.hasNext()) {
                if (iterator.next().tv.id == id) {
                    iterator.remove()
                    removed = true
                }
            }
            if (removed) {
                _tvList.value = current
            }
        } else {
            val newList = current.toMutableList()
            val iterator = newList.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().tv.id == id) {
                    iterator.remove()
                }
            }
            _tvList.value = newList
        }
    }

    fun replaceTVModel(tvModel: TVModel) {
        val current = _tvList.value
        if (current == null) {
            _tvList.value = mutableListOf(tvModel)
            return
        }

        val targetId = tvModel.tv.id
        for (i in 0 until current.size) {
            if (current[i].tv.id == targetId) {
                return
            }
        }

        if (current is MutableList<TVModel>) {
            current.add(tvModel)
            _tvList.value = current
        } else {
            val newList = current.toMutableList()
            newList.add(tvModel)
            _tvList.value = newList
        }
    }

    fun initTVList() {
        _tvList.value = mutableListOf()
    }

    //API19-: clear() in ViewModel must rename!
    fun clear2() {
        initTVList()
        setPosition(0)
    }

    fun getTVModel(): TVModel? {
        return getTVModel(positionValue)
    }

    fun getTVModel(idx: Int): TVModel? {
        val list = _tvList.value ?: return null
        if (idx < 0 || idx >= list.size) {
            return null
        }
        setPosition(idx)
        return list[idx]
    }

    fun getCurrent(): TVModel? {
        val s = size()
        if (s == 0) return null
        val pos = positionValue
        if (pos < 0 || pos >= s) {
            return getTVModel(0)
        }
        return getTVModel(pos)
    }

    fun getPrev(): TVModel? {
        val list = _tvList.value ?: return null
        val s = list.size
        if (s == 0) {
            return null
        }

        val p = (s + positionValue - 1) % s
        setPosition(p)
        return list[p]
    }

    fun getNext(): TVModel? {
        val list = _tvList.value ?: return null
        val s = list.size
        if (s == 0) {
            return null
        }

        val p = (positionValue + 1) % s
        setPosition(p)
        return list[p]
    }

    fun setPositionByTvId(id: Int) {
        val list = _tvList.value ?: return
        for (i in 0 until list.size) {
            if (list[i].tv.id == id) {
                setPosition(i)
                return
            }
        }
    }

    init {
        _position.value = SP.position
        Log.i(TAG, "SP.position ${SP.position}")
    }

    fun size(): Int {
        return _tvList.value?.size ?: 0
    }

    companion object {
        const val TAG = "TVListModel"
    }
}
