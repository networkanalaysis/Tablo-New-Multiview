# Tablo TV

A native Android TV app for authenticating to Tablo devices, discovering local hardware, listing channels, and starting HLS playback through Media3/ExoPlayer.

## Supported platforms
- Android TV
- Google TV
- Amazon Fire TV
- Android 10+ / API 29+

## Architecture
- UI: Jetpack Compose + Android TV styling
- ViewModel: state-driven channel and playback screens
- Repository/API: Tablo cloud + local device access through OkHttp
- Playback: AndroidX Media3 / ExoPlayer
- Security: Android Keystore backed encryption for saved credentials

## Authentication flow
1. User enters Tablo cloud credentials.
2. App logs into `lighthousetv.ewscloud.com`.
3. The account and linked devices are discovered.
4. The selected device receives a Lighthouse token.
5. Local HTTP requests use HMAC-MD5 signing.

## Local networking
- Cloud traffic uses HTTPS.
- Local Tablo traffic uses HTTP when the device is on the LAN.
- The app avoids polling and keeps timeouts modest.

## HLS playback
The client requests a live stream from the selected local Tablo device and feeds the returned playlist URL into ExoPlayer.

## Multiview strategy
The project is structured for a multiview manager and multiple ExoPlayer instances, with dedicated lifecycle cleanup to avoid leaks.

## Build instructions
```bash
./gradlew assembleDebug
```

## ADB installation
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tablo setup
- Ensure the Tablo account is linked to the device.
- Confirm the Tablo is on the same network or reachable through the cloud account.
- Provide valid Tablo cloud credentials in the app.

## Known limitations
- This is an MVP skeleton focused on project structure and buildability.
- Full device pairing, guide integration, and multiview playback require additional hardware validation against a real Tablo and a more complete UI flow.

## Fire TV notes
The same APK targets Android TV APIs and is designed to be compatible with Fire TV devices that support the required Android/Media3 stack.

## Disclaimer
This project is an unofficial client and is not affiliated with Nuvyyo or Tablo.
