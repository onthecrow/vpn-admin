package com.onthecrow.vpnadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.onthecrow.vpnadmin.firebase.bootstrapAndroidFirebase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bootstrapAndroidFirebase(applicationContext)
        setContent { App() }
    }
}
