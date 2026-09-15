package com.tablotv.app

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TabloApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        private const val CLOUD_HOST = "https://lighthousetv.ewscloud.com"
        private const val CLOUD_USER_AGENT = "Tablo-FAST/2.0.0 (Mobile; iPhone; iOS 16.6)"
        private const val LOCAL_USER_AGENT = "Tablo-FAST/1.7.0 (Mobile; iPhone; iOS 18.4)"
    }

    fun login(email: String, password: String): CloudLoginResponse {
        val body = "{\"email\":\"$email\",\"password\":\"$password\"}"
        val request = Request.Builder()
            .url("$CLOUD_HOST/api/v2/login/")
            .header("User-Agent", CLOUD_USER_AGENT)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Login failed: ${response.code}")
        return json.decodeFromString(CloudLoginResponse.serializer(), response.body?.string() ?: "")
    }

    fun account(authHeader: String): CloudAccountResponse {
        val request = Request.Builder()
            .url("$CLOUD_HOST/api/v2/account/")
            .header("Authorization", authHeader)
            .header("User-Agent", CLOUD_USER_AGENT)
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Account lookup failed: ${response.code}")
        return json.decodeFromString(CloudAccountResponse.serializer(), response.body?.string() ?: "")
    }

    fun selectAccount(authHeader: String, pid: String, sid: String): String {
        val body = "{\"pid\":\"$pid\",\"sid\":\"$sid\"}"
        val request = Request.Builder()
            .url("$CLOUD_HOST/api/v2/account/select/")
            .header("Authorization", authHeader)
            .header("User-Agent", CLOUD_USER_AGENT)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Device select failed: ${response.code}")
        return json.decodeFromString(CloudSelectResponse.serializer(), response.body?.string() ?: "").token
    }

    fun discoverDevices(email: String, password: String): List<TabloDevice> {
        val login = login(email, password)
        val account = account(login.authHeader)
        val primaryProfile = account.profiles.firstOrNull() ?: throw IllegalStateException("No profiles found")
        val clientId = "00000000-0000-0000-0000-000000000000"
        val device = account.devices.firstOrNull()
            ?: throw IllegalStateException("No Tablo devices found")
        val token = selectAccount(login.authHeader, primaryProfile.identifier, device.serverId)
        return listOf(
            TabloDevice(
                sid = device.serverId,
                name = device.name,
                localUrl = device.url,
                lighthouseToken = token,
                accountToken = login.access_token,
                clientId = clientId
            )
        )
    }

    fun loadChannels(device: TabloDevice): List<TabloChannel> {
        val request = Request.Builder()
            .url("$CLOUD_HOST/api/v2/account/${device.lighthouseToken}/guide/channels/")
            .header("Authorization", "Bearer ${device.accountToken}")
            .header("Lighthouse", device.lighthouseToken)
            .header("User-Agent", CLOUD_USER_AGENT)
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Channel lookup failed: ${response.code}")
        val channels = json.decodeFromString(ListSerializer(CloudChannel.serializer()), response.body?.string() ?: "[]")
        return channels.mapNotNull { cloud ->
            val info = cloud.ota ?: cloud.ott
            val callSign = info?.callSign ?: cloud.name ?: cloud.identifier
            val major = info?.major ?: 0
            val minor = info?.minor ?: 0
            TabloChannel(
                identifier = cloud.identifier,
                callSign = callSign,
                major = major,
                minor = minor,
                network = info?.network ?: "",
                kind = cloud.kind
            )
        }.sortedWith(compareBy<TabloChannel> { it.major == 0 }.thenBy { it.major }.thenBy { it.minor }.thenBy { it.callSign })
    }

    fun watch(device: TabloDevice, channel: TabloChannel): TabloStream {
        val path = "/guide/channels/${channel.identifier}/watch"
        val body = """
            {
              "bandwidth": null,
              "extra": {
                "limitedAdTracking": 1,
                "deviceOSVersion": "16.6",
                "lang": "en_US",
                "height": 1080,
                "deviceId": "00000000-0000-0000-0000-000000000000",
                "width": 1920,
                "deviceModel": "iPhone10,1",
                "deviceMake": "Apple",
                "deviceOS": "iOS"
              },
              "device_id": "${device.clientId}",
              "platform": "ios"
            }
        """.trimIndent()
        val (authHeader, date) = TabloSigner.makeDeviceAuth("POST", path, body)

        val request = Request.Builder()
            .url((device.localUrl.trimEnd('/') + path))
            .header("Authorization", authHeader)
            .header("Date", date)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "*/*")
            .header("User-Agent", LOCAL_USER_AGENT)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IllegalStateException("Watch request failed: ${response.code}")
        val raw = response.body?.string() ?: "{}"
        val result = json.decodeFromString(WatchResponse.serializer(), raw)
        val playlist = result.playlistUrl ?: throw IllegalStateException("No playlist returned: $raw")
        return TabloStream(
            channelIdentifier = channel.identifier,
            playlistUrl = playlist,
            token = result.token,
            expires = result.expires,
            keepalive = result.keepalive
        )
    }
}
