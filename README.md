# StreamFlow

A fast, private, ad-free YouTube client for Android. No Google account, no API key, no ads, no tracking — everything runs on your phone.

**[⬇ Download the latest APK](https://github.com/Tann-Menghong/StreamFlowApp/releases/latest)**

Requires Android 5.0+ (works best on Android 7.0+). The app updates itself: when a new release is published here, StreamFlow offers the update in-app.

## Features

### Watching
- Adaptive video playback up to 1080p (ExoPlayer/Media3) with hardware-aware quality: phones with hardware VP9/AV1 decode and plenty of RAM start at 1080p automatically
- Fullscreen player with gestures: double-tap skip, swipe for brightness/volume, horizontal swipe-to-seek with storyboard previews, pinch to zoom, hold for 2× speed, screen lock
- SponsorBlock auto-skip (sponsors, intros, outros, self-promo and more)
- Chapters, subtitles/CC, playback speed, repeat, equalizer + volume boost
- Picture-in-Picture, background/audio-only playback with media notification, Bluetooth resume
- Live streams (adaptive HLS/DASH)
- Shorts feed with vertical swipe
- Comments with pinned/creator-hearted/owner badges and threaded replies
- Sleep timer that survives autoplay (enforced by the playback service)

### Your library — all local, all private
- Subscriptions with channel groups, per-channel notification bells, and new-upload notifications (quiet hours supported)
- Favorites, Watch Later, watch history with resume positions, bookmarks ("clip moments" at exact timestamps)
- Local playlists with reorder, plus YouTube playlist browsing and "Play all"
- Downloads (video or audio) via the system download manager, with retry; optional Wi-Fi auto-download of Watch Later
- Playback queue that survives app restarts
- Incognito mode: watch without leaving any trace in history or searches
- Full JSON backup/restore (subscriptions, favorites, playlists, bookmarks, groups, bells, ordering, timestamps) + import from NewPipe or Google Takeout

### Home & discovery
- Endless personalized "For You" feed seeded by your history, searches, and picked topics
- Trending by country (20 countries), category chips with custom topics
- Search with live suggestions, filters (videos/channels/playlists), and sorting
- Hide watched videos, hide Shorts, "not interested" for videos and channels
- Continue Watching row, hero carousel, list/grid layouts (2–3 columns)

### On-device AI (optional)
- One-time download of a small open model (Qwen 2.5 0.5B, ~550 MB) — then video summaries and Q&A about any video, fully offline, no API key

### Polish
- English and Khmer (ភាសាខ្មែរ)
- Dark / AMOLED / Light / System themes, accent colors (incl. custom), font sizes and styles, corner styles, Modern/Classic design
- High refresh rate at full panel resolution, edge-to-edge, per-device performance tuning
- Launcher shortcuts + home-screen widget
- Open YouTube links and shares directly in StreamFlow

## Tech stack

Kotlin · Jetpack Compose (Material 3) · ExoPlayer/Media3 · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · Room · DataStore · WorkManager · Coil · OkHttp · MediaPipe LLM Inference

No YouTube API key: extraction is done on-device by NewPipe Extractor.

## Building

```
git clone https://github.com/Tann-Menghong/StreamFlowApp.git
cd StreamFlowApp
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`. Requires JDK 17 and the Android SDK (compileSdk 34).

## Disclaimer

StreamFlow is an independent open-source project. It is not affiliated with, endorsed by, or sponsored by YouTube or Google. All trademarks belong to their respective owners.
