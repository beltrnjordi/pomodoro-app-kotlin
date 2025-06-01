package jorbel3.uv.es.pomodoroapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import jorbel3.uv.es.pomodoroapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var pomodoroService: PomodoroService? = null
    private var serviceBound = false


    // Conexión al servicio
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PomodoroService.PomodoroBinder
            pomodoroService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove any default action bar handling
        //WindowCompat.setDecorFitsSystemWindows(window, true)

        // Lanzar la solicitud de permiso para notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001 // Código de solicitud
                )
            }
        }

        // Instalación de la SplashScreen
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { true }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle system insets for the toolbar
        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply padding to AppBarLayout instead of Toolbar
            //binding.toolbar.setPadding(0,  insets.top , 0, 0)

            // Apply padding to bottom nav
            binding.navView.setPadding(0, 0, 0, insets.bottom)

            WindowInsetsCompat.CONSUMED
        }

        // Configurar toolbar y navegación
        setupNavigation()

        // Iniciar y vincular el servicio
        val serviceIntent = Intent(this, PomodoroService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        // Restaurar estado si existe
        savedInstanceState?.getBundle("pomodoroState")?.let {
            if (serviceBound) {
                pomodoroService?.restoreState(it)
            }
        }

        splashScreen.setKeepOnScreenCondition { false }
    }

    /**
     * Configura la navegación entre los fragments y la barra de navegación.
     */
    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Configurar la barra de navegación con el NavController
        navView.setupWithNavController(navController)

        // Vincular la toolbar con el NavController (mostrar título)
        binding.toolbar.setupWithNavController(
            navController,
            AppBarConfiguration(
                setOf(
                    R.id.navigation_home,
                    R.id.navigation_tasks,
                    R.id.navigation_settings
                )
            )
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (serviceBound) {
            pomodoroService?.getCurrentState()?.let {
                outState.putBundle("pomodoroState", it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }

    fun switchToTab(tabId: Int) {
        binding.navView.selectedItemId = tabId
    }

    fun getPomodoroService(): PomodoroService? {
        return if (serviceBound) pomodoroService else null
    }
}