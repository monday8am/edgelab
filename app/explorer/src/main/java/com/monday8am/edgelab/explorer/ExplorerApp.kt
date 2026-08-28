package com.monday8am.edgelab.explorer

import android.app.Application
import android.os.StrictMode
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.monday8am.edgelab.explorer.di.ServiceLocator

class ExplorerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().build())
        }

        if (FirebaseApp.getApps(this).isNotEmpty()) {
            val providerFactory =
                if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
                else PlayIntegrityAppCheckProviderFactory.getInstance()
            Firebase.appCheck.installAppCheckProviderFactory(providerFactory)
        }
    }
}
