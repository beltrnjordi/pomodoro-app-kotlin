package jorbel3.uv.es.pomodoroapp.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import jorbel3.uv.es.pomodoroapp.MainActivity
import jorbel3.uv.es.pomodoroapp.PomodoroService
import jorbel3.uv.es.pomodoroapp.R
import jorbel3.uv.es.pomodoroapp.data.PomodoroSettingsManager
import jorbel3.uv.es.pomodoroapp.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var settingsManager: PomodoroSettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        settingsManager = PomodoroSettingsManager.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load saved settings
        loadSettings()

        // Setup UI interactions
        setupSliders()
        setupTextInputs()
        setupChips()
        setupSaveButton()
    }

    /**
     * Cargar la configuración guardada en la UI.
     * Se llama al iniciar el fragmento.
     */
    private fun loadSettings() {
        // Cargar valores guardados de la configuración
        val pomodoroTime = settingsManager.getPomodoroTime()
        val shortBreakTime = settingsManager.getShortBreakTime()
        val longBreakTime = settingsManager.getLongBreakTime()
        val longBreakInterval = settingsManager.getLongBreakInterval()


        // Apply values to UI components
        binding.sliderPomodoro.value = pomodoroTime.toFloat()
        binding.editPomodoroLength.setText(pomodoroTime.toString())

        binding.sliderShortBreak.value = shortBreakTime.toFloat()
        binding.editShortBreak.setText(shortBreakTime.toString())

        binding.sliderLongBreak.value = longBreakTime.toFloat()
        binding.editLongBreak.setText(longBreakTime.toString())

        binding.editPomodoroCount.setText(longBreakInterval.toString())
        selectChipByValue(longBreakInterval)
    }

    /**
     * Selecciona el chip correspondiente al valor dado. (Para saber cuántos pomodoros se hacen antes de un descanso largo)
     * @param value El valor del chip a seleccionar.
     */
    private fun selectChipByValue(value: Int) {
        val chipId = when (value) {
            2 -> R.id.chip2
            3 -> R.id.chip3
            4 -> R.id.chip4
            5 -> R.id.chip5
            else -> R.id.chip4 // Default to 4
        }

        binding.chipGroupPomodoroCount.check(chipId)
    }

    /**
     * Configura los sliders y sus listeners.
     * Se llama al iniciar el fragmento.
     */
    private fun setupSliders() {
        // Pomodoro time slider
        binding.sliderPomodoro.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                binding.editPomodoroLength.setText(value.toInt().toString())
            }
        }

        // Short break slider
        binding.sliderShortBreak.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                binding.editShortBreak.setText(value.toInt().toString())
            }
        }

        // Long break slider
        binding.sliderLongBreak.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                binding.editLongBreak.setText(value.toInt().toString())
            }
        }
    }

    /**
     * Configura los EditText y sus listeners.
     * Se llama al iniciar el fragmento.
     */
    private fun setupTextInputs() {

        // Duración del pomodoro
        binding.editPomodoroLength.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { value ->
                    if (value in 5..60 && binding.sliderPomodoro.value.toInt() != value) {
                        binding.sliderPomodoro.value = value.toFloat()
                    }
                }
            }
        })

        // Duración del descanso corto
        binding.editShortBreak.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { value ->
                    if (value in 1..15 && binding.sliderShortBreak.value.toInt() != value) {
                        binding.sliderShortBreak.value = value.toFloat()
                    }
                }
            }
        })

        // Duración del descanso largo
        binding.editLongBreak.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { value ->
                    if (value in 5..30 && binding.sliderLongBreak.value.toInt() != value) {
                        binding.sliderLongBreak.value = value.toFloat()
                    }
                }
            }
        })

        // Número de pomodoros antes del descanso largo
        binding.editPomodoroCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { value ->
                    if (value in 2..5) {
                        selectChipByValue(value)
                    }
                }
            }
        })
    }

    /**
     * Configura los chips y su listener.
     * Se llama al iniciar el fragmento.
     */
    private fun setupChips() {
        binding.chipGroupPomodoroCount.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                binding.editPomodoroCount.setText(chip.text.toString())
            }
        }
    }

    /**
     * Configura el botón de guardar y su listener.
     * Se llama al iniciar el fragmento.
     */
    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            saveSettings()
        }
    }

    /**
     * Guarda la configuración actual en el gestor de configuración.
     * Se llama al hacer clic en el botón de guardar.
     */
    private fun saveSettings() {
        // Get values from UI
        val pomodoroTime = binding.editPomodoroLength.text.toString().toIntOrNull()
            ?: resources.getInteger(R.integer.pomodoro_time)
        val shortBreakTime = binding.editShortBreak.text.toString().toIntOrNull()
            ?: resources.getInteger(R.integer.short_break_time)
        val longBreakTime = binding.editLongBreak.text.toString().toIntOrNull()
            ?: resources.getInteger(R.integer.long_break_time)
        val longBreakInterval = binding.editPomodoroCount.text.toString().toIntOrNull()
            ?: resources.getInteger(R.integer.long_break_interval)

        // Save settings
        settingsManager.saveSettings(
            pomodoroTime,
            shortBreakTime,
            longBreakTime,
            longBreakInterval
        )

        // Notify service immediately
        (requireActivity() as? MainActivity)?.getPomodoroService()?.let { service ->
            service.notifySettingsChanged()
        }

        Toast.makeText(context, "Configuración actualizada", Toast.LENGTH_SHORT).show()

        // Volver a la pantalla anterior
        requireActivity().supportFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}