package com.casco.kettle.shell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import expo.modules.brownfield.BrownfieldMessaging

/**
 * The native screen of the spike. Each control answers one M0 question; the log at the bottom is
 * what JS sent back, so the answers are read off the screen rather than inferred.
 */
class MainActivity : ComponentActivity() {
  private val log = object {
    val lines = mutableStateListOf<String>()
    operator fun plusAssign(line: String) {
      android.util.Log.i("KettleSpike", line)
      lines += line
    }
  }
  private var listenerId: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    log += "native filesDir: ${filesDir.absolutePath}"
    log += "native first frame: ${SystemClock.elapsedRealtime() - ShellApplication.processStartedAt} ms after process start"

    listenerId = BrownfieldMessaging.addListener { message -> runOnUiThread { log += "from JS: $message" } }

    setContent {
      MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Kettle brownfield spike", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { openRn(null) }) { Text("Open RN app") }
            Button(onClick = { openRn("kettle://history") }) { Text("Open RN at kettle://history") }
            Button(onClick = { openRn("kettle://settings") }) { Text("Open RN at kettle://settings") }
            Button(onClick = {
              BrownfieldMessaging.sendMessage(mapOf("type" to "ping", "sentAt" to System.currentTimeMillis()))
              log += "sent ping"
            }) { Text("Ping JS") }
            log.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    listenerId?.let(BrownfieldMessaging::removeListener)
    super.onDestroy()
  }

  private fun openRn(url: String?) {
    val intent = Intent(this, RnActivity::class.java)
    // RN's IntentModule only reports intent.data as the initial URL under ACTION_VIEW.
    if (url != null) {
      intent.action = Intent.ACTION_VIEW
      intent.data = Uri.parse(url)
    }
    startActivity(intent)
  }
}
