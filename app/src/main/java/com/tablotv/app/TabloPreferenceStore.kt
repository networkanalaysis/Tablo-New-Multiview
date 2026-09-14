package com.tablotv.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TabloPreferenceStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "tablo_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccount(email: String, password: String) {
        prefs.edit().putString("email", email).putString("password", password).apply()
    }

    fun clearAccount() {
        prefs.edit().remove("email").remove("password").remove("selected_device").apply()
    }

    fun savedEmail(): String = prefs.getString("email", "") ?: ""
    fun savedPassword(): String = prefs.getString("password", "") ?: ""
    fun saveSelectedDevice(deviceJson: String) {
        prefs.edit().putString("selected_device", deviceJson).apply()
    }
    fun selectedDevice(): String = prefs.getString("selected_device", "") ?: ""
}
