package jorbel3.uv.es.pomodoroapp.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jorbel3.uv.es.pomodoroapp.db.Task
import jorbel3.uv.es.pomodoroapp.db.TaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val taskDao = TaskDatabase.getInstance(application).taskDao()
    val subjects = MutableLiveData<List<String>>()

    /**
     * Metodo para insertar una nueva tarea en la base de datos.
     * @param title Título de la tarea.
     * @param subject Asignatura de la tarea.
     * @param numberPomodoros Número de pomodoros asignados a la tarea.
     */
    fun insertTask(
        title: String,
        subject: String,
        numberPomodoros: Int
    ) {
        val task = Task(
            id = 0,
            title = title,
            subject = subject,
            isCompleted = false,
            numberPomodoros = numberPomodoros
        )

        // Lanzamos una corrutina para realizar la operación en segundo plano
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insertTask(task)
        }
    }

    /**
     * Método para cargar las asignaturas desde la base de datos.
     * Se ejecuta en un hilo diferente al principal para evitar bloquear la interfaz de usuario.
     */
    fun loadSubjects() {
        // Lanzamos una corrutina para realizar la operación en segundo plano
        viewModelScope.launch(Dispatchers.IO) {
            val result = taskDao.getAllSubjects()
            subjects.postValue(result)
        }
    }
}
