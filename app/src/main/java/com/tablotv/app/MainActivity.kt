package com.tablotv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TabloBlue = Color(0xFF5B8AF5)
private val TabloPurple = Color(0xFF7C5BF5)
private val TabloBackground = Color(0xFF101114)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = TabloPreferenceStore(this)
        setContent {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = TabloBackground)) {
                TabloTvApp(preferences)
            }
        }
    }
}

@Composable
fun TabloTvApp(preferences: TabloPreferenceStore) {
    var signedIn by remember {
        mutableStateOf(preferences.savedEmail().isNotBlank() && preferences.savedPassword().isNotBlank())
    }
    var devices by remember { mutableStateOf<List<TabloDevice>>(emptyList()) }
    var selected by remember { mutableStateOf<TabloDevice?>(null) }
    var stream by remember { mutableStateOf<TabloStream?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun discover(
        email: String,
        password: String,
        saveCredentials: Boolean,
        onFinished: () -> Unit = {}
    ) {
        scope.launch {
            error = null
            status = "Connecting to Tablo..."
            try {
                val loaded = withContext(Dispatchers.IO) {
                    TabloApiClient().discoverDevices(email.trim(), password)
                }
                if (saveCredentials) {
                    preferences.saveAccount(email.trim(), password)
                }
                devices = loaded
                selected = loaded.firstOrNull()
                signedIn = true
                status = if (loaded.isEmpty()) "No Tablo devices found" else {
                    "Found ${loaded.size} Tablo device(s)"
                }
            } catch (t: Throwable) {
                status = "Ready"
                error = t.message ?: "Sign in failed"
            } finally {
                onFinished()
            }
        }
    }

    if (!signedIn) {
        SignInScreen(
            error = error,
            onSignIn = { email, password, onFinished ->
                discover(email, password, saveCredentials = true, onFinished = onFinished)
            }
        )
    } else {
        TabloHomeScreen(
            devices = devices,
            selected = selected,
            stream = stream,
            status = status,
            error = error,
            onDiscover = {
                discover(preferences.savedEmail(), preferences.savedPassword(), saveCredentials = false)
            },
            onSelectDevice = { selected = it },
            onForgetAccount = {
                preferences.clearAccount()
                devices = emptyList()
                selected = null
                stream = null
                error = null
                status = "Ready"
                signedIn = false
            },
            onPlayStream = {
                val device = selected
                if (device == null) {
                    error = "Select a Tablo device before starting playback"
                    return@TabloHomeScreen
                }
                scope.launch {
                    error = null
                    status = "Starting stream..."
                    try {
                        val channel = withContext(Dispatchers.IO) {
                            TabloApiClient().loadChannels(device).firstOrNull()
                        } ?: throw IllegalStateException("No channels found")
                        stream = withContext(Dispatchers.IO) {
                            TabloApiClient().watch(device, channel)
                        }
                        status = "Stream ready for ${channel.displayName}"
                    } catch (t: Throwable) {
                        status = "Ready"
                        error = t.message ?: "Stream failed"
                    }
                }
            }
        )
    }
}

@Composable
private fun SignInScreen(
    error: String?,
    onSignIn: (String, String, () -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun submit() {
        if (email.isBlank() || password.isBlank() || loading) return
        loading = true
        onSignIn(email, password) { loading = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0x1A5B8AF5), TabloBackground),
                    radius = 900f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(TabloBlue, TabloPurple))),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Tablo TV", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Sign in with your Tablo account",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("Enter your Tablo password") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(error, color = Color(0xFFFF7777), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = ::submit,
                enabled = email.isNotBlank() && password.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TabloBlue,
                    disabledContainerColor = TabloBlue.copy(alpha = 0.35f)
                )
            ) {
                Text(if (loading) "Connecting..." else "Sign In", fontSize = 17.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Uses your Tablo account credentials. Credentials are stored securely on this device.",
                color = Color.White.copy(alpha = 0.28f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TabloHomeScreen(
    devices: List<TabloDevice>,
    selected: TabloDevice?,
    stream: TabloStream?,
    status: String,
    error: String?,
    onDiscover: () -> Unit,
    onSelectDevice: (TabloDevice) -> Unit,
    onForgetAccount: () -> Unit,
    onPlayStream: () -> Unit
) {
    Surface(color = TabloBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tablo TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onForgetAccount) { Text("Forget Account") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Status: $status", color = Color(0xFFB9C5D6), fontSize = 16.sp)
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = Color(0xFFFF7777), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDiscover) { Text("Discover Devices") }
                Button(onClick = onPlayStream) { Text("Play First Channel") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    val isSelected = device.sid == selected?.sid
                    Button(
                        onClick = { onSelectDevice(device) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isSelected) "${device.name} (selected)" else device.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            if (stream != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Live stream acquired", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
