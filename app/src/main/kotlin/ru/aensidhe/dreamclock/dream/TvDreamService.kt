package ru.aensidhe.dreamclock.dream

import android.service.dreams.DreamService

class TvDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false
        setContentView(android.widget.FrameLayout(this))
    }
}
