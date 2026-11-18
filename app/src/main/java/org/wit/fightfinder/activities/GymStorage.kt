package org.wit.fightfinder.activities

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.wit.fightfinder.FightFinderApplication
import org.wit.fightfinder.R
import timber.log.Timber
import java.io.IOException

internal val Context.gymStore get() = (applicationContext as FightFinderApplication).gymStore

internal fun LifecycleOwner.saveGymChange(context: Context, button: View? = null, action: suspend () -> Unit) {
    button?.isEnabled = false
    lifecycleScope.launch {
        try {
            action()
        } catch (e: IOException) {
            Timber.e(e, "Unable to save gym changes")
            Toast.makeText(context, R.string.gym_save_failed, Toast.LENGTH_LONG).show()
        } finally {
            button?.isEnabled = true
        }
    }
}
