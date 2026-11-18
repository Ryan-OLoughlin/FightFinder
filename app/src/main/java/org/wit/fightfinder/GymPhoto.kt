package org.wit.fightfinder

import android.net.Uri
import android.widget.ImageView
import coil.load

fun ImageView.showGymPhoto(imageUri: String?) {
    load(imageUri?.let(Uri::parse)) {
        val gallery = android.R.drawable.ic_menu_gallery
        placeholder(gallery)
        fallback(gallery)
        error(gallery)
    }
}
