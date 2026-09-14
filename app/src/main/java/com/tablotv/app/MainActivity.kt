package com.tablotv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = TabloPreferenceStore(this)
        setContent {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Color(0xFF101114))) {
                TabloTvApp(preferences)
            }
        }
    }
}

@Composable
fun TabloTvApp(preferences: TabloPreferenceStore) {
    var devices by remember { mutableStateOf<List<TabloDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<TabloDevice?>(null) }
    var stream by remember { mutableStateOf<TabloStream?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var appName by remember { mutableStateOf("Tablo TV") }

    val discoverDevices: () -> Unit = {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = TabloApiClient()
                val loaded = api.discoverDevices(
                    preferences.savedEmail().ifBlank { "user@example.com" },
                    preferences.savedPassword().ifBlank { "password" }
                )
                withContext(Dispatchers.Main) {
                    devices = loaded
                    selected = loaded.firstOrNull()
                    status = if (loaded.isNotEmpty()) "Found ${loaded.size} Tablo device(s)" else "No devices found"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    status = "Discovery error: ${t.message ?: "unknown"}"
                }
            }
        }
    }

    val loadStream: () -> Unit = load@{
        val device = selected
        if (device == null) {
            status = "Select a device before starting playback"
            return@load
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val channel = TabloChannel(
                    identifier = "example-channel",
                    callSign = "KFOR",
                    major = 4,
                    minor = 1,
                    network = "NBC",
                    kind = "ota"
                )
                val api = TabloApiClient()
                val watched = api.watch(device, channel)
                withContext(Dispatchers.Main) {
                    stream = watched
                    status = "Playing ${channel.displayName}"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    status = "Stream error: ${t.message ?: "unknown"}"
                }
            }
        }
    }

    Surface(
        color = Color(0xFF101114),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { preferences.clearAccount(); status = "Account forgotten" }) {
                    Text("Forget Tablo Account")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Status: $status", color = Color(0xFFB9C5D6), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = discoverDevices) { Text("Discover Devices") }
                Button(onClick = loadStream) { Text("Play Test Stream") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    val isSelected = device.sid == selected?.sid
                    Button(
                        onClick = { selected = device },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isSelected) "${device.name} (selected)" else device.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            stream?.let {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Playlist URL: ${it.playlistUrl}", color = Color.White)
            }
        }
    }
}
