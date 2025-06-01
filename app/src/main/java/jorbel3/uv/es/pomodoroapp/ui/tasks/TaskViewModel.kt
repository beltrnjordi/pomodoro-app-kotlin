package jorbel3.uv.es.pomodoroapp.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jorbel3.uv.es.pomodoroapp.db.Task

class TaskViewModel : ViewModel() {
    private val _selectedTask = MutableLiveData<Task?>()
    val selectedTask: LiveData<Task?> = _selectedTask

    fun selectTask(task: Task?) {
        _selectedTask.value = task
    }
}