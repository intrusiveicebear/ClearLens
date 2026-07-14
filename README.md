# ClearLens

ClearLens is a privacy-first Android gallery cleaner. It scans on-device for exact duplicates, visually similar photos, unusually blurry images, extremely dark images, and likely blank/accidental shots. It never deletes automatically.

## What version 1 includes

- SHA-256 exact duplicate detection
- Fast 64-bit perceptual hashing for similar-photo groups
- Blur, exposure, and low-detail analysis
- Automatic “keep best” recommendation based on sharpness, exposure, resolution, and favourite status
- Review filters, individual checkboxes, group selection, file sizes, dates, and thumbnails
- Persistent **Protect this folder** controls for folders that should never be scanned
- Conservative defaults: uncertain quality suggestions begin unselected
- Android’s protected system trash confirmation; files are recoverable from the device trash
- Fully local processing with no account, ads, analytics, internet permission, or photo uploads

## Open and run on Windows

1. Install the current stable Android Studio from the official Android developer website.
2. Unzip `ClearLens-Android.zip`.
3. In Android Studio choose **Open**, then select the `ClearLens` folder.
4. Allow the first Gradle sync to finish. Android Studio may offer to install Android SDK 35; accept it.
5. Connect an Android 11+ phone with USB debugging enabled, or create an emulator.
6. Press the green **Run** button.

The app asks for gallery access the first time it opens. A large gallery can take several minutes on its first scan because exact duplicate candidates are verified byte-for-byte.

## Build an installable APK

In Android Studio choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The debug APK will be placed under `app/build/outputs/apk/debug/`.

For sharing publicly, use **Build > Generate Signed Bundle / APK**, create and securely back up a signing key, and build an Android App Bundle (`.aab`) for Google Play.

The included `.github/workflows/build-apk.yml` can also build the installable debug APK automatically with GitHub Actions. Run the **Build ClearLens APK** workflow and download its `ClearLens-APK` artifact.

## Detection safety

“Meaningless” is subjective, so ClearLens does not pretend it can identify sentimental value. It only flags measurable problems such as near-zero detail, extreme darkness, or blur. Those suggestions start unselected and explain why they were shown. Favourited photos receive the highest keep score.

## Current scope

- Android 11 (API 30) or newer
- Photos only; video cleanup can be added later
- HEIC/JPEG/PNG/WebP and other formats supported by the phone’s thumbnail decoder
- No background scan: scanning runs only when the user requests it

## Main source folders

- `app/src/main/java/com/clearlens/app/data` — MediaStore access and image analysis
- `app/src/main/java/com/clearlens/app/model` — scan result models
- `app/src/main/java/com/clearlens/app/ui` — Jetpack Compose interface
- `app/src/test` — local tests
