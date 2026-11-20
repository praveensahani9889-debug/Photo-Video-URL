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
    "public_links": {
      "$id": {
        ".read": true,
        ".write": "!data.exists() || !newData.exists() || (newData.child('type').val() === data.child('type').val() && newData.child('supabaseUrl').val() === data.child('supabaseUrl').val() && newData.child('mimeType').val() === data.child('mimeType').val())",
        "views": {
          ".write": "data.parent().exists() && newData.isNumber() && newData.val() == (data.exists() ? data.val() : 0) + 1",
          ".validate": "newData.isNumber() && newData.val() >= 0"
        },
        "expiresAt": {
          ".validate": "newData.isNumber() && newData.val() > 0"
        },
        "maxViews": {
          ".validate": "newData.isNumber() && newData.val() >= 1 && newData.val() <= 1000000"
        },
        "hasPassword": {
          ".validate": "newData.isBoolean()"
        },
        "passwordHash": {
          ".validate": "newData.isString() && newData.val().length == 64"
        },
        "passwordSalt": {
          ".validate": "newData.isString() && newData.val().length == 32"
        },
        "passwordIter": {
          ".validate": "newData.isNumber() && newData.val() >= 1000"
        },
        "bgColor": {
          ".validate": "newData.isString() && newData.val().matches(/^#[0-9A-Fa-f]{6}$/)"
        },
        "button": {
          "text": {
            ".validate": "newData.isString() && newData.val().length >= 1 && newData.val().length <= 30"
          },
          "color": {
            ".validate": "newData.isString() && newData.val().matches(/^#[0-9A-Fa-f]{6}$/)"
          },
          "url": {
            ".validate": "newData.isString() && newData.val().matches(/^https?:[/][/].{1,490}$/)"
          }
        },
        "pageTitle": {
          ".validate": "newData.isString() && newData.val().length <= 60"
        },
        "pageMessage": {
          ".validate": "newData.isString() && newData.val().length <= 200"
        },
        "allowDownload": {
          ".validate": "newData.isBoolean()"
        }
      }
    },
    "photo_video_url": {
      "public_links": {
        "$id": {
          ".read": true
        }
      }
    },
    "user": {
      "$uid": {
        ".read": true,
        ".write": true
      }
    },
    "device_profiles": {
      "$key": {
        ".read": true,
        ".write": "!data.exists()"
      }
    },
    "ip_profiles": {
      "$key": {
        ".read": true,
        ".write": true
      }
    },
    "app": {
      ".read": true,
      ".write": false
    }
  }
}
```

* `public_links`: a short link can be created once and never overwritten; it can only be removed (the History delete uses this).
  The advanced options (see *Version 2.8.0*) are validated here; `views` can only go up by exactly 1 per write (the website counts views with it).
* `user/<installationId>` holds the upload history of one phone (the app has no login).
* `device_profiles/<hash>` is **create-only**: the first ID of a phone is saved once and never changes (see *One profile per phone*).
* `ip_profiles/<hash>/freeUsed` counts free demo photos per network.
* `app` is read-only; fill `app/backend` (free-trial Supabase) from the console.
* **Do not** use rules that only open `photo_video_url/public_links`: the app then cannot save links or read history.

**Upload works in Supabase but no link / nothing in Firebase?** Open the app > Settings > *Check Firebase Connection*.
A line that says *BLOCKED by Firebase rules* means the rules above are not published yet. Also keep
**App Check enforcement OFF** for Realtime Database (the app does not use App Check).

---

## 🛰️ Remote control, messages and scanner (version 2.5.x)
**Remote switches in Firebase** (Realtime Database > `app` > `backend`). Add these keys next to the free-trial Supabase
keys that are already there. A missing key means "normal". `true` / `false` can be typed as boolean or text.

| Key | Example | What it does |
| --- | --- | --- |
| `demoEnabled` | `true` | `false` = the free demo is off (banner hidden, demo uploads refused) |
| `demoImageLimit` | `3` | how many free demo photos each phone gets |
| `ipLimitEnabled` | `true` | `false` = the free-demo limit per network (same public IP) is NOT checked at all |
| `ipFreeLimit` | `30` | how many free demo photos one network gets (used when `ipLimitEnabled` is on) |
| `maxSupabaseAccounts` | `5` | how many Supabase projects one user can add in Settings (1–10) |
| `maintenance` | `false` | `true` = the app does not open, it shows "App is under maintenance" (+ Try again) |
| `maintenanceMessage` | `Back at 6 PM` | optional text for the maintenance screen |
| `newUpdate` | `false` | `true` = the app compares `latestVersion` with its own version |
| `latestVersion` | `2.5.2` | if it is HIGHER than the installed version, "New update available" is shown |
| `updateLink` | `https://...apk` | opened by the **Update** button |
| `forceUpdate` | `false` | `true` = the update dialog cannot be dismissed |
| `updateMessage` | `Bug fixes` | optional text for the update dialog |

If `latestVersion` equals (or is lower than) the installed version, the app runs normally. The values are read when the
app opens (and "Try again" on the maintenance screen).

**Messages (Firebase Cloud Messaging):** every phone listens to the topic **`all`**. Android 13+ asks once for the
notification permission. Two ways to publish:
1. *Firebase console* > Engage > Messaging > New campaign > Notification > Target: *Topic* = `all`. Under
   **Additional options** set **Priority = High** (a normal priority message can be held back for a long time while the
   phone sleeps) and, if you want, a **notification image**. The system shows these messages itself, also when the app
   is closed: status bar icon = the app logo (white), accent colour = brand blue.
2. `tools/send_fcm.py` (a "data" message): the app builds the notification itself with the **coloured app logo**, the
   brand colour, an optional picture and a link, in a high-importance channel (pops up with sound), also when the app
   is closed. See the top of the script for the one-time setup.

If messages only arrive when the app is opened, the phone is holding the app back: Settings > **Notifications** has the
shortcuts (notification settings, battery "Don't optimize") and a hint for Autostart on Xiaomi / Oppo / Vivo / Realme /
Samsung phones. A force-stopped app receives nothing until it is opened once.

**QR scanner:** Search screen > the QR icon next to Paste. It asks for the camera permission (CameraX + ML Kit, both from
Google), shows a scan frame with a light button, and searches the scanned link at once. The camera picture is never saved
or uploaded.

**Downloads from the viewer:** Download (APK, files, PDF) saves the file straight into `Downloads` with a notification,
it no longer opens the browser / Supabase page.

## 🆕 Version 2.9.0 — QR from gallery, QR tile, safer names, steadier edits

* **QR scanner:** a *Gallery* button reads a QR code from a picture (also when the camera permission is off).
* **Quick Settings tile:** pull down the quick settings → *Edit* → drag **QR scanner** up. One tap opens the scanner.
* **Search:** typing `https://ivu.gt.tc` (or one of its pages) shows the banner of the service with an *Open website* button.
* **Advanced options:** the expiry choices (hours / minutes / days) are big buttons that fit every phone; a custom name is
  locked once it was created; an existing password stays unless *Change* is tapped; photos and videos have no
  *Allow download* switch (only documents, files and APKs); colours wrap instead of scrolling sideways.
* **Editing a link is steadier:** with the rules of this README the public link is replaced in **one step** (no moment
  without a page). With older rules the app still works (delete + write again, now protected against time outs).
  **Publish the rules of `firebase-rules.json` again** to get the one-step update.
* **History:** the big *Advanced options* button is gone; a pencil next to *ORIGINAL SUPABASE URL* opens the same page.
* **Settings → Notifications:** a direct on / off switch (also leaves / joins the Firebase topic).
* **Supabase:** one project is one entry (same URL or same key cannot be added again). If an upload fails it is retried
  once and then tried on the other saved projects; an error is shown only when all of them failed.
* **Blocked names:** many more abusive words, impersonation names and every page / folder name of the website
  (`features`, `disclaimer`, `how-it-works`, `thumb`, `config`, `cache`, `errors`, `web_...`).

## 🆕 Version 2.8.0 — Advanced link options (custom address, expiry, password, view limit, page design)

After the upload and the ad, **before the result page**, an *Advanced options* page opens. **Everything is optional**:
*Skip* keeps the default link. The same page can be opened later from History > a file > *Advanced options*.
(Not shown for the free demo photos. Remote switch: `advancedOptionsEnabled` = `false` hides the page.)

| Option | What the user can do |
| --- | --- |
| **Link address** | The default link (`ivu.gt.tc/image_AbC123`) is shown first. The user can type his own name: `ivu.gt.tc/<name>`. 3–30 characters, letters / numbers / `-` / `_` only (saved in lower case). Spaces and symbols like `@ #` are refused. Reserved and blocked words are refused (see below). *Check availability* reads `public_links/<name>`. |
| **Expiry** | 1 hour / 24 hours / 7 days / 30 days / custom (minutes, hours or days, 5 minutes – 365 days). |
| **Password** | 4–64 characters. Only a PBKDF2 hash is saved. |
| **View limit** | 1 – 1,000,000 views. |
| **Background colour** | 12 swatches or any `#RRGGBB`. |
| **Button under the file** | Text (30 chars), colour and a web link (`http(s)` only). |
| **More** | Allow download on / off, page title (60 chars), page message (200 chars). |

### What is saved in Firebase (for the website)
The options are saved in **`public_links/<id>`** (what the website reads) and in the history entry
`user/<uid>/<category>/<id>` (what the app reads). `<id>` is the custom name, or `image_xxx` / `video_xxx` ... when no custom
name was chosen. **A custom link has no type prefix: the file type is the `type` field of the node**
(`image`, `video`, `document`, `file` or `apk`).

| Field | Type | Meaning |
| --- | --- | --- |
| `expiresAt` | number (ms since 1970) | after this time the link must answer "expired" (410) |
| `maxViews` | number | after this many views the link must answer "limit reached" (410) |
| `views` | number | **counted by the website**: +1 per page view (rules allow exactly +1 per write) |
| `hasPassword` | true | a password is set |
| `passwordHash` / `passwordSalt` / `passwordIter` | string (64 hex) / string (32 hex) / number | see below (public node only) |
| `bgColor` | `#RRGGBB` | page background |
| `button` | `{ text, color, url }` | button below the file |
| `pageTitle`, `pageMessage` | string | shown on the page |
| `allowDownload` | `false` | only written when OFF; missing = download allowed |

**Password check on the website.** `hash = PBKDF2-HMAC-SHA256(password as UTF-8, salt = bytes of the hex salt, passwordIter, 32 bytes)`, as hex.
PHP: `hash_pbkdf2('sha256', $password, hex2bin($salt), $iter, 64, false)` — compare with `hash_equals`.
JavaScript: `crypto.subtle.deriveBits({name:'PBKDF2', hash:'SHA-256', salt, iterations}, key, 256)`.
**Be honest with users:** the password is a gate on the page. The Supabase file address is public in `public_links/<id>/supabaseUrl`
(the existing design of the website), so someone who reads Firebase directly can still open the file. For real secrecy the
file itself would have to be encrypted (possible later).

**Website checklist** (to do): resolve `/<name>` with `public_links/<name>` for custom names too; refuse expired links and links
over their view limit; ask for the password; add +1 to `views` (`ServerValue.increment(1)` or REST); draw `bgColor`,
`pageTitle`, `pageMessage` and the `button`; hide the download button when `allowDownload` is `false`;
never let a custom name collide with a page of the website (`about`, `contact`, `terms`, `privacy` ... are refused by the app).

### Expiry: when is the file really deleted?
At its expiry time the link stops working (the website checks `expiresAt`). The **file** is deleted from **Supabase and Firebase**
by the app (the Supabase keys exist only on the phone): when the app starts, and after the History is loaded. It uses the history
saved on the phone, so no Firebase request is made when nothing has expired. If the phone stays off, the deletion waits until the
app is opened next. The *view limit* only blocks the link; it does not delete the file.

### Blocked names
`BlockedWords.kt` refuses: owner names (`admin`, `praveen`, `praveen_studio` ...), pages and system words (`about`, `privacy`,
`api`, `login` ...), abusive words (English and Hindi/Urdu in English letters) and words for illegal content; number tricks
(`adm1n`) are caught too. The developer can add words **without an app update** in Firebase `app/backend/blockedWords`
(words separated by commas).

### Notes for developers
* Changing a link moves it atomically (`updateChildren` removes the old nodes and creates the new ones), so it works with the
  existing rules; if the new name is taken in the meantime Firebase refuses the whole change.
* `app/backend` new keys: `advancedOptionsEnabled` (`true`/`false`), `blockedWords` (text).
* Publish the new `firebase-rules.json` (validation of the new fields + `views` counter). The app also works with the old rules.

## 🆕 Version 2.7.0 — speed, several Supabase projects, new Settings

### Version numbers (how the next versions are counted)
`X.Y.Z`: **X** = big upload (2 → 3), **Y** = new features (7 → 8), **Z** = bug fix (0 → 1).
`versionCode` = `X*100 + Y*10 + Z` (2.9.0 = 290). Change both in `app/build.gradle.kts`, the version in the
workflow file (`android-ci.yml`) and the `version` default in `FirebaseDatabaseManager.kt`.

### Firebase switches (app/backend, no app update needed)
New keys next to the ones in the table above: **`ipLimitEnabled`** (network limit on / off), **`ipFreeLimit`** (how many
free photos per network) and **`maxSupabaseAccounts`** (how many Supabase projects a user can add). A missing key means
"normal" (on / 30 / 5). When `ipLimitEnabled` is `false` the network limit is not checked at all (and the public IP
lookup is skipped).

### Several Supabase projects (up to 5) with automatic switching
* Settings > Supabase Storage > **Set up Supabase** opens ONE full setup screen: project URL, anon key, bucket, plan size,
  a copy-paste SQL script (creates the public bucket + upload / read / delete permissions) and a **full check**.
  Every "set up Supabase" button of the app (Home banner, upload dialogs, share dialog) opens this same screen.
* After setup, Settings shows **Supabase Connected**. Tap a project for **Edit / Delete / Analyze / Use for uploads**.
* Uploads go to the *active* project. When it is full (Supabase answers "storage full", or the estimate reaches ~98 %)
  the app marks it full, switches to the next project that has room, shows a **notification + message**, and finishes the
  upload there by itself.
* **Analyze** page: used / free storage per project (bar + %), files per type, last scan, and a setup check
  (connection, public bucket, upload / read / delete permission). *Scan storage* reads the real size from Supabase
  (needs the SELECT permission from the setup SQL).
* The used-storage estimate is updated locally on every upload and delete and re-synced whenever the History is loaded.
  The plan size is typed by the user (the anon key cannot read it): 1 GB = Free, 100 GB = Pro.
* Deleting an upload in History deletes the file in **the Supabase project that holds it**, then the Firebase entries.
* The encrypted Firebase backup now holds all projects (old single-project backups and old installs are converted
  automatically).

### Fewer Firebase requests, less memory
* No live listeners any more. The History is loaded **once** when the History screen opens (only if the copy in memory is
  older than 5 minutes) or with the refresh button; uploads / deletes update the list locally. User counters are read at
  most every 10 minutes and updated locally after an upload.
* App start reads the whole `app` node with **one** request (before: 3). The notification topic is subscribed once.
* The name in Settings is sent to Firebase only when **Save / Update** is tapped (before: auto-save while typing).
* The encrypted Supabase preferences are created once per process (before: for every screen / repository).
* Image caches are smaller (disk 16 MB, memory 10 %); the viewer decodes at most 1920 px.
* WorkManager (unused, created its own database at every start) and the unused `MediaUploadWorker`, the empty
  `UnityRewardedBackend.kt` and the template `ExampleInstrumentedTest` were removed.

### Legal pages
Settings > About & Legal opens `https://ivu.gt.tc/about`, `/contact`, `/terms` and `/privacy` in the browser.
**These pages must exist on the website.**

## 📺 Ads (version 2.4.x)
**Automatic ad after a link was created:** when an upload is done, an ad (Unity *Interstitial* placement
`BP_Interstitial_Android`) shows by itself BEFORE the result page (link, Copy, Share, QR). Copy / Share / QR are not
touched. If no ad can be shown (no internet, no fill, SDK error) the result page appears at once; the user never waits
more than a few seconds and the app never gets stuck.
* Interstitial is the right format for an ad that shows by itself. A *Rewarded* ad must be started by the user
  (Unity rules), so the rewarded Copy / Share flow is kept in the code but switched off (`rewardedAdsEnabled = false`).
