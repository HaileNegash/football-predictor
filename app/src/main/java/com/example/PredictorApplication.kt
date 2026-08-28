package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class PredictorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.i("PredictorApp", "Firebase App initialized successfully.")
        } catch (e: Exception) {
            Log.w("PredictorApp", "Firebase initialization deferred or handled automatically: ${e.message}")
        }
    }
}
