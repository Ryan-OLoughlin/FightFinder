package org.wit.fightfinder.views.list

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import org.wit.fightfinder.FightFinderApplication
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.ActivityMainBinding
import org.wit.fightfinder.views.auth.openAuth
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    private val app get() = application as FightFinderApplication
    private lateinit var navController: NavController
    private lateinit var appBar: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        val signedIn = app.signedInUid != null
        enableEdgeToEdge()
        super.onCreate(savedInstanceState.takeIf { signedIn })
        if (!signedIn) {
            openAuth()
            return
        }

        if (Timber.treeCount() == 0) Timber.plant(Timber.DebugTree())

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController
        val drawerDestinations = setOf(R.id.gymList, R.id.gymMap)
        appBar = AppBarConfiguration(drawerDestinations, binding.drawer)
        setupActionBarWithNavController(navController, appBar)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = destination.id in drawerDestinations
            binding.drawer.setDrawerLockMode(
                if (topLevel) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            )
            if (topLevel) binding.navigation.setCheckedItem(destination.id)
        }
        binding.navigation.setNavigationItemSelectedListener { item ->
            binding.drawer.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.action_sign_out -> {
                    app.signOut()
                    openAuth()
                }
                R.id.gymList -> navController.popBackStack(R.id.gymList, inclusive = false)
                R.id.gymMap -> if (navController.currentDestination?.id != R.id.gymMap) {
                    navController.navigate(R.id.gymMap, null, navOptions {
                        launchSingleTop = true
                        anim {
                            enter = R.anim.screen_enter
                            exit = R.anim.screen_exit
                            popEnter = R.anim.screen_pop_enter
                            popExit = R.anim.screen_pop_exit
                        }
                    })
                }
            }
            true
        }

        val closeDrawerOnBack = object : OnBackPressedCallback(binding.drawer.isDrawerVisible(GravityCompat.START)) {
            override fun handleOnBackPressed() {
                binding.drawer.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, closeDrawerOnBack)
        binding.drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                closeDrawerOnBack.isEnabled = slideOffset > 0f
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBar) || super.onSupportNavigateUp()
}
