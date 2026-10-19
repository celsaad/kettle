package com.casco.kettle.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface

/** Empty until M2 (#148) hosts the RN screens here. It exists so the Compose toolchain is built in CI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Surface {} }
    }
}
