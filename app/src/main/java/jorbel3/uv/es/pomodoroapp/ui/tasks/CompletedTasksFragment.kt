package jorbel3.uv.es.pomodoroapp.ui.tasks
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import jorbel3.uv.es.pomodoroapp.R

class CompletedTasksFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var taskAdapter: CompletedTaskAdapter
    private val taskListViewModel: TaskListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_completedtasks_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.completedTasksRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)

        setupRecyclerView()
        observeCompletedTasks()
    }

    /**
     * Configura el RecyclerView para mostrar la lista de tareas completadas.
     */
    private fun setupRecyclerView() {
        taskAdapter = CompletedTaskAdapter(emptyList())

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }

    /**
     * Observa la lista de tareas completadas y actualiza el RecyclerView.
     */
    private fun observeCompletedTasks() {
        lifecycleScope.launchWhenStarted {
            taskListViewModel.completedTasks.collectLatest { tasks ->
                taskAdapter.updateTasks(tasks)
                emptyStateLayout.visibility =
                    if (tasks.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // This line is deprecated but still works
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}