# Photo Video URL — Native Android Application

A production-oriented Android application that enables users to select photos, videos, documents, files, and APKs from their Android device, upload them directly to their own Supabase Storage bucket, and register instant, unique short URLs in Firebase Realtime Database for public sharing at **`https://ivu.gt.tc/{shortId}`**.

---

## 📱 Project Identity

- **Application Name:** Photo Video URL
- **Package Name / Application ID:** `com.praveen.studio.photovideo.url`
- **Target Platform:** Android (Native Kotlin & Jetpack Compose)
- **Minimum SDK:** Android 9 (API 28)
- **Target SDK:** Android 15 (API 36)
- **Architecture:** Clean Architecture + MVVM + Repository Pattern
- **Support Contact:** `praveensahani9273@gmail.com`

---

## 🌟 Key Features

0. **In-app gallery & file browser:** the app asks for the storage permission once (Android 9-12: *Storage*; Android 13+: *Photos and videos*; Android 11+ documents/files/APKs: *All files access*) and lists photos, videos, documents, archives and APKs itself. "Other folders" opens the system picker as a fallback.

1. **Multi-Category Media Uploads (Locked until Supabase is connected):**
   - **Photos (Images):** PNG, JPG, WEBP, GIF, SVG (photos only, picked in the in-app gallery)
   - **Videos:** MP4, MKV, MOV, WEBM (videos only, picked in the in-app gallery)
   - **Documents:** PDF, DOC, DOCX, TXT, XLS, PPT (Strictly documents)
   - **Files:** ZIP, 7Z, TAR, RAR, and folder archives only
   - **APKs:** 2 Modes:
     - *Upload Device APK File* (pick `.apk` files in the in-app file browser)
     - *Extract Installed App APK* (Lists installed apps on device, extracts `.apk` into user's public `Downloads` folder, and uploads it directly)

2. **Supabase Storage Integration:**
   - Direct streaming REST upload via OkHttp with live byte-level progress reporting.
   - Organized folder hierarchy: `media/images/`, `media/videos/`, `media/documents/`, `media/files/`, `media/apks/`.
   - Credentials securely encrypted on-device via **Android Keystore** (`EncryptedSharedPreferences`).
   - One-touch connection test to verify project URL and anon public key.

3. **Unique Short URL Generation:**
   - Generates collision-checked random URL-safe 7-character IDs prefixed by media type (e.g., `image_AaBb123`, `video_XyZ9821`, `document_PqR4567`, `file_MnK8214`, `apk_BvC9281`).
   - Final shareable link: `https://ivu.gt.tc/{shortId}`.

4. **Real-Time Firebase Realtime Database Sync:**
   - Maintains user upload history under `user/{installationId}/details` and `user/{installationId}/{category}/{shortId}`.
   - Publishes public link resolver node under `public_links/{shortId}`.
   - Configuration backend under `app/backend` and `app/about`.
   - Offline-first cache backed by DataStore ensures history persists across restarts.

5. **Real-Time Live Upload HUD:**
   - Live percentage bar, upload speed (KB/s and MB/s), uploaded MB / total MB, and elapsed time counter.
   - Instant 1-tap Copy, Share, and Open browser actions.

6. **Free Trial Gallery Demo (3 Photos Only):**
   - Direct gallery photo selection before connecting Supabase.
   - Strictly limited to 3 trial uploads per Installation ID.
   - Once all 3 uploads are completed, the trial banner disappears completely.

7. **Custom Adaptive Launcher Icon:**
   - Material You compliant adaptive launcher icon featuring a stylized film frame intertwined with an electric link chain emblem.

---

## 🔀 Which Supabase is used when?

| Situation | Supabase used |
|---|---|
| User has **not** set up Supabase | Only the **3 free demo photos** (picked from the gallery) use the developer Supabase stored manually in Firebase `app/backend`. Files go to the `trial/images/` folder. |
| User finished **Settings → Supabase setup** | **Everything** (photos, videos, documents, files, APKs) goes to the **user's own** Supabase. `app/backend` is never used. |

- Before setup, all five category cards stay visible but dull (no lock icon). Tapping one opens **"Set up your Supabase"**, which leads to Settings.
- After the 3 free photos are used, the demo banner disappears.

## 🗜️ Large files (ZIP & Upload)

Supabase Free allows 50 MB per file (Pro and above can raise it). When a picked file is bigger than 50 MB, or Supabase answers with HTTP 413, the app asks:
**ZIP & Upload** (creates a ZIP in the app cache and uploads it as a File), **Upload as is**, or **Cancel**.
ZIP shrinks documents a lot, but videos, photos and APKs are already compressed and shrink very little.
Uploads are streamed from disk, so big files no longer load fully into memory.

## 📦 APK extractor list

Only apps the user installed (and updated system apps such as Chrome or YouTube) are listed. Built-in Android system apps are hidden.

## 🏗️ Technical Architecture

```
com.praveen.studio.photovideo.url/
├── PhotoVideoApplication.kt       # Application class initializing Firebase & App Check
├── MainActivity.kt                # Single activity with edge-to-edge support
├── data/
│   ├── local/
│   │   ├── InstallationIdProvider.kt  # Non-fingerprinted random UUID installation identifier
│   │   ├── PreferencesManager.kt      # DataStore preferences & local upload cache
│   │   └── SecureStorageManager.kt    # Android Keystore EncryptedSharedPreferences
│   ├── model/
│   │   ├── MediaCategory.kt           # Category enum (IMAGES, VIDEOS, DOCUMENTS, FILES, APKS)
│   │   ├── SelectedMediaItem.kt       # Staged file item representation
│   │   ├── SupabaseConfig.kt          # Validated Supabase credentials
│   │   ├── ThemePreference.kt         # System, Light, Dark preferences
│   │   ├── UploadProgress.kt          # Sealed interface for upload states
│   │   └── UploadRecord.kt            # Firebase RTDB record structure
│   ├── remote/
│   │   ├── CountingRequestBody.kt     # Streaming OkHttp progress sink
│   │   ├── FirebaseDatabaseManager.kt # Firebase RTDB CRUD and collision-checking
│   │   └── SupabaseStorageClient.kt   # Supabase Storage REST API client
│   └── worker/
│       └── MediaUploadWorker.kt       # WorkManager resilient background worker
├── domain/
│   └── repository/
│       ├── HistoryRepository.kt       # Remote RTDB + local cache reactive stream
│       ├── SettingsRepository.kt      # Configuration & theme manager
│       └── UploadRepository.kt        # Multi-stage upload coordinator
└── ui/
    ├── PhotoVideoApp.kt               # Root composable with M3 NavigationBar & BackHandler
    ├── components/
    │   ├── AppHeader.kt               # App title, logo, connection pill, history shortcut
    │   ├── DemoModeBanner.kt          # Demo mode banner & 3-sample modal picker
    │   ├── UploadCategoryCard.kt      # 5 category upload cards
    │   └── UploadProgressDialog.kt    # Live progress dialog with transfer speed & metrics
    ├── navigation/
    │   └── Screen.kt                  # HOME, HISTORY, SETTINGS screens
    ├── screens/
    │   ├── history/
    │   │   ├── HistoryItemCard.kt     # Item card with thumbnail, URLs, copy/share/open
    │   │   ├── HistoryScreen.kt       # Search bar, category filter chips, dynamic list
    │   │   └── HistoryViewModel.kt
    │   ├── home/
    │   │   ├── HomeScreen.kt          # Five upload cards & photo/document pickers
    │   │   └── HomeViewModel.kt
    │   └── settings/
    │       ├── SettingsScreen.kt      # Supabase URL, Anon key, test connection, setup guide
    │       └── SettingsViewModel.kt
    └── theme/
        ├── Color.kt                   # Modern cyan, slate, indigo, and accent colors
        ├── Theme.kt                   # Material 3 theme wrapper
        └── Type.kt                    # Clean typography scales
```

---

## ⚙️ Supabase Setup Instructions

1. **Create a Supabase Project:**
   - Sign up or log in at [supabase.com](https://supabase.com).
   - Create a new project and select your preferred region.

2. **Retrieve API Credentials:**
   - In the Supabase project dashboard, navigate to **Project Settings** > **API**.
   - Copy the **Project URL** (e.g., `https://abcdefghijkl.supabase.co`).
   - Copy the **`anon` public key** (starts with `eyJhbGciOi...`).
   - *Never copy or use the `service_role` secret key.*

3. **Create the Storage Bucket:**
   - Go to **Storage** in the sidebar.
   - Click **New bucket**.
   - Set the bucket name to `media` (or your preferred name, matching the app settings).
   - Toggle **Public bucket** to `ON` so uploaded files can be viewed via their public URLs.

4. **Configure Storage RLS Policies:**
   Under **Storage** > **Policies** for bucket `media`:
   - **SELECT Policy (Public Viewing):**
     - Target: `anon`, `authenticated`
     - Policy: `bucket_id = 'media'`
   - **INSERT Policy (Client Uploads):**
     - Target: `anon`, `authenticated`
     - Policy: `bucket_id = 'media'`

---

## 🔥 Firebase Realtime Database Clean Structure & Security Rules

### Database Hierarchy
```
user/
  └── {installationId}/
        ├── details/
        │     ├── name: "Praveen"
        │     ├── totalPhotosUpload: 0
        │     ├── totalVideosUpload: 0
        │     ├── totalDocumentsUpload: 0
        │     ├── totalFilesUpload: 0
        │     ├── totalApksUpload: 0
        │     ├── supabaseApi: ""
        │     ├── supabaseKey: ""
        │     ├── premium: false
        │     ├── freeTierUploadsUsed: 0
        │     └── freeTierMax: 3
        ├── images/
        │     └── {shortId}/
        │           ├── fileName: "sample.jpg"
        │           ├── fileType: "image"
        │           ├── fileSize: 1048576
        │           ├── supabaseUrl: "https://.../sample.jpg"
        │           ├── shortUrl: "https://ivu.gt.tc/image_AaBb123"
        │           ├── uploadDate: "06/11/2026"
        │           ├── uploadTime: "10:45 AM"
        │           ├── timestamp: 1730880000000
        │           └── shortId: "image_AaBb123"
        ├── videos/
        ├── documents/
        ├── files/
        └── apks/

app/
  ├── backend/           <-- Developer manually enters Supabase API & Key here!
  │     ├── supabaseApi: "https://xyz.supabase.co"
  │     ├── supabaseKey: "eyJhbGciOi..."
  │     └── bucket: "media"
  └── about/
        ├── appName: "Photo Video URL"
        ├── contact: "praveensahani9273@gmail.com"
        ├── supportEmail: "praveensahani9273@gmail.com"
        ├── domain: "https://ivu.gt.tc"
        └── version: "2.1.0"

public_links/
  └── {shortId}/
        ├── type: "image"
        ├── mimeType: "image/jpeg"
        ├── supabaseUrl: "https://..."
        ├── createdAt: 1730880000000
        └── active: true
```

### Security Rules (Firebase Console)
Publish the rules of **`firebase-rules.json`** (same folder as this README) under
**Firebase Console > Realtime Database > Rules > Publish**:

```json
{
  "rules": {
    "public_links": { "$id": { ".read": true, ".write": "!data.exists() || !newData.exists()" } },
    "photo_video_url": { "public_links": { "$id": { ".read": true } } },
    "user": { "$uid": { ".read": true, ".write": true } },
    "device_profiles": { "$key": { ".read": true, ".write": "!data.exists()" } },
    "ip_profiles": { "$key": { ".read": true, ".write": true } },
    "app": { ".read": true, ".write": false }
  }
}
```

* `public_links`: a short link can be created once and never overwritten; it can only be removed (the History delete uses this).
* `user/<installationId>` holds the upload history of one phone (the app has no login).
* `device_profiles/<hash>` is **create-only**: the first ID of a phone is saved once and never changes (see *One profile per phone*).
* `ip_profiles/<hash>/freeUsed` counts free demo photos per network.
* `app` is read-only; fill `app/backend` (free-trial Supabase) from the console.
* **Do not** use rules that only open `photo_video_url/public_links`: the app then cannot save links or read history.

**Upload works in Supabase but no link / nothing in Firebase?** Open the app > Settings > *Check Firebase Connection*.
A line that says *BLOCKED by Firebase rules* means the rules above are not published yet. Also keep
**App Check enforcement OFF** for Realtime Database (the app does not use App Check).

---

## 📱 One profile per phone (version 2.1.0)
* The first installation ID of a phone is saved in Firebase (`device_profiles`, key = salted hash of the Android ID).
  After an **uninstall, re-install or "clear data"** the same ID is found again: the **History comes back**, the
  **3 free demo photos are not given again**, and no new profile is created in the database.
* The user's **Supabase settings are backed up in Firebase, encrypted** with a key that only the phone has, and are
  restored automatically after a re-install (no new set-up). **Disconnect** in Settings removes the backup.
* The public **IP is not used to choose a profile** (people on the same Wi-Fi or mobile network would see each
  other's History). It only limits the free demo photos per network (`IP_FREE_MAX` in `IdentityManager.kt`, now 30).
  Only salted hashes are stored, never the IP or the Android ID.
* The Android ID stays the same for the same APK signing key until the phone is factory reset. A build signed with
  another key counts as a new phone.
* **Demo photos** (free trial) are stored in the developer's Supabase. In History their real URL and storage path are
  hidden; only the short link is shown.

## 🗑️ Deleting uploads (History)
History > delete icon > tick single files or tap **Select all** > red delete icon > **Continue**.
The file is deleted from Supabase Storage and the history entry + short link are removed from Firebase.
For the Supabase part run this once in **Supabase > SQL Editor** (change `media` to your bucket name):

```sql
create policy "ivu anon read" on storage.objects for select to anon using (bucket_id = 'media');
create policy "ivu anon delete" on storage.objects for delete to anon using (bucket_id = 'media');
```
Without the delete policy the app shows "Supabase did not allow deleting the file" and keeps the entry.
Free-trial photos live in the developer's storage and are only removed from the list.

## 🧯 If the app ever crashes
A crash opens the **"Photo Video URL stopped unexpectedly"** screen with the exact error text.
Tap **Copy report** and send it to the developer. (This screen runs in its own process, so it also works
when the normal app cannot start.)

## 📦 APK files (GitHub Releases)
* `PhotoVideoURL-v2.1.0.apk` – normal build, the safest one.
* `PhotoVideoURL-v2.1.0-small.apk` – optimised build (unused code removed, much smaller). Use it if it runs fine on your phone.

---

## 🛠️ Building & Running

### Local Android Studio Build
```bash
gradle assembleDebug
```
The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Running Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 License & Attribution

Developed for **Photo Video URL** by Praveen Sahani.  
Support: `praveensahani9273@gmail.com`
