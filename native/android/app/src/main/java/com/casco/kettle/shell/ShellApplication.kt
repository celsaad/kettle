package com.casco.kettle.shell

import android.app.Application
import android.os.SystemClock

class ShellApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    processStartedAt = SystemClock.elapsedRealtime()
  }

  companion object {
    var processStartedAt = 0L
  }
}
