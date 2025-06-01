package jorbel3.uv.es.pomodoroapp.ui.home

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import jorbel3.uv.es.pomodoroapp.MainActivity
import jorbel3.uv.es.pomodoroapp.PomodoroService
import jorbel3.uv.es.pomodoroapp.R
import jorbel3.uv.es.pomodoroapp.data.PomodoroSettingsManager
import jorbel3.uv.es.pomodoroapp.databinding.FragmentHomeBinding
import jorbel3.uv.es.pomodoroapp.db.Task
import jorbel3.uv.es.pomodoroapp.ui.tasks.TaskListViewModel
import jorbel3.uv.es.pomodoroapp.ui.tasks.TaskViewModel

/**
 * Fragment que representa la pantalla principal de la aplicación.
 * Contiene la lógica para iniciar, pausar y detener el temporizador Pomodoro.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var taskViewModel: TaskViewModel

    private var pomodoroService: PomodoroService? = null
    private var serviceBound = false

    /**
     * Conexión al servicio PomodoroService.
     * Se utiliza para interactuar con el servicio y obtener su estado.
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PomodoroService.PomodoroBinder
            pomodoroService = binder.getService()
            serviceBound = true

            pomodoroService?.let { service ->
                homeViewModel.bindService(service)

                // Actualizar ViewModel con los valores del servicio
                service.currentTime.value?.let { time ->
                    homeViewModel.updateTime(time)
                }

                homeViewModel.updateTotalTime(service.getTotalTimeInMillis())

                // Usar setValue en lugar de postValue (o exponer métodos en ViewModel)
                service.timerState.value?.let { state ->
                    homeViewModel.setTimerState(state)
                }

                service.pomodoroCount.value?.let { count ->
                    homeViewModel.setPomodorosCompleted(count)
                }

                taskViewModel.selectedTask.value?.let { task ->
                    service.setCurrentTask(task.id.toLong())
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskViewModel = ViewModelProvider(requireActivity()).get(TaskViewModel::class.java)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        taskViewModel = ViewModelProvider(requireActivity()).get(TaskViewModel::class.java)

        // Obtenemos el tiempo de pomodoro desde la configuraciónS
        val settingsManager = PomodoroSettingsManager.getInstance(requireContext())
        val pomodoroTimeMinutes = settingsManager.getPomodoroTime()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root = binding.root

        setupViews()
        setupObservers()

        val serviceIntent = Intent(requireContext(), PomodoroService::class.java).apply {
            putExtra("pomodoroTimeMinutes", pomodoroTimeMinutes)

            taskViewModel.selectedTask.value?.let { task ->
                putExtra("taskId", task.id.toLong())
            }
        }

        requireActivity().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        requireActivity().startService(serviceIntent)

        return root
    }

    private fun setupViews() {
        // Configuración de vistas como antes...
        binding.playButton.setOnClickListener { homeViewModel.startTimer() }
        binding.pauseButton.setOnClickListener { homeViewModel.pauseTimer() }
        binding.stopButton.setOnClickListener { homeViewModel.stopTimer() }
        binding.skipButton.setOnClickListener { homeViewModel.skipToNextState() }
    }

    private fun setupObservers() {
        // Observadores para el temporizador
        homeViewModel.timeText.observe(viewLifecycleOwner) { binding.timerText.text = it }
        homeViewModel.timerRunning.observe(viewLifecycleOwner) { updateButtonsVisibility(it) }
        homeViewModel.progress.observe(viewLifecycleOwner) { binding.progressCircle.progress = it }

        // Observador para el contador de pomodoros
        homeViewModel.pomodorosCompleted.observe(viewLifecycleOwner) { completed ->
            val currentTask = taskViewModel.selectedTask.value
            if (currentTask != null) {
                if (homeViewModel.checkTaskCompletion(completed, currentTask.numberPomodoros)) {
                    // Si la tarea está completa, actualizar la UI, y desvincular el servicio de la tarea.
                    Toast.makeText(requireContext(), "Tarea: ${currentTask.title} completada", Toast.LENGTH_SHORT).show()
                    binding.taskText.text = "Pomodoro Libre"
                    binding.pomodoroStatus.text = "Pomodoro $completed"
                    pomodoroService?.setCurrentTask(null)
                    taskViewModel.selectTask(null)
                } else {
                    // Actualizar la UI con el número de pomodoros completados
                    binding.pomodoroStatus.text = "Pomodoro $completed de ${currentTask.numberPomodoros}"
                }
            } else {
                // No hay tarea seleccionada, mostrar valor por defecto.
                binding.taskText.text = "Pomodoro Libre"
                binding.pomodoroStatus.text = "Pomodoro $completed"
            }
        }

        // Observador para la tarea seleccionada
        taskViewModel.selectedTask.observe(viewLifecycleOwner) { task ->
            if (task != null) {
                binding.taskText.text = task.title
                binding.pomodoroStatus.text = "Pomodoro 0 de ${task.numberPomodoros}"
                // If service is already bound, set the current task.
                pomodoroService?.setCurrentTask(task.id.toLong())
            } else {
                // No task selected, show default value.
                binding.pomodoroStatus.text = "Pomodoro 0 of 5"
            }
        }

        // Observador para el estado del temporizador
        homeViewModel.timerComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete && homeViewModel.timerState.value == PomodoroService.TimerState.POMODORO) {
                taskViewModel.selectedTask.value?.let { task ->
                    val taskListViewModel: TaskListViewModel by viewModels()
                    taskListViewModel.markPomodoroCompleted(task.id)

                    // Actualizar el número de pomodoros completados
                    val updatedPomodoros = task.numberCompletedPomodoros + 1
                    if (homeViewModel.checkTaskCompletion(updatedPomodoros, task.numberPomodoros)) {
                        // Si la tarea está completa, actualizar la UI y desvincular el servicio de la tarea.
                        binding.taskText.text = "Pomodoro Libre"
                        binding.pomodoroStatus.text = "Pomodoro $updatedPomodoros"
                        pomodoroService?.setCurrentTask(null)
                        taskViewModel.selectTask(null)
                    } else {
                        // Actualizar la tarea en la base de datos
                        val updatedTask = task.copy(numberCompletedPomodoros = updatedPomodoros)
                        taskViewModel.selectTask(updatedTask)
                    }
                }
            }
        }
    }

    /**
     * Actualiza la visibilidad de los botones de control del temporizador.
     * @param isRunning Indica si el temporizador está en ejecución.
     */
    private fun updateButtonsVisibility(isRunning: Boolean) {
        binding.playButton.visibility = if (isRunning) View.GONE else View.VISIBLE
        binding.pauseButton.visibility = if (isRunning) View.VISIBLE else View.GONE
        binding.stopButton.visibility = if (isRunning) View.VISIBLE else View.GONE

        // Ocultar el linearlayout de saltar si el temporizador no está en ejecución, asi queda bien
        binding.linearLayoutSkip.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as MainActivity).getPomodoroService()?.let { service ->
            homeViewModel.bindService(service)
        }
    }

    override fun onDestroyView() {
        // Solo desvincular el servicio, NO detenerlo
        if (serviceBound) {
            requireActivity().unbindService(connection)
            serviceBound = false
        }
        _binding = null
        super.onDestroyView()
    }
}