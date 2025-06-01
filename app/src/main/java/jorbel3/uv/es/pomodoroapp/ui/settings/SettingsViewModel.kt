package jorbel3.uv.es.pomodoroapp.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _pomodoroTime = MutableLiveData<Int>()
    val pomodoroTime: LiveData<Int> = _pomodoroTime

    private val _shortBreakTime = MutableLiveData<Int>()
    val shortBreakTime: LiveData<Int> = _shortBreakTime

    private val _longBreakTime = MutableLiveData<Int>()
    val longBreakTime: LiveData<Int> = _longBreakTime

    private val _longBreakInterval = MutableLiveData<Int>()
    val longBreakInterval: LiveData<Int> = _longBreakInterval

    private val _autoStartBreaks = MutableLiveData<Boolean>()
    val autoStartBreaks: LiveData<Boolean> = _autoStartBreaks

    private val _autoStartPomodoros = MutableLiveData<Boolean>()
    val autoStartPomodoros: LiveData<Boolean> = _autoStartPomodoros

}