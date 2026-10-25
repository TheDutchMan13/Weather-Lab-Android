# Weather Lab for Android

A small Android app that reads your Weather Lab station through the read-only API in Weather Lab 0.31.0 or later.
It shows the current readings, today's high, low and rain, the station status, and recent alerts. It refreshes every minute.

## Before you start

1. On the PC, open Weather Lab → Settings → Phones and devices, and turn on **Let phones and devices on my network open Weather Lab**.
2. Under **Android app**, turn on **Let the Android app read Weather Lab**, then click **Create a token**.
3. Copy the token and the address shown (for example `192.168.1.50:8600`). The token is shown once.
4. Keep the PC running Weather Lab. The API only runs while Weather Lab is open.

## Building the app

1. Install Android Studio from developer.android.com/studio.
2. In Android Studio, choose **File → Open** and select this folder: `C:\Weather Station\WeatherLabAndroid`.
3. Let Gradle sync. The first sync downloads the Android SDK parts it needs, which can take a few minutes.
4. To run it on your phone: turn on Developer options and USB debugging on the phone, connect it, and press Run.
5. To make an APK file for installing without a computer: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The APK is written under `app\build\outputs\apk\debug\`.

The Gradle wrapper script isn't included yet. Android Studio uses its own Gradle, so this is only needed for command-line builds: run `gradle wrapper` once in this folder if you have Gradle installed.

## First run

Open the app, enter the address and token from the PC, and tap **Save and connect**. The app stores both on the phone, encrypted.
If the PC is off, or Weather Lab isn't open, the app shows the error from the PC.

## Notes

- Connections are plain HTTP over your home network, so the app allows that for the address you enter. Use it on your home Wi-Fi, or through Tailscale when you're away.
- The token is the only sign-in. Remove it in Weather Lab's Settings to cut off the app at any time.

## Automatic builds

Every push to `main` builds the debug APK on GitHub (see `.github/workflows/build.yml`).
Open the repository's **Actions** tab, pick the latest run, and download **WeatherLabAndroid-debug** from the bottom of the page.
Copy the APK to your phone and open it to install. Allow installs from your browser or file manager when Android asks.

## Making a release

A release is a signed APK that stays installable as an update. The signing key is made once, kept off the repository,
and handed to GitHub as secrets. If the key is lost, new versions can no longer be installed over old ones, so back it
up somewhere safe (not in this folder).

1. Make the key. In PowerShell (Android Studio's own copy of Java makes it):
   `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore weatherlab-release.jks -alias weatherlab -keyalg RSA -keysize 2048 -validity 10000`
   Choose a password. Copy `weatherlab-release.jks` to a safe place, then take it out of this folder.
2. Turn the key into text: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("weatherlab-release.jks")) | Set-Clipboard`
3. In the repository on GitHub, open Settings > Secrets and variables > Actions, and add four secrets:
   `KEYSTORE_BASE64` (the text from step 2), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`weatherlab`) and `KEY_PASSWORD`
   (the same as the keystore password, unless you chose another).
4. Raise `versionCode` and `versionName` in `app/build.gradle.kts`, push, then tag it:
   `git tag v0.3.0` and `git push origin v0.3.0`. GitHub builds the signed APK and posts it under Releases.

## License

GNU GPL v3.0 or later. Copyright (C) 2026 Hans De Kryger. See `LICENSE`.
