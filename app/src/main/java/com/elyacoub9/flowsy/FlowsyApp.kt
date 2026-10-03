package com.elyacoub9.flowsy

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class FlowsyApp : Application() {
    lateinit var container: AppContainer
        @VisibleForTesting internal set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** For ViewModel factories: `initializer { MyViewModel(container().x) }`. */
fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as FlowsyApp).container
