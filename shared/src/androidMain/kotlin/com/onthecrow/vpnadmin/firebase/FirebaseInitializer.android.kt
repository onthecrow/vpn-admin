package com.onthecrow.vpnadmin.firebase

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import java.util.Properties

/**
 * On Android, the FirebaseApp is auto-initialized by the official SDK via
 * the FirebaseInitProvider (it reads `google-services.json` resources generated
 * by the Gms plugin). We only need the admin credentials.
 */
internal lateinit var appContext: Context

fun bootstrapAndroidFirebase(context: Context) {
    appContext = context.applicationContext
}

actual object FirebaseInitializer {
    actual suspend fun initAndSignIn(): Result<FirebaseHandles> = runCatching {
        val ctx = appContext
        val props = Properties().apply {
            ctx.assets.open("firebase-admin.properties").use { load(it) }
        }
        val email = requireNotNull(props.getProperty("admin.email")) {
            "admin.email missing in assets/firebase-admin.properties"
        }
        val password = requireNotNull(props.getProperty("admin.password")) {
            "admin.password missing in assets/firebase-admin.properties"
        }

        val auth = Firebase.auth
        if (auth.currentUser?.email != email) {
            auth.signInWithEmailAndPassword(email, password)
        }
        FirebaseHandles(firestore = Firebase.firestore, auth = auth)
    }
}
