package ru.aensidhe.dreamclock

import android.app.Application
import ru.aensidhe.dreamclock.settings.CrashLog

class DreamClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
