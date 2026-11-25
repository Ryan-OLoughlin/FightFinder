package org.wit.fightfinder.views.map

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.core.content.ContextCompat
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.wit.fightfinder.R

internal fun MapView.configureGymMap() {
    setDestroyMode(false)
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    if (isNightMap()) {
        overlayManager.tilesOverlay.apply {
            setColorFilter(nightTileFilter)
            loadingBackgroundColor = Color.rgb(28, 33, 43)
            loadingLineColor = Color.rgb(48, 56, 70)
        }
    }
}

internal fun Marker.styleSavedGymPin(map: MapView) {
    if (map.isNightMap()) {
        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_night_gym_pin)
    }
}

private fun MapView.isNightMap(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private val nightTileFilter = ColorMatrixColorFilter(ColorMatrix().apply {
    // Mute the inverted colours and widen the difference between pale road and land pixels.
    val contrast = 1.6f
    val white = 255f * contrast
    setSaturation(0.15f)
    postConcat(ColorMatrix(floatArrayOf(
        -contrast, 0f, 0f, 0f, white + 24f,
        0f, -contrast, 0f, 0f, white + 29f,
        0f, 0f, -contrast, 0f, white + 39f,
        0f, 0f, 0f, 1f, 0f
    )))
})
