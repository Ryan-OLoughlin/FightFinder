package org.wit.fightfinder.views.map

import android.content.res.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

internal fun MapView.configureGymMap() {
    setDestroyMode(false)
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (night == Configuration.UI_MODE_NIGHT_YES) {
        overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
    }
}
