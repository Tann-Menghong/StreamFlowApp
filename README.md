# StreamFlow

An Android client for watching YouTube videos without ads, in the same spirit as
[NewPipe](https://github.com/TeamNewPipe/NewPipe): it parses YouTube's public web
pages directly via [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
instead of going through YouTube's official, ad-injecting API/player. No account,
sign-in, or YouTube/Google API key is required.

## Features

- Browse trending videos, search YouTube, and watch videos with no ads
- Channel pages: tap any uploader's name to view their avatar, banner,
  subscriber count, description, and full video list — just like YouTube
- Background/audio-only playback that keeps running when the app is minimized,
  with media notification controls (play/pause/seek) — a Premium-like experience
- Local watch history and bookmarks ("Library" tab), stored entirely on-device
- Quality selection for videos that have multiple progressive (muxed) formats
- Dark UI built with Jetpack Compose and Material 3

## How it works

StreamFlow does not use YouTube's official Data API or IFrame player. Instead it
uses NewPipeExtractor — the same open-source extraction library that powers
NewPipe — to parse YouTube's web pages and resolve direct, playable stream URLs.
Because playback never goes through YouTube's own ad-injecting player, there are
no ads. Playback itself is handled by [Media3 ExoPlayer](https://developer.android.com/media/media3),
running inside a `MediaSessionService` so video/audio keeps playing in the
background with system media controls.

v1 plays progressive ("muxed") formats, which top out around 720p, plus an
audio-only mode for background listening. Merging separate video-only and
audio-only DASH streams (needed for 1080p+) is not implemented yet — see
[Roadmap](#roadmap).

## Building

### Requirements

- Android Studio (Koala or newer) or the command line with JDK 17
- Android SDK with `compileSdk 34` installed

### Command line

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### CI builds

Every push builds a debug APK via GitHub Actions
(`.github/workflows/build.yml`); download it from the workflow run's
**Artifacts** section (`streamflow-debug-apk`) without needing a local Android
toolchain.

## Installing

Sideload the built APK (`adb install app-debug.apk`, or copy it to a device and
open it with "install from unknown sources" enabled). StreamFlow is not, and
will not be, published on the Play Store.

## Legal note

StreamFlow is an independent, unofficial client. It is not affiliated with,
endorsed by, or sponsored by YouTube or Google. It works by parsing YouTube's
publicly accessible web pages rather than using YouTube's official API, which
means it does not comply with YouTube's Terms of Service — the same situation
as NewPipe. Use it at your own risk, for personal use only.

## Roadmap

- Merge separate video-only + audio-only DASH streams to support 1080p and
  above (today's progressive formats top out around 720p)
- Subscriptions (a followed-channels feed; channel pages themselves are done)
- Playlists
- SponsorBlock-style segment skipping
