package jorbel3.uv.es.pomodoroapp.data

import android.content.Context
import jorbel3.uv.es.pomodoroapp.R

/**
 * Clase singleton para gestionar la configuración del Pomodoro en toda la aplicación
 */
class PomodoroSettingsManager (context: Context) {

    // Contexto de la aplicación y SharedPreferences
    private val appContext = context.applicationContext
    private val sharedPref = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    companion object {
        // Constantes estáticas para las claves de SharedPreferences
        private const val PREFS_NAME = "pomodoro_settings"
        private const val KEY_POMODORO_TIME = "pomodoro_time"
        private const val KEY_SHORT_BREAK_TIME = "short_break_time"
        private const val KEY_LONG_BREAK_TIME = "long_break_time"
        private const val KEY_LONG_BREAK_INTERVAL = "long_break_interval"

        // Variable para implementar Singleton
        @Volatile
        private var INSTANCE: PomodoroSettingsManager? = null

        // Metodo para obtener la única instancia
        fun getInstance(context: Context): PomodoroSettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PomodoroSettingsManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Metodo para obtener el tiempo del Pomodoro en minutos
     * @return Tiempo del Pomodoro en minutos
     */
    fun getPomodoroTime(): Int {
        return sharedPref.getInt(KEY_POMODORO_TIME, appContext.resources.getInteger(R.integer.pomodoro_time))
    }

    /**
     * Metodo para obtener el tiempo del descanso corto en minutos
     * @return Tiempo del descanso corto en minutos
     */
    fun getShortBreakTime(): Int {
        return sharedPref.getInt(KEY_SHORT_BREAK_TIME, appContext.resources.getInteger(R.integer.short_break_time))
    }

    /**
     * Metodo para obtener el tiempo del descanso largo en minutos
     * @return Tiempo del descanso largo en minutos
     */
    fun getLongBreakTime(): Int {
        return sharedPref.getInt(KEY_LONG_BREAK_TIME, appContext.resources.getInteger(R.integer.long_break_time))
    }

    /**
     * Metodo para obtener el intervalo del descanso largo
     * @return Intervalo del descanso largo
     */
    fun getLongBreakInterval(): Int {
        return sharedPref.getInt(KEY_LONG_BREAK_INTERVAL, appContext.resources.getInteger(R.integer.long_break_interval))
    }

    /**
     * Metodo para guardar la configuración del Pomodoro en SharedPreferences
     * @param pomodoroTime Tiempo del Pomodoro en minutos
     * @param shortBreakTime Tiempo del descanso corto en minutos
     * @param longBreakTime Tiempo del descanso largo en minutos
     * @param longBreakInterval Intervalo para el descanso largo
     */
    fun saveSettings(
        pomodoroTime: Int,
        shortBreakTime: Int,
        longBreakTime: Int,
        longBreakInterval: Int
    ) {
        sharedPref.edit().apply {
            putInt(KEY_POMODORO_TIME, pomodoroTime)
            putInt(KEY_SHORT_BREAK_TIME, shortBreakTime)
            putInt(KEY_LONG_BREAK_TIME, longBreakTime)
            putInt(KEY_LONG_BREAK_INTERVAL, longBreakInterval)
            apply()
        }
    }
}