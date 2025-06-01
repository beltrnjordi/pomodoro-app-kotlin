package jorbel3.uv.es.pomodoroapp

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import jorbel3.uv.es.pomodoroapp.db.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.media.RingtoneManager
import android.util.Log
import androidx.core.content.ContextCompat
import jorbel3.uv.es.pomodoroapp.data.PomodoroSettingsManager
import kotlinx.coroutines.withContext

/**
 * Servicio para manejar el temporizador Pomodoro.
 * Este servicio permite iniciar, pausar y detener el temporizador,
 * así como mostrar notificaciones y reproducir un sonido al finalizar.
 */
class PomodoroService : Service() {

    enum class TimerState {
        POMODORO, SHORT_BREAK, LONG_BREAK
    }

    private val binder = PomodoroBinder()
    private var countDownTimer: CountDownTimer? = null
    private var mediaPlayer: MediaPlayer? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var isForegroundService = false

    // Singleton para obtener los valores de configuración del temporizador
    // Inicialización perezosa en vez de lateinit
    private val settingsManager by lazy {
        PomodoroSettingsManager.getInstance(applicationContext)
    }

    // Valores de tiempo para los temporizadores (Pomodoro y descanso)
    private var pomodoroTimeMillis = 25 * 60 * 1000L
    private var shortBreakTimeMillis = 5 * 60 * 1000L
    private var longBreakTimeMillis = 15 * 60 * 1000L
    private var longBreakInterval = 4 // Número de Pomodoros antes de un descanso largo

    // Estado actual y contador de pomodoros
    private var currentState = TimerState.POMODORO
    private var completedPomodoros = 0
    private var timeLeftInMillis = 0L
    private var isRunning = false
    private var isTimerInitialized = false

    // LiveData para comunicar el estado con la UI
    val currentTime = MutableLiveData<Long>()
    val isTimerRunning = MutableLiveData<Boolean>()
    val timerState = MutableLiveData<TimerState>()
    val pomodoroCount = MutableLiveData<Int>()

    // Identificador de la tarea actual
    private var currentTaskId: Long? = null

