package com.vocalclear.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for VocalClear app.
 * Required for Hilt dependency injection initialization.
 */
@HiltAndroidApp
class VocalClearApp : Application()
