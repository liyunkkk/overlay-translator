package com.gameocr.app.root

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootCapabilities @Inject constructor(
    private val rootManager: RootManager
) {
    fun isRootReady(context: Context): Boolean {
        return rootManager.isRootAvailable()
    }
}
