package com.praveen.studio.photovideo.url.data.remote

import com.praveen.studio.photovideo.url.data.model.AppControl
import com.praveen.studio.photovideo.url.data.model.AppControlStore

/** Loads the remote switches (maintenance, update, demo) into [AppControlStore]. */
object AppControlManager {
    /** Never throws. When offline the last known values stay. */
    suspend fun refresh(): AppControl {
        val fetched = FirebaseDatabaseManager().fetchAppControl()
        if (fetched != null) AppControlStore.update(fetched)
        return AppControlStore.current
    }
}
