# Fixes in this version (app only - web code is unchanged)

## New behaviour you asked for
* **Links open inside the app like on the website.** `MediaViewerScreen` (ui/components) follows the same rules as the PHP site
  (`domain/util/LinkRules.kt` is a port of security.php / media_handler.php):
  image -> shown (pinch / double-tap zoom; SVG + GIF via a small `<img>` page), video -> in-app player,
  PDF -> pages rendered in the app (fallback: Download), other documents / files / APKs -> download card
  (`?download=<name>`, APK is only downloaded). Disabled link -> "Link disabled", unknown -> "Not found",
  Firebase down -> "Temporarily unavailable" + Try again.
* The viewer opens on top of whichever tab is open (before, the preview only appeared on the Home tab).
* Only real media links (`/image_…`, `/video_…`, `/document_…`, `/file_…`, `/apk_…`) are taken by the app.
  `/privacy`, `/about` etc. stay in the browser.
* **Android 9 (and 10-12) pickers:** Images / Videos / free-trial picker now use the system file picker (SAF, no storage
  permission). The Android photo picker is used only on Android 13+.
* **Own Supabase did not show on the website:** (1) the app now checks that the bucket is *public* (Test connection and after
  every upload) and tells you if it is not - a private bucket was the usual reason; (2) project URL is normalised to
  `https://<ref>.supabase.co` (pasted `http://`, uppercase, `/rest/v1/` all work); (3) a failed Firebase "public link" write
  is now an error (before the app showed Success and the link gave 404); (4) file extensions with spaces
  ("Meeting 10.30 AM") no longer create URLs the website refuses.

## Other bugs fixed
* Rotation / reopening from recents no longer re-opens the share dialog or viewer (duplicate uploads).
* Mixed share (photo + video + file) -> each file gets its own category.
* A failed file in a batch no longer drops the rest; Retry continues the batch. Batches run one after another.
* Uploads are really cancelled when the screen/ViewModel goes away (OkHttp call is cancelled).
* APK extractor: split-APK apps are exported as one `.apks` zip (base + splits); Android 9 no longer fails because of the
  optional Downloads copy.
* History: thumbnails work again for records loaded from Firebase, local cache shows even when offline, "clear" really
  hides entries on this device, link file names are stored (`fileName`) so downloads are not called `apk_xxx_...`.
* Supabase URL / key are no longer copied to Firebase (old copies are deleted when you save/clear in Settings).
  Default user name "Praveen" removed. Credentials are excluded from Auto Backup (restore crash on new phones).
* CI: `debug.keystore.base64` is included, so every GitHub build is signed with the same key and updates install.
  **Keep this file committed.**

## Still needs your attention (cannot be fixed inside the app without a login page)
* Firebase rules: the app has no login, so rules cannot separate users. At least use:
```json
{
  "rules": {
    "public_links": { "$id": { ".read": true, ".write": "!data.exists()" } },
    "photo_video_url": { "public_links": { "$id": { ".read": true } } },
    "user": { "$uid": { ".read": true, ".write": true } },
    "app": { ".read": true, ".write": false }
  }
}
```
  (`public_links` create-only stops people from overwriting someone's link; `app` is read-only, fill `app/backend` from the
  console.) The developer Supabase key in `app/backend` is readable by every app install and the 3-free-uploads counter is
  client side - both are limits of an app without login.
* Uploads continue only while the app process is alive (no foreground service yet).
* Website only shows `https://<project>.supabase.co` projects (web rule), custom domains upload fine but do not show.
