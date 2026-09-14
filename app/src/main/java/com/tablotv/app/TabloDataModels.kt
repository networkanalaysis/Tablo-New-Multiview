package com.tablotv.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TabloDevice(
    val sid: String,
    val name: String,
    val localUrl: String,
    val lighthouseToken: String,
    val accountToken: String,
    val clientId: String = "00000000-0000-0000-0000-000000000000"
)

@Serializable
data class TabloChannel(
    val identifier: String,
    val callSign: String,
    val major: Int = 0,
    val minor: Int = 0,
    val network: String = "",
    val kind: String = "ota"
) {
    val displayName: String
        get() = if (major > 0) "$major.$minor $callSign" else callSign
}

@Serializable
data class TabloStream(
    val channelIdentifier: String,
    val playlistUrl: String,
    val token: String? = null,
    val expires: String? = null,
    val keepalive: Int? = null
)

@Serializable
data class CloudLoginResponse(
    val token_type: String,
    val access_token: String
) {
    val authHeader: String
        get() = "$token_type $access_token"
}

@Serializable
data class CloudDevice(
    val name: String,
    val serverId: String,
    val url: String
)

@Serializable
data class CloudProfile(
    val identifier: String,
    val name: String? = null
)

@Serializable
data class CloudAccountResponse(
    val profiles: List<CloudProfile> = emptyList(),
    val devices: List<CloudDevice> = emptyList()
)

@Serializable
data class CloudSelectResponse(
    val token: String
)

@Serializable
data class CloudChannelInfo(
    val major: Int? = null,
    val minor: Int? = null,
    @SerialName("callSign") val callSign: String? = null,
    val network: String? = null
)

@Serializable
data class CloudChannel(
    val identifier: String,
    val name: String? = null,
    val kind: String = "ota",
    val ota: CloudChannelInfo? = null,
    val ott: CloudChannelInfo? = null
)

@Serializable
data class WatchResponse(
    @SerialName("playlist_url") val playlistUrl: String? = null,
    val token: String? = null,
    val expires: String? = null,
    val keepalive: Int? = null
)
