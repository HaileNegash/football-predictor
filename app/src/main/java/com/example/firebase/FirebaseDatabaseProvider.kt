package com.example.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseDatabaseProvider {
    private const val TAG = "FirebaseDbProvider"
    
    // User's specific Cloud Firestore Database ID from dashboard
    const val VAULT_DATABASE_ID = "ai-studio-aibrainkeyvault-0cc24613-6377-406e-89e2-f84faa5463fa"

    fun getFirestore(context: Context, useVaultDb: Boolean = true): FirebaseFirestore? {
        return try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getInstance()
            } ?: return null

            if (useVaultDb) {
                try {
                    FirebaseFirestore.getInstance(app, VAULT_DATABASE_ID)
                } catch (e: Throwable) {
                    Log.w(TAG, "Connecting to named vault database $VAULT_DATABASE_ID: ${e.message}, falling back to default")
                    FirebaseFirestore.getInstance(app)
                }
            } else {
                try {
                    FirebaseFirestore.getInstance(app)
                } catch (e: Throwable) {
                    FirebaseFirestore.getInstance(app, VAULT_DATABASE_ID)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing Firebase Firestore instance: ${e.message}", e)
            null
        }
    }
}
