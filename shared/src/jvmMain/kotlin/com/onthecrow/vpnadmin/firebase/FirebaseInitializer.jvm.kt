package com.onthecrow.vpnadmin.firebase

import android.app.Application
import com.google.firebase.FirebaseApp as JavaFirebaseApp
import com.google.firebase.FirebaseOptions as JavaFirebaseOptions
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Lookup for the secrets file:
 *   1. -Dfirebase.config=/abs/path/to/firebase-admin.properties
 *   2. ./firebase-admin.properties (current working directory)
 *   3. ./desktopApp/firebase-admin.properties (when running from the repo root)
 */
actual object FirebaseInitializer {
    actual suspend fun initAndSignIn(): Result<FirebaseHandles> = runCatching {
        val props = loadProps()

        // GitLive's firebase-java-sdk needs a FirebasePlatform installed before any Firebase
        // call (logging + token storage). In-memory storage is fine — admin re-signs in each
        // launch.
        if (!platformInstalled) {
            FirebasePlatform.initializeFirebasePlatform(InMemoryFirebasePlatform())
            platformInstalled = true
        }

        val options = JavaFirebaseOptions.Builder()
            .setProjectId(props.required("firebase.projectId"))
            .setApplicationId(props.required("firebase.applicationId"))
            .setApiKey(props.required("firebase.apiKey"))
            .apply {
                props.getProperty("firebase.storageBucket")?.takeIf { it.isNotBlank() }
                    ?.let(::setStorageBucket)
                props.getProperty("firebase.gcmSenderId")?.takeIf { it.isNotBlank() }
                    ?.let(::setGcmSenderId)
            }
            .build()

        if (JavaFirebaseApp.getApps(Application()).isEmpty()) {
            JavaFirebaseApp.initializeApp(Application(), options)
        }

        val email = props.required("admin.email")
        val password = props.required("admin.password")

        val auth = Firebase.auth
        if (auth.currentUser?.email != email) {
            auth.signInWithEmailAndPassword(email, password)
        }
        FirebaseHandles(firestore = Firebase.firestore, auth = auth)
    }

    @Volatile
    private var platformInstalled: Boolean = false

    private fun loadProps(): Properties {
        val candidates = buildList {
            System.getProperty("firebase.config")?.let { add(File(it)) }
            add(File("firebase-admin.properties"))
            add(File("desktopApp/firebase-admin.properties"))
        }
        val file = candidates.firstOrNull { it.exists() }
            ?: error(
                "firebase-admin.properties not found. Looked in: " +
                    candidates.joinToString { it.absolutePath } +
                    ". Copy desktopApp/firebase-admin.properties.template and fill in values."
            )
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    private fun Properties.required(key: String): String =
        getProperty(key)?.takeIf { it.isNotBlank() }
            ?: error("Missing required key '$key' in firebase-admin.properties")
}

private class InMemoryFirebasePlatform : FirebasePlatform() {
    private val store = ConcurrentHashMap<String, String>()
    private val dbRoot = File(System.getProperty("java.io.tmpdir"), "vpnadmin-firebase").also { it.mkdirs() }

    override fun store(key: String, value: String) { store[key] = value }
    override fun retrieve(key: String): String? = store[key]
    override fun clear(key: String) { store.remove(key) }
    override fun log(msg: String) { println("[firebase] $msg") }
    override fun getDatabasePath(name: String): File = File(dbRoot, name)
}
