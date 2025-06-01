package jorbel3.uv.es.pomodoroapp.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import jorbel3.uv.es.pomodoroapp.PomodoroService
import jorbel3.uv.es.pomodoroapp.R

class HomeViewModel : ViewModel() {

    // LiveData para el estado del temporizador (si está en ejecución o no)
    private val _timerRunning = MutableLiveData<Boolean>(false)
    val timerRunning: LiveData<Boolean> = _timerRunning

    // LiveData para el progreso del temporizador (0 a 100)
    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    // LiveData para notificar cuando se complete el Pomodoro
    private val _timerComplete = MutableLiveData<Boolean>(false)
    val timerComplete: LiveData<Boolean> = _timerComplete

    // Calcular valor inicial segun la configuración del temporizador
    private var totalTimeInMillis: Long = 25 * 60 * 1000 // Default value
    val minutes = totalTimeInMillis / 1000 / 60
    val seconds = (totalTimeInMillis / 1000) % 60

    // LiveData para el texto del temporizador
    private val _timeText = MutableLiveData<String>(String.format("%02d:%02d", minutes, seconds))
    val timeText: LiveData<String> = _timeText

    // LiveData para el contador de pomodoros completos
    private val _pomodorosCompleted = MutableLiveData<Int>(0)
    val pomodorosCompleted: LiveData<Int> = _pomodorosCompleted

    // LiveData para el número de pomodoros totales
    private val _pomodorosTotal = MutableLiveData<Int>(0)
    val pomodorosTotal: LiveData<Int> = _pomodorosTotal

    // LiveData para el estado actual del temporizador (Pomodoro, descanso corto o largo)
    private val _timerState = MutableLiveData<PomodoroService.TimerState>(PomodoroService.TimerState.POMODORO)
    val timerState: LiveData<PomodoroService.TimerState> = _timerState

    private var pomodoroService: PomodoroService? = null

    /**
     * Función para enlazar el servicio y observar los cambios en el temporizador
     * @param service Servicio de Pomodoro
     */
    fun bindService(service: PomodoroService) {
        pomodoroService = service

        // Actualizar el tiempo total al iniciar el servicio
        updateTotalTime(service.getTotalTimeInMillis())
        updateTime(service.currentTime.value ?: 0L)

        // Observar cambios en el tiempo
        service.currentTime.observeForever { updateTime(it) }

        // Observar cambios en el estado del temporizador
        service.timerState.observeForever { state ->
            _timerState.postValue(state)
            // Actualizar el tiempo total cuando cambia el estado
            totalTimeInMillis = service.getTotalTimeInMillis()
        }

        // Observar cambios en el estado de ejecución del temporizador
        service.isTimerRunning.observeForever {
            _timerRunning.postValue(it)

            // Si el temporizador se detiene y el tiempo restante es 0, se ha completado
            if (!it && service.currentTime.value == 0L) {
                _timerComplete.postValue(true)
            } else {
                _timerComplete.postValue(false)
            }
        }

        // Observar cambios en el número de pomodoros totales
        service.pomodoroCount.observeForever { count ->
            _pomodorosCompleted.postValue(count)
        }
    }

    /**
     * Función para actualizar el tiempo total del temporizador
     * @param newTotalTime Nuevo tiempo total en milisegundos
     */
    fun updateTotalTime(newTotalTime: Long) {
        totalTimeInMillis = newTotalTime
        // Force progress recalculation
        pomodoroService?.currentTime?.value?.let {
            updateTime(it)
        }
    }

    /**
     * Función para iniciar el temporizador de forma segura
     * En el caso de que el servicio no esté vinculado, no se hace nada
     */
    fun startTimer() {
        pomodoroService?.startTimer()
    }

    /**
     * Función para pausar el temporizador de forma segura
     * En el caso de que el servicio no esté vinculado, no se hace nada
     */
    fun pauseTimer() {
        pomodoroService?.pauseTimer()
    }

    /**
     * Función para detener el temporizador de forma segura
     * En el caso de que el servicio no esté vinculado, no se hace nada
     */
    fun stopTimer() {
        pomodoroService?.stopTimer()
    }

    /**
     * Función para avanzar al siguiente estado en el ciclo Pomodoro
     */
    fun skipToNextState() {
        pomodoroService?.skipToNextState()
    }

    /**
     * Función para actualizar el texto del temporizador y la barra de progreso circular
     * @param remainingMillis Tiempo restante en milisegundos
     */
    fun updateTime(remainingMillis: Long) {
        val minutes = remainingMillis / 1000 / 60
        val seconds = (remainingMillis / 1000) % 60
        _timeText.postValue(String.format("%02d:%02d", minutes, seconds))

        // Calculate and update progress (0 to 100)
        if (totalTimeInMillis > 0) {
            val progressValue = ((totalTimeInMillis - remainingMillis) * 100 / totalTimeInMillis).toInt()
            _progress.postValue(progressValue)
        } else {
            _progress.postValue(0)
        }
    }

    // Métodos públicos para actualizar los valores
    fun setTimerState(state: PomodoroService.TimerState) {
        _timerState.postValue(state)
    }

    fun setPomodorosCompleted(count: Int) {
        _pomodorosCompleted.postValue(count)
    }

    /**
     * Comprueba si se ha completado el número total de Pomodoros de la sesión
     */
    fun checkTaskCompletion(completedPomodoros: Int, totalPomodoros: Int): Boolean {
        return completedPomodoros >= totalPomodoros
    }
}