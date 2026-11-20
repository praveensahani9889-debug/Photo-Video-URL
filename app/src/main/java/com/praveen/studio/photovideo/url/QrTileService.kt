package com.praveen.studio.photovideo.url

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile (the icons next to Wi-Fi, Data, Sound...): one tap opens the QR scanner of the app.
 * The user adds it once: pull down the quick settings, tap the pencil / "Edit" and drag "QR scanner" up.
 */
class QrTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = getString(R.string.qr_tile_label)
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // the camera needs an unlocked phone: the system asks for the unlock first
        if (isLocked) {
            unlockAndRun { openScanner() }
        } else {
            openScanner()
        }
    }

    private fun openScanner() {
        val intent = Intent(this, LinkSearchActivity::class.java).apply {
            putExtra(LinkSearchActivity.EXTRA_OPEN_SCANNER, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Throwable) {
            // nothing to do: the tile simply does not open anything
        }
    }
}
