package jorbel3.uv.es.pomodoroapp.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.slider.Slider
import jorbel3.uv.es.pomodoroapp.R

class NewTaskFragment : Fragment() {

    // Campos del formulario de nueva tarea
    private lateinit var textTaskName: EditText
    private lateinit var textTaskSubject: EditText
    private lateinit var sliderNumberPomodoros: Slider
    private lateinit var textViewPomodoroCount: TextView
    private lateinit var buttonSaveTask: Button

    private val viewModel: NewTaskViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_newtask, container, false)

        // Cargar sugerencias desde la BD
        viewModel.loadSubjects()

        // Configurar el AutoCompleteTextView para los temas
        viewModel.subjects.observe(viewLifecycleOwner) { subjectList ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, subjectList)
            (textTaskSubject as? AutoCompleteTextView)?.setAdapter(adapter)
        }

        // Enable back button in the ActionBar
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Inicializar los campos del formulario
        textTaskName = view.findViewById(R.id.editTextTaskName)
        textTaskSubject = view.findViewById(R.id.editTextSubject)
        sliderNumberPomodoros = view.findViewById(R.id.sliderPomodoros)
        buttonSaveTask = view.findViewById(R.id.buttonSave)
        textViewPomodoroCount = view.findViewById(R.id.textViewPomodoroCount)

        // Configurar el Slider
        sliderNumberPomodoros.addOnChangeListener { _, value, _ ->
            val pomodorosText = "${value.toInt()} pomodoros"
            textViewPomodoroCount.text = pomodorosText
        }

        // Establecer valor inicial
        textViewPomodoroCount.text = "${sliderNumberPomodoros.value.toInt()} pomodoros"

        // Configurar el botón de guardar tarea
        buttonSaveTask.setOnClickListener {
            val taskName = textTaskName.text.toString()
            val taskSubject = textTaskSubject.text.toString()

            // Validar los campos
            if (taskName.isEmpty() || taskSubject.isEmpty() || sliderNumberPomodoros.value.toInt() <= 0) {
                // Mostrar un mensaje de error
                textTaskName.error = "Please fill all fields"
                return@setOnClickListener
            }

            // Aquí puedes guardar la tarea usando el ViewModel
            viewModel.insertTask(
                taskName,
                taskSubject,
                sliderNumberPomodoros.value.toInt()
            )
            // Volver a la pantalla anterior
            findNavController().navigateUp()
        }

        return view
    }

    // Con esto puedo volver a la pantalla anterior
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // Enable menu handling
    }

    // Con esto puedo volver a la pantalla anterior
    @Deprecated("Deprecated in Java")
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