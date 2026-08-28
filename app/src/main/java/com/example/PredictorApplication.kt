package com.example

import android.app.Application
import android.util.Log

class PredictorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("PredictorApp", "Smart Football Predictor initialized with local storage engine.")
    }
}
