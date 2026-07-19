# StreamFlow

A fast, private, ad-free streaming app for Android: a full YouTube client, live Cambodian TV, and ad-blocked donghua & drama portals — in one app. No Google account, no API key, no ads, no tracking. Everything runs on your phone.

**[⬇ Download the latest APK](https://github.com/Tann-Menghong/StreamFlowApp/releases/latest)**

Requires Android 5.0+ (works best on Android 7.0+). The app updates itself: when a new release is published here, StreamFlow offers the update in-app.

## Features

### Watching (YouTube)
- Adaptive video playback up to 1080p (ExoPlayer/Media3) with hardware-aware quality: phones with hardware VP9/AV1 decode and plenty of RAM start at 1080p automatically
- Fast everywhere: 0.8s playback start, on-device media cache (replays and back-seeks stream from disk), next-video pre-buffering, keyframe-snap seeking, decoder fallback
- Fullscreen player with gestures: double-tap skip, swipe for brightness/volume, horizontal swipe-to-seek with storyboard previews, pinch to zoom, hold for 2× speed, screen lock
- SponsorBlock auto-skip with per-category control (sponsors, intros, outros, self-promo…)
- DeArrow community titles (clickbait-free) and real dislike counts (Return YouTube Dislike)
- Chapters, subtitles/CC, audio language picker for dubbed videos, playback speed with per-channel memory, repeat
- 5-band custom equalizer + presets + volume boost
- Picture-in-Picture, background/audio-only playback with media notification, Bluetooth resume
- Live streams (adaptive HLS/DASH), Shorts feed with vertical swipe
- Comments with pinned/creator-hearted/owner badges and threaded replies
- Sleep timer that survives autoplay — by minutes or "end of this video"
- Bookmarks shown as markers right on the seek bar

### Live TV (PDTV tab)
- Dozens of live channels — Khmer TV (TVK, BTV, Bayon, CTN, CNC…), sports (beIN, Fox Sports…), news, movies, Korean & Vietnamese TV
- Native ExoPlayer playback tuned for live: sub-second channel start, deep 60s anti-stutter buffer, chunkless HLS preparation, pre-warmed channel servers for instant zapping
- Self-healing streams: silent reconnects, hard segment retries, automatic live-edge rejoin
- Channel grid with logos, fullscreen mode, screen stays awake, last channel auto-plays

### Donghua & Drama tabs
- donghuafun.com and kisskh.co in ad-blocked in-app browsers: ad domains, popunder redirects, and overlay ads blocked
- Mobile layout that fits your screen, pinch-zoom, fullscreen video, per-site "continue where you left off"

### Your library — all local, all private
- Subscriptions with channel groups, per-channel notification bells, and new-upload notifications (quiet hours supported; "Watch later" button right on the notification)
- Favorites, Watch Later, watch history with resume positions, bookmarks ("clip moments" at exact timestamps)
- Watch stats dashboard: 7-day activity chart and your top channels
- Local playlists with reorder, plus YouTube playlist browsing and "Play all"
- Downloads (video or audio) with live progress, retry and cancel; subtitles saved alongside video downloads; optional Wi-Fi auto-download of Watch Later
- Playback queue that survives app restarts
- Incognito mode: watch without leaving any trace in history or searches
- Full JSON backup/restore (everything: subscriptions, favorites, playlists, bookmarks, groups, bells, ordering, timestamps) + weekly auto-backup + OPML export + import from NewPipe or Google Takeout

### Home & discovery
- Endless personalized "For You" feed seeded by your history, searches, and picked topics
- Trending by country (20 countries), category chips with custom topics
- Clean Home: search lives behind a single icon (with voice search); dedicated Search tab available
- Search with live suggestions, filters (videos/channels/playlists, length, upload date), and sorting
- Hide watched videos, hide Shorts, "not interested" for videos and channels
- Continue Watching row, hero carousel, list/grid layouts (2–3 columns)

### On-device AI (optional)
- One-time download of a small open model (Qwen 2.5 0.5B, ~550 MB) — then video summaries and Q&A about any video, fully offline, no API key

### Polish
- English and Khmer (ភាសាខ្មែរ)
- Dark / AMOLED / Light / System themes, accent colors (incl. custom), font sizes and styles, corner styles, three design styles (Modern / Aurora glass / Classic)
- High refresh rate at full panel resolution, edge-to-edge, predictive back, per-device performance tuning
- Launcher shortcuts (including your recent channels), Quick Actions widget, and a scrollable Latest Uploads widget
- Open YouTube video/channel/playlist links and shares directly in StreamFlow

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

StreamFlow is an independent open-source project. It is not affiliated with, endorsed by, or sponsored by YouTube, Google, or any of the streaming sites accessible through the app. All trademarks belong to their respective owners.
