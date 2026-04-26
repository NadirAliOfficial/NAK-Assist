package com.teamnak.nakassist

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile to toggle Away Mode from the notification shade.
 * User adds it manually via "Edit tiles" — standard Android behavior.
 */
class AwayModeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val newState = !MessageNotificationService.awayMode
        MessageNotificationService.awayMode = newState
        PersistenceHelper.saveAwayMode(applicationContext, newState)
        FloatingButtonManager.setAwayMode(newState)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isOn = MessageNotificationService.awayMode
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isOn) "Away: ON" else "Away: OFF"
        tile.subtitle = if (isOn) "Auto-replying" else "Manual"
        tile.updateTile()
    }
}
