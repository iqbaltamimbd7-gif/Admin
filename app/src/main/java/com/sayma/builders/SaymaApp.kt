package com.sayma.builders

import android.app.Application
import com.sayma.builders.data.Api

class SaymaApp : Application() {
    lateinit var api: Api
        private set

    override fun onCreate() {
        super.onCreate()
        api = Api(this)
    }
}
