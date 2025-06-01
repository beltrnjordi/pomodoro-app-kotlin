package jorbel3.uv.es.pomodoroapp.ui.tasks

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jorbel3.uv.es.pomodoroapp.R
import jorbel3.uv.es.pomodoroapp.db.Task  // Importa la clase Task de la DB

class CompletedTaskAdapter(
    private var tasks: List<Task>
) : RecyclerView.Adapter<CompletedTaskAdapter.TaskViewHolder>() {

    /**
     * ViewHolder que representa cada tarea en la lista.
     * Contiene las vistas que se enlazarán con los datos de la tarea.
     */
    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val title: TextView = view.findViewById(R.id.taskTitle)
        val subject: TextView = view.findViewById(R.id.taskSubject)
        val planned: TextView = view.findViewById(R.id.taskPlanned)
        val completed: TextView = view.findViewById(R.id.taskCompleted)

        /**
         * Metodo para enlazar los datos de la tarea con la informacion de las cards
         * @param task Tarea a enlazar.
         */
        fun bind(task: Task) {
            title.text = task.title
            subject.text = task.subject
            planned.text = "Planned: ${task.numberPomodoros}"
            completed.text = "Completed: ${task.numberCompletedPomodoros}"
        }
    }

    /**
     * Metodo que se llama para crear el ViewHolder.
     * @param parent ViewGroup padre donde se añadirá el ViewHolder.
     * @param viewType Tipo de vista (no se utiliza en este caso).
     * @return Un nuevo ViewHolder con la vista inflada.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_completedtask, parent, false)
        return TaskViewHolder(view)
    }

    /**
     * Método que se llama para enlazar los datos de la tarea con la vista.
     * @param holder ViewHolder que contiene las vistas a enlazar.
     * @param position Posición de la tarea en la lista.
     */
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task)
    }

    override fun getItemCount(): Int = tasks.size

    /**
     * Metodo para actualizar la lista de tareas completadas.
     * @param newTasks Nueva lista de tareas completadas.
     */
    fun updateTasks(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}