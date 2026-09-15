package com.tablotv.app

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val TabloBlue = Color(0xFF5B8AF5)
private val TabloPurple = Color(0xFF7C5BF5)
private val TabloBackground = Color(0xFF101114)
private val TabloCard = Color(0xFF1E2026)
private val White = Color.White

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
    var signedIn by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf<TabloDevice?>(null) }
    var channels by remember { mutableStateOf<List<TabloChannel>>(emptyList()) }
    var stream by remember { mutableStateOf<TabloStream?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connect(email: String, password: String, saveCredentials: Boolean) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            status = "Connecting to Tablo..."
            try {
                val discovered = withContext(Dispatchers.IO) {
                    TabloApiClient().discoverDevices(email.trim(), password)
                }
                val connected = discovered.firstOrNull()
                    ?: throw IllegalStateException("No Tablo TV was found for this account")
                val loadedChannels = withContext(Dispatchers.IO) {
                    TabloApiClient().loadChannels(connected)
                }
                if (saveCredentials) preferences.saveAccount(email.trim(), password)
                device = connected
                channels = loadedChannels
                signedIn = true
                status = "${loadedChannels.size} channels available on ${connected.name}"
            } catch (t: Throwable) {
                status = "Ready"
                error = if (t is IOException && t.message?.contains("timeout", ignoreCase = true) == true) {
                    "Tablo took too long to respond. Check the TV's internet connection and try again."
                } else {
                    t.message ?: "Unable to connect to Tablo"
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val email = preferences.savedEmail()
        val password = preferences.savedPassword()
        if (email.isNotBlank() && password.isNotBlank()) connect(email, password, saveCredentials = false)
    }

    if (!signedIn) {
        SignInScreen(
            loading = loading,
            error = error,
            onSignIn = { email, password -> connect(email, password, saveCredentials = true) }
        )
    } else {
        ChannelScreen(
            email = preferences.savedEmail(),
            device = device,
            channels = channels,
            stream = stream,
            status = status,
            error = error,
            onRefresh = {
                val email = preferences.savedEmail()
                val password = preferences.savedPassword()
                connect(email, password, saveCredentials = false)
            },
            onPlay = { channel ->
                device?.let { connected ->
                    scope.launch {
                        loading = true
                        error = null
                        status = "Starting ${channel.displayName}..."
                        try {
                            stream = withContext(Dispatchers.IO) {
                                TabloApiClient().watch(connected, channel)
                            }
                            status = "Live stream ready for ${channel.displayName}"
                        } catch (t: Throwable) {
                            error = t.message ?: "Unable to start channel"
                            status = "Ready"
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            onForgetAccount = {
                preferences.clearAccount()
                signedIn = false
                device = null
                channels = emptyList()
                stream = null
                error = null
                status = "Ready"
            }
        )
    }

    stream?.let { liveStream ->
        LivePlayer(
            stream = liveStream,
            channel = channels.firstOrNull { it.identifier == liveStream.channelIdentifier },
            onClose = { stream = null }
        )
    }
}

@Composable
private fun SignInScreen(
    loading: Boolean,
    error: String?,
    onSignIn: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (email.isNotBlank() && password.isNotBlank() && !loading) {
            focusManager.clearFocus()
            onSignIn(email, password)
        }
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
            modifier = Modifier.width(520.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(TabloBlue, TabloPurple))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tv, contentDescription = "Tablo TV", tint = White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Sign in to Tablo", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Connect one Tablo TV to access your live OTA channels",
                color = White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(28.dp))

            if (error != null) {
                Surface(
                    color = Color(0xFF3B1818),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Text(error, color = White, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email address", color = White) },
                placeholder = { Text("you@example.com", color = White) },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = signInFieldColors(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().focusable().focusRing()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = White) },
                placeholder = { Text("Enter your Tablo password", color = White) },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = signInFieldColors(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().focusable().focusRing()
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = ::submit,
                enabled = email.isNotBlank() && password.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TabloPurple,
                    contentColor = White,
                    disabledContainerColor = TabloPurple.copy(alpha = 0.45f),
                    disabledContentColor = White
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp).focusable().focusRing()
            ) {
                Text(if (loading) "Connecting..." else "Sign In", color = White, fontSize = 18.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Your credentials are encrypted on this device and are never shown in diagnostics.",
                color = White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun signInFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    disabledTextColor = White,
    cursorColor = White,
    focusedBorderColor = TabloPurple,
    unfocusedBorderColor = White,
    disabledBorderColor = White.copy(alpha = 0.5f),
    focusedLabelColor = White,
    unfocusedLabelColor = White,
    disabledLabelColor = White,
    focusedPlaceholderColor = White,
    unfocusedPlaceholderColor = White,
    disabledPlaceholderColor = White.copy(alpha = 0.7f),
    focusedContainerColor = TabloCard,
    unfocusedContainerColor = TabloCard,
    disabledContainerColor = TabloCard
)

private fun Modifier.focusRing(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    border(
        width = 2.dp,
        color = if (focused) TabloPurple else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ).onFocusChanged { focused = it.isFocused }
}

@Composable
private fun ChannelScreen(
    email: String,
    device: TabloDevice?,
    channels: List<TabloChannel>,
    stream: TabloStream?,
    status: String,
    error: String?,
    onRefresh: () -> Unit,
    onPlay: (TabloChannel) -> Unit,
    onForgetAccount: () -> Unit
) {
    var profileOpen by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Live TV") }

    Surface(color = TabloBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(TabloBlue, TabloPurple))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = "Tablo", tint = White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("TABLO", color = White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(20.dp))
                    listOf("Live TV", "Guide", "Library").forEach { tab ->
                        Button(
                            onClick = { activeTab = tab },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == tab) TabloBlue.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = White
                            ),
                            modifier = Modifier.focusable().focusRing()
                        ) {
                            Text(tab, color = White, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                /* Keep the connected device visible in the header. */
                Column(horizontalAlignment = Alignment.End) {
                    Text(activeTab.uppercase(), color = White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text(
                        device?.name ?: "Connected Tablo",
                        color = White,
                        fontSize = 16.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TabloCard,
                            contentColor = White
                        ),
                        modifier = Modifier.focusable().focusRing()
                    ) { Text("Refresh Channels", color = White) }
                    Box {
                        Button(
                            onClick = { profileOpen = !profileOpen },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TabloCard,
                                contentColor = White
                            ),
                            modifier = Modifier.focusable().focusRing()
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Account", tint = White)
                            Spacer(Modifier.width(8.dp))
                            Text(email.take(2).uppercase().ifBlank { "TV" }, color = White)
                        }
                        if (profileOpen) {
                            Surface(
                                color = Color(0xFF252832),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .width(260.dp)
                                    .padding(top = 58.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("SIGNED IN AS", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(email, color = White, fontSize = 14.sp)
                                    Spacer(Modifier.height(12.dp))
                                    TextButton(
                                        onClick = {
                                            profileOpen = false
                                            onForgetAccount()
                                        },
                                        modifier = Modifier.fillMaxWidth().focusable().focusRing()
                                    ) {
                                        Text("Sign Out", color = White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (activeTab == "Live TV") "ON AIR NOW" else activeTab.uppercase(),
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                if (activeTab == "Live TV") "Browse your local guide and start watching instantly"
                else "This section is coming soon",
                color = White,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(status, color = White, fontSize = 16.sp)
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error, color = White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(18.dp))
            if (activeTab != "Live TV") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$activeTab is not available yet.", color = White, fontSize = 18.sp)
                }
            } else if (channels.isEmpty()) {
                Text("No channels are available on this Tablo.", color = White, fontSize = 18.sp)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.identifier }) { channel ->
                        ChannelRow(channel = channel, onPlay = { onPlay(channel) })
                    }
                }
            }
            if (stream != null) {
                Spacer(Modifier.height(8.dp))
                Text("Live stream acquired", color = White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: TabloChannel, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }

    Button(
        onClick = onPlay,
        colors = ButtonDefaults.buttonColors(
            containerColor = TabloCard,
            contentColor = White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .border(
                2.dp,
                if (focused) TabloPurple else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .focusRing()
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.width(78.dp).height(50.dp)
                    .background(Color(0xFF2E313A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (channel.major > 0) "${channel.major}.${channel.minor}" else "-",
                    color = White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(channel.callSign, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (channel.network.isNotBlank()) {
                    Text("Watching ${channel.network}", color = White, fontSize = 14.sp)
                }
            }

            Text("WATCH", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LivePlayer(
    stream: TabloStream,
    channel: TabloChannel?,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(stream.playlistUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(stream.playlistUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        this.player = player
                        useController = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    channel?.displayName ?: "Live TV",
                    color = White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        contentColor = White
                    ),
                    modifier = Modifier.focusable().focusRing()
                ) {
                    Text("Close", color = White)
                }
            }
        }
    }
}