* Code: `ads/` (`AdsManager`, `AdsConfig`, `AdsConsent`, `UnityAdsBridge.java` = the only file that uses the Unity SDK
  4.19.0, written in Java) and `ui/components/RewardedActionGate.kt` (rewarded flow, off).
* **Premium-ready (not implemented):** `AdsConfig(adsEnabled, premiumAdFree, rewardedAdsEnabled,
  allowActionWhenAdUnavailable, interstitialAdsEnabled)`. A future, verified Premium entitlement only calls
  `AdsConfigProvider.update(...)`. With ads off / Premium nothing is initialized.
* **Why no ad shows?** Settings > "Ads: ..." line (tap to refresh) shows test or real ads, SDK state and the last ad
  error (for example `52100` = no fill). New Unity placements can take a while before real ads fill. To check that
  everything works, use **test ads**: GitHub > Settings > Secrets and variables > Actions > Variables > add
  `ADS_TEST_MODE` = `true`, run the workflow, and set it back to `false` for real ads.
* **Test vs real ads:** debug builds use Unity test mode by default; the GitHub workflow passes
  `-PadsTestMode=` (variable above, default `false` = real ads); `release` / `optimized` always use real ads.
* IDs (in `app/build.gradle.kts`): Game ID `800434128`, rewarded `BP_Rewarded_Android`, interstitial
  `BP_Interstitial_Android`, banner `BP_Banner_Android` (not used). The Organization Core ID and the Stats API key are
  not needed in the app.
* Privacy: no consent screen yet, so only non-personalized (non-behavioral) ads are requested. A consent screen only has
  to call `AdsConsent(context).setState(...)`. Check the consent rules of your regions before launch.
* Safety net: if the Unity code ever fails to compile, the GitHub workflow builds an APK **without** ads
  (`app/src/fallback/UnityAdsBridge.java.txt`) and shows a warning, so there is always an APK.

## 🔳 QR code (version 2.3.0)
Wherever a link is shown (upload result, History card, in-app link viewer) there is a **QR code** button. It opens a
bubble style QR (round gradient dots, soft corners, the app logo in the middle) and **Download QR** saves it as a
picture in `Pictures/PhotoVideoURL` (the link is written under the code). The code uses the highest error correction
so it still scans with the logo in the middle (tested with a QR decoder at different sizes, blur and compression).

## 🔎 Search & Download (version 2.2.0)
Bottom bar > **Search** opens its own screen: paste a link, it is shown, and **Download** saves it to
`Downloads/PhotoVideoURL`. Recent searches are kept **on the phone only** (not in Firebase).
* Photo Video URL links (`https://ivu.gt.tc/image_xxxx` or just the id): image, video, document, file, APK.
* Any direct link to a photo, video, audio, PDF or file, and web pages that publish their photo / video in the
  standard page tags (`og:image`, `og:video`, `<video src>`).
* Links of YouTube, Instagram, Facebook, X/Twitter, Snapchat, TikTok and similar services are **not** downloaded
  (against their rules); the app says so and offers "Open in browser".
* Support in Settings opens `https://ivu.gt.tc/contact`.

## 📱 One profile per phone (version 2.1.0)
* The first installation ID of a phone is saved in Firebase (`device_profiles`, key = salted hash of the Android ID).
  After an **uninstall, re-install or "clear data"** the same ID is found again: the **History comes back**, the
  **3 free demo photos are not given again**, and no new profile is created in the database.
* The user's **Supabase settings are backed up in Firebase, encrypted** with a key that only the phone has, and are
  restored automatically after a re-install (no new set-up). **Disconnect** in Settings removes the backup.
* The public **IP is not used to choose a profile** (people on the same Wi-Fi or mobile network would see each
  other's History). It only limits the free demo photos per network (default 30). The developer can switch this limit on / off and change the number in Firebase `app/backend` (`ipLimitEnabled`, `ipFreeLimit`), see *Version 2.7.0*.
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

## 📦 APK file (GitHub Releases)
* `PhotoVideoURL-v2.9.0.apk` – the only APK that is published.

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
