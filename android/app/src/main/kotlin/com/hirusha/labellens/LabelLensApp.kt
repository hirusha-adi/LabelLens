package com.hirusha.labellens

import android.app.Application
import com.hirusha.labellens.data.Store

class LabelLensApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this).also { it.start() }
    }
}
