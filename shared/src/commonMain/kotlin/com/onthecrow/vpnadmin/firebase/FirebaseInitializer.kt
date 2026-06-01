package com.onthecrow.vpnadmin.firebase

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * Platform-specific bootstrap. Loads admin credentials from a local properties file
 * (gitignored), initializes the Firebase app on JVM (Android does it automatically
 * via google-services.json + ContentProvider), and signs in with the admin account.
 */
expect object FirebaseInitializer {
    suspend fun initAndSignIn(): Result<FirebaseHandles>
}

class FirebaseHandles(
    val firestore: FirebaseFirestore,
    val auth: FirebaseAuth,
)
