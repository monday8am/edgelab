package com.monday8am.edgelab.explorer

import android.app.Application
import android.os.StrictMode
import com.monday8am.edgelab.explorer.di.ServiceLocator

class ExplorerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().build())
    }

    override fun onTerminate() {
        super.onTerminate()
        ServiceLocator.dispose()
    }
}
