package com.applab.batterypilot

import android.app.Application
import com.applab.batterypilot.collect.Guard
import com.applab.batterypilot.collect.SampleWorker

class BatteryPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.ensureChannel(this)
        SampleWorker.schedule(this)
    }
}
