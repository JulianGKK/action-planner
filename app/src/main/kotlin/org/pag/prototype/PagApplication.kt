package org.pag.prototype

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

/** Passive configuration only; CameraX is first requested by a conscious camera entry. */
class PagApplication : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig = CameraXConfig.Builder
        .fromConfig(Camera2Config.defaultConfig()).setMinimumLoggingLevel(Log.ERROR).build()
}
