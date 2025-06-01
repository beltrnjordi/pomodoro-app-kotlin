package jorbel3.uv.es.pomodoroapp.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jorbel3.uv.es.pomodoroapp.R
import jorbel3.uv.es.pomodoroapp.databinding.FragmentTaskListBinding
import jorbel3.uv.es.pomodoroapp.db.Task  // Importa la clase Task
import kotlinx.coroutines.flow.collectLatest

class TaskListFragment : Fragment() {

    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!

    private lateinit var taskAdapter: TaskAdapter
    private val taskListViewModel: TaskListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        val root = binding.root

        // Configurar el RecyclerView
        setupRecyclerView()

        return root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeTasks()
    }

    /**
     * Configura los botones de la interfaz.
     */
    private fun setupButtons() {
        binding.fabAddTask.setOnClickListener {
            findNavController().navigate(R.id.navigation_newtask)
        }

        binding.fabCompletedTasks.setOnClickListener {
            // Navigate to completed tasks screen
            findNavController().navigate(R.id.action_taskList_to_completedTasks)
        }
    }

    /**
     * Observa los cambios en la lista de tareas activas y actualiza el RecyclerView.
     * En el caso de que no haya tareas, se muestra un mensaje de "No hay tareas".
     */
    private fun observeTasks() {
        // Observe only active tasks
        lifecycleScope.launchWhenStarted {
            taskListViewModel.activeTasks.collectLatest { tasks ->
                taskAdapter.updateTasks(tasks)
                binding.emptyStateLayout.visibility =
                    if (tasks.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /**
     * Configura el RecyclerView para mostrar la lista de tareas.
     */
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(emptyList()) { task ->
            // Guardar la tarea seleccionada en preferencias compartidas o en ViewModel
            val taskViewModel = ViewModelProvider(requireActivity()).get(TaskViewModel::class.java)
            taskViewModel.selectTask(task)

            // Navegar a la pantalla Home
            findNavController().navigate(R.id.navigation_home)
        }

        binding.taskRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}