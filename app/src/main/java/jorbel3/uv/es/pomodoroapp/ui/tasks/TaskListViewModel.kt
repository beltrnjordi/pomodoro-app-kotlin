package jorbel3.uv.es.pomodoroapp.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jorbel3.uv.es.pomodoroapp.db.Task
import jorbel3.uv.es.pomodoroapp.db.TaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TaskDatabase.getInstance(application).taskDao()

    // Cogemos unicamente las tareas que aún no han sido completadas (TaskListFragment)
    val activeTasks: Flow<List<Task>> = dao.getActiveTasks()
    // Cogemos las tareas que ya han sido completadas (TaskListFragment)
    val completedTasks: Flow<List<Task>> = dao.getCompletedTasks()

    /**
     * Metodo para añadir una nueva tarea a la base de datos.
     * @param taskId ID de la tarea a añadir.
     */
    fun markPomodoroCompleted(taskId: Int) {
        // Lanzamos una corrutina para realizar la operación en segundo plano
        viewModelScope.launch(Dispatchers.IO) {
            // Obtenemos la tarea actual
            val task = dao.getTaskById(taskId)

            // Incrementamos el contador de pomodoros completados
            task?.let {
                val updatedTask = it.copy(
                    numberCompletedPomodoros = it.numberCompletedPomodoros + 1
                )
                // Actualizamos la tarea en la base de datos
                dao.updateTask(updatedTask)
            }
        }
    }
}