    inner class PomodoroBinder : Binder() {
        fun getService(): PomodoroService = this@PomodoroService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Registrar el BroadcastReceiver
        val filter = IntentFilter(ACTION_REFRESH_SETTINGS)
        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            filter,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_NOT_EXPORTED
            } else {
                ContextCompat.RECEIVER_VISIBLE_TO_INSTANT_APPS
            }
        )

        createNotificationChannel()
        loadSettingsFromManager()
        isTimerRunning.value = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isTimerInitialized && intent?.getBooleanExtra("recover", false) != true) {
            intent?.getIntExtra("pomodoroTimeMinutes", -1)?.takeIf { it > 0 }?.let {
                pomodoroTimeMillis = it * 60 * 1000L
            }
            intent?.getLongExtra("taskId", -1)?.takeIf { it != -1L }?.let {
                currentTaskId = it
            }
            timeLeftInMillis = when (currentState) {
                TimerState.POMODORO -> pomodoroTimeMillis
                TimerState.SHORT_BREAK -> shortBreakTimeMillis
                TimerState.LONG_BREAK -> longBreakTimeMillis
            }
            isTimerInitialized = true
            // Update currentTime LiveData so the UI displays the correct initial time
            currentTime.postValue(timeLeftInMillis)
        }

        if (!isForegroundService) {
            startForeground(TIMER_NOTIFICATION_ID, createPersistentNotification())
            isForegroundService = true
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Cuando se remueve la app del recents, mantener el servicio vivo
        val restartIntent = Intent(applicationContext, PomodoroService::class.java).apply {
            putExtra("pomodoroTimeMinutes", pomodoroTimeMillis / 60000)
            currentTaskId?.let { putExtra("taskId", it) }
        }

        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onDestroy() {
        try {
            // 1. Cancelar cualquier temporizador en ejecución
            countDownTimer?.cancel()

            // 2. Liberar el MediaPlayer si está en uso
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null

            // Desregistrar el BroadcastReceiver
            try {
                unregisterReceiver(settingsReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver might not be registered
            }

            // Reset state
            isRunning = false
            isForegroundService = false

            // Stop foreground service properly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

        } catch (e: Exception) {
            Log.e("PomodoroService", "Error during service destruction", e)
        } finally {
            super.onDestroy()
        }
    }

    /**
     * Métodos para manejar el temporizador, su estado y la tarea actual.
     */

    /**
     * Establece la tarea actual y reinicia el temporizador.
     * Si la tarea es diferente a la anterior, se reinicia el temporizador.
     */
    fun setCurrentTask(taskId: Long?) {
        // Cancelar el temporizador actual si está en ejecución
        countDownTimer?.cancel()
        isRunning = false
        isTimerRunning.postValue(false)

        val oldTaskId = currentTaskId
        currentTaskId = taskId

        // Si la tarea es diferente a la anterior, reiniciar el temporizador
        if (taskId != oldTaskId) {
            // Reiniciar el temporizador al estado Pomodoro
            currentState = TimerState.POMODORO
            timeLeftInMillis = pomodoroTimeMillis

            // Actualizar el estado y el tiempo restante
            CoroutineScope(Dispatchers.IO).launch {
                val completed = getCompletedPomodoros(taskId ?: -1, applicationContext)
                completedPomodoros = completed

                // Actualizar el estado y el tiempo restante en el hilo principal
                withContext(Dispatchers.Main) {
                    timerState.postValue(currentState)
                    currentTime.postValue(timeLeftInMillis)
                    pomodoroCount.postValue(completedPomodoros)
                }
            }
        }
    }

    /**
     * Obtiene el número de pomodoros completados para una tarea específica.
     * @param taskId ID de la tarea
     * @param context Contexto de la aplicación
     * @return Número de pomodoros completados
     */
    suspend fun getCompletedPomodoros(taskId: Long, context: Context): Int {
        // Get an instance of the TaskDao from your TaskDatabase singleton
        val taskDao = TaskDatabase.getInstance(context).taskDao()
        // Retrieve the Task entity by its id
        val task = taskDao.getTaskById(taskId.toInt())
        // Return the number of completed pomodoros from the task, or 0 if no task was found
        return task?.numberCompletedPomodoros ?: 0
    }

    /**
     * Carga la configuración del temporizador desde el SettingsManager.
     * Se llama al iniciar el servicio y al recibir un broadcast de actualización de configuración.
     */
    private fun loadSettingsFromManager() {
        pomodoroTimeMillis = settingsManager.getPomodoroTime() * 60 * 1000L
        shortBreakTimeMillis = settingsManager.getShortBreakTime() * 60 * 1000L
        longBreakTimeMillis = settingsManager.getLongBreakTime() * 60 * 1000L
        longBreakInterval = settingsManager.getLongBreakInterval()
        Log.d("PomodoroService", "Settings loaded: $pomodoroTimeMillis, $shortBreakTimeMillis, $longBreakTimeMillis, $longBreakInterval")
    }

    /**
     * Metodo para refrescar la configuración del temporizador.
     * Se llama al recibir un broadcast de actualización de configuración.
     */
    fun refreshSettings() {
        loadSettingsFromManager()

        // Actualizar los valores según el estado actual
        timeLeftInMillis = when (currentState) {
            TimerState.POMODORO -> pomodoroTimeMillis
            TimerState.SHORT_BREAK -> shortBreakTimeMillis
            TimerState.LONG_BREAK -> longBreakTimeMillis
        }

        // Notificar a los observadores
        currentTime.postValue(timeLeftInMillis)
        timerState.postValue(currentState)

        // Opcional: Enviar broadcast de confirmación
        sendBroadcast(Intent(ACTION_SETTINGS_UPDATED))
    }


    /**
     * Metodo para recuperar el estado actual del temporizador.
     */
    fun getCurrentState(): Bundle {
        return Bundle().apply {
            putLong("timeLeft", timeLeftInMillis)
            putString("timerState", currentState.name)
            putInt("completedPomodoros", completedPomodoros)
            putBoolean("isRunning", isRunning)
        }
    }

    /**
     * Metodo para restaurar el estado del temporizador
     */
    fun restoreState(bundle: Bundle) {
        bundle.apply {
            timeLeftInMillis = getLong("timeLeft", pomodoroTimeMillis)
            currentState = TimerState.valueOf(getString("timerState", TimerState.POMODORO.name))
            completedPomodoros = getInt("completedPomodoros", 0)
            isRunning = getBoolean("isRunning", false)

            currentTime.postValue(timeLeftInMillis)
            timerState.postValue(currentState)
            pomodoroCount.postValue(completedPomodoros)
            isTimerRunning.postValue(isRunning)
        }
    }

    /**
     * Métodos para iniciar, pausar y detener el temporizador.
     */
    fun startTimer() {
        if (!isRunning) {
            // Reset and set timeLeftInMillis accordingly
            startForegroundServiceIfNeeded()  // Ensure the service enters foreground mode before starting
            startCountdownTimer(timeLeftInMillis)
        }
    }
    fun pauseTimer() {
        countDownTimer?.cancel()
        isRunning = false
        isTimerRunning.postValue(false)
        updateNotification(timeLeftInMillis, getStateDescription())
    }
    fun stopTimer() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftInMillis = pomodoroTimeMillis
        currentTime.postValue(timeLeftInMillis)
        isTimerRunning.postValue(false)
        if (isForegroundService) {
            stopForegroundService()
            isForegroundService = false
        }
    }

    /**
     * Métodos para cambiar entre los diferentes estados del temporizador.
     */
    fun switchToPomodoro() {
        countDownTimer?.cancel()
        currentState = TimerState.POMODORO
        timeLeftInMillis = pomodoroTimeMillis
        currentTime.postValue(timeLeftInMillis)
        timerState.postValue(currentState)
        isRunning = false
        isTimerRunning.postValue(false)
        updateNotification(timeLeftInMillis, getStateDescription())
    }
    fun switchToShortBreak() {
        countDownTimer?.cancel()
        currentState = TimerState.SHORT_BREAK
        timeLeftInMillis = shortBreakTimeMillis
        currentTime.postValue(timeLeftInMillis)
        timerState.postValue(currentState)
        isRunning = false
        isTimerRunning.postValue(false)
        updateNotification(timeLeftInMillis, getStateDescription())
    }
    fun switchToLongBreak() {
        countDownTimer?.cancel()
        currentState = TimerState.LONG_BREAK
        timeLeftInMillis = longBreakTimeMillis
        currentTime.postValue(timeLeftInMillis)
        timerState.postValue(currentState)
        isRunning = false
        isTimerRunning.postValue(false)
        updateNotification(timeLeftInMillis, getStateDescription())
    }

    /**
     * Métodos para obtener el tiempo de cada estado.
     */
    fun getTotalTimeInMillis(): Long {
        return when (currentState) {
            TimerState.POMODORO -> pomodoroTimeMillis
            TimerState.SHORT_BREAK -> shortBreakTimeMillis
            TimerState.LONG_BREAK -> longBreakTimeMillis
        }
    }

    /**
     * Inicia el temporizador
     */
    private fun startCountdownTimer(durationMillis: Long) {
        isRunning = true
        isTimerRunning.postValue(true)

        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            /**
             * Método que se llama cada segundo.
             * Actualiza el tiempo restante y la notificación.
             */
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                currentTime.postValue(millisUntilFinished)
                updateNotification(millisUntilFinished, getStateDescription())
            }

            /**
             * Método que se llama cuando el temporizador termina.
             * Cambia el estado del temporizador y muestra una notificación de finalización.
             */
            override fun onFinish() {
                timeLeftInMillis = 0
                currentTime.postValue(0)
                isRunning = false
                isTimerRunning.postValue(false)

                when (currentState) {
                    TimerState.POMODORO -> finishPomodoro()
                    TimerState.SHORT_BREAK -> finishShortBreak()
                    TimerState.LONG_BREAK -> finishLongBreak()
                }
            }
        }.start()
    }

    /**
     * Finaliza el pomodoro y cambia al siguiente estado (descanso corto o largo).
     */
    private fun finishPomodoro() {

        // Reproduce el sonido de alarma y muestra la notificación de finalización
        playAlarm()

        // Incrementa el contador de pomodoros completados
        completedPomodoros++
        pomodoroCount.postValue(completedPomodoros)

        showCompletionNotification(
            when {
                completedPomodoros % longBreakInterval == 0 -> "¡Descanso largo iniciado!"
                else -> "¡Descanso corto iniciado!"
            }
        )

        // Limpia la notificación y el servicio
        clearNotificationsAndService()


        // Actualiza el estado del temporizador
        currentState = if (completedPomodoros % longBreakInterval == 0) {
            TimerState.LONG_BREAK
        } else {
            TimerState.SHORT_BREAK
        }

        // Reinicia el temporizador con el tiempo correspondiente
        timeLeftInMillis = when (currentState) {
            TimerState.LONG_BREAK -> longBreakTimeMillis
            TimerState.SHORT_BREAK -> shortBreakTimeMillis
            else -> pomodoroTimeMillis
        }

        timerState.postValue(currentState)
        currentTime.postValue(timeLeftInMillis)

        // Inicia el temporizador para el siguiente estado
        if (currentTaskId != null) {
            updateTaskCompletedPomodoro()
        }
    }

    /**
     * Actualiza la base de datos con el número de pomodoros completados.
     */
    private fun updateTaskCompletedPomodoro() {
        // Use a broadcast to notify UI components about completed pomodoro
        val intent = Intent("jorbel3.uv.es.pomodoroapp.POMODORO_COMPLETED")
        intent.putExtra("pomodorosCompleted", completedPomodoros)
        sendBroadcast(intent)

        // You would need to implement database update logic here
        // This depends on how you're tracking the current task
        // For example:
        currentTaskId?.let { taskId ->
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val taskDao = TaskDatabase.getInstance(applicationContext).taskDao()
                    val task = taskDao.getTaskById(taskId.toInt())
                    task?.let { currentTask ->
                        val updatedTask = currentTask.copy(numberCompletedPomodoros = currentTask.numberCompletedPomodoros + 1)
                        taskDao.updateTask(updatedTask)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun finishShortBreak() {
        // Show completion and clear ongoing notification
        showCompletionNotification("¡Descanso terminado! Vuelve al trabajo.")
        clearNotificationsAndService()
        playAlarm()

        // Update state
        currentState = TimerState.POMODORO
        timeLeftInMillis = pomodoroTimeMillis
        timerState.postValue(currentState)
        currentTime.postValue(timeLeftInMillis)
    }

    private fun finishLongBreak() {
        // Show completion and clear ongoing notification
        showCompletionNotification("¡Descanso largo terminado! Vuelve al trabajo.")
        clearNotificationsAndService()
        playAlarm()

        // Update state
        currentState = TimerState.POMODORO
        timeLeftInMillis = pomodoroTimeMillis
        timerState.postValue(currentState)
        currentTime.postValue(timeLeftInMillis)
    }

    fun skipToNextState() {
        countDownTimer?.cancel()
        isRunning = false
        isTimerRunning.postValue(false)

        // Clear ongoing notification first
        clearNotificationsAndService()

        // Handle state transition
        when (currentState) {
            TimerState.POMODORO -> finishPomodoro()
            TimerState.SHORT_BREAK -> finishShortBreak()
            TimerState.LONG_BREAK -> finishLongBreak()
        }
    }

    private fun getStateDescription(): String {
        return when (currentState) {
            TimerState.POMODORO -> "Pomodoro"
            TimerState.SHORT_BREAK -> "Descanso Corto"
            TimerState.LONG_BREAK -> "Descanso Largo"
        }
    }

    private fun playAlarm() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm)
        mediaPlayer?.start()
    }


    /**
     * Método para notificar cambios en la configuración del temporizador.
     * Se llama al recibir un broadcast de actualización de configuración.
     */
    fun notifySettingsChanged() {
        // Recargar la configuración del temporizador
        loadSettingsFromManager()

        // Actualizar el tiempo restante según el estado actual
        val newTimeLeftInMillis = when (currentState) {
            TimerState.POMODORO -> pomodoroTimeMillis
            TimerState.SHORT_BREAK -> shortBreakTimeMillis
            TimerState.LONG_BREAK -> longBreakTimeMillis
        }

        // Actualizar el temporizador solo si no está en ejecución
        if (!isRunning) {
            timeLeftInMillis = newTimeLeftInMillis
            currentTime.postValue(timeLeftInMillis)
        }

        // Actualizar el temporizador en la base de datos
        updateTotalTime(newTimeLeftInMillis)

        // Notify any observers
        timerState.postValue(currentState)
    }

    private fun updateTotalTime(newTimeInMillis: Long) {
        when (currentState) {
            TimerState.POMODORO -> pomodoroTimeMillis = newTimeInMillis
            TimerState.SHORT_BREAK -> shortBreakTimeMillis = newTimeInMillis
            TimerState.LONG_BREAK -> longBreakTimeMillis = newTimeInMillis
        }
    }

    private fun getCurrentStateText(): String {
        return when (currentState) {
            TimerState.POMODORO -> "Pomodoro"
            TimerState.SHORT_BREAK -> "Descanso Corto"
            TimerState.LONG_BREAK -> "Descanso Largo"
        }
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundServiceIfNeeded() {
        if (!isForegroundService) {
            val notification = createNotification(timeLeftInMillis, getCurrentStateText())
            startForeground(TIMER_NOTIFICATION_ID, notification)
            isForegroundService = true
        }
    }

    /**
     * Toda la lógica para las notificaciones a continuación
     */

    /**
     * Crea una notificación persistente que se muestra mientras la app está en ejecución.
     */
    private fun createPersistentNotification(): Notification {
        return NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setContentTitle("Pomodoro en progreso")
            .setContentText("La aplicación está manteniendo el temporizador activo")
            .setSmallIcon(R.drawable.tomato_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .build()
    }

    /**
     * Crea una notificación que muestra el tiempo restante y el estado del temporizador.
     */
    private fun createNotification(millisUntilFinished: Long, status: String): Notification {
        val minutes = millisUntilFinished / 1000 / 60
        val seconds = (millisUntilFinished / 1000) % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TIMER_CHANNEL_ID)
            .setContentTitle(status)
            .setContentText("Tiempo: $timeString | Pomodoros: $completedPomodoros")
            .setSmallIcon(R.drawable.tomato_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Important! Only alert once for this notification
            .setSound(null) // No sound for ongoing updates
            .setVibrate(null) // No vibration for ongoing updates
            .build()
    }

    /**
     * Actualiza la notificación con el tiempo restante y el estado del temporizador.
     */
    private fun updateNotification(millis: Long, status: String) {
        try {
            val notification = createNotification(millis, status)
            if (isForegroundService) {
                startForeground(TIMER_NOTIFICATION_ID, notification)
            } else {
                notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PomodoroService", "Error updating notification", e)
        }
    }

    /**
     * Muestra una notificación de finalización del pomodoro o descanso.
     * Si hay un ID de tarea, muestra el nombre de la tarea en la notificación.
     */
    private fun showCompletionNotification(message: String) {
        // Get task info if available
        val taskInfo = currentTaskId?.let { taskId ->
            // Launch coroutine to get task info asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val taskDao = TaskDatabase.getInstance(applicationContext).taskDao()
                    val task = taskDao.getTaskById(taskId.toInt())
                    task?.let {
                        withContext(Dispatchers.Main) {
                            // Mostrar notificación con nombre de tarea
                            showTaskCompletionNotification(message, it.title)
                        }
                    } ?: run {
                        // Si no se encuentra la tarea, mostrar notificación genérica
                        withContext(Dispatchers.Main) {
                            showBasicCompletionNotification(message)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        showBasicCompletionNotification(message)
                    }
                }
            }
            return@let
        }

        // Si no hay ID de tarea, mostrar una notificación genérica (sin nombre de tarea)
        if (currentTaskId == null) {
            showBasicCompletionNotification(message)
        }
    }

    /**
     * Crea los canales de notificación necesarios para Android O y versiones posteriores.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_MIN // Changed to MIN for less intrusive updates
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                description = "Muestra el estado actual del temporizador"
            }

            val completionChannel = NotificationChannel(
                COMPLETION_CHANNEL_ID,
                "Pomodoro Completions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
                description = "Alertas cuando se completa un pomodoro o descanso"
            }

            notificationManager.createNotificationChannels(listOf(timerChannel, completionChannel))
        }
    }

    /**
     * Cancela la notificación y detiene el servicio en primer plano.
     * Se llama cuando el temporizador se detiene o se completa.
     */
    private fun clearNotificationsAndService() {
        if (isForegroundService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundService = false
        }
        notificationManager.cancel(TIMER_NOTIFICATION_ID)
    }

    /**
     * Muestra una notificación de finalización básica.
     * Se utiliza cuando no hay tarea asociada o no se puede recuperar la información de la tarea.
     */
    private fun showBasicCompletionNotification(message: String) {
        val notification = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setContentTitle(message)
            .setContentText("Pomodoros completados: $completedPomodoros")
            .setSmallIcon(R.drawable.tomato_icon)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500)) // Custom vibration pattern
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    /**
     * Muestra una notificación de finalización con el nombre de la tarea.
     * Se utiliza cuando se completa un pomodoro o descanso asociado a una tarea.
     */
    private fun showTaskCompletionNotification(message: String, taskName: String) {
        val notification = NotificationCompat.Builder(this, COMPLETION_CHANNEL_ID)
            .setContentTitle(message)
            .setContentText("\"$taskName\" | Pomodoros: $completedPomodoros")
            .setSmallIcon(R.drawable.tomato_icon)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500)) // Custom vibration pattern
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        notificationManager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    // Define these constants at the top of your class
    companion object {
        private const val TIMER_NOTIFICATION_ID = 1
        private const val COMPLETION_NOTIFICATION_ID = 2
        private const val TIMER_CHANNEL_ID = "pomodoro_timer_channel"
        private const val COMPLETION_CHANNEL_ID = "pomodoro_completion_channel"

        // Action constants for notification controls
        const val ACTION_STOP_TIMER = "jorbel3.uv.es.pomodoroapp.STOP_TIMER"
        const val ACTION_PAUSE_TIMER = "jorbel3.uv.es.pomodoroapp.PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "jorbel3.uv.es.pomodoroapp.RESUME_TIMER"

        const val ACTION_REFRESH_SETTINGS = "jorbel3.uv.es.pomodoroapp.REFRESH_SETTINGS"
        const val ACTION_SETTINGS_UPDATED = "jorbel3.uv.es.pomodoroapp.SETTINGS_UPDATED"
    }

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REFRESH_SETTINGS -> refreshSettings()
            }
        }
    }
}