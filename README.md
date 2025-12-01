<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" alt="Plyr" width="192"/>
</div>

Android music plyr built with Kotlin and Jetpack Compose. Stream music from YouTube, organize playlists, and control playback.

## Build

Made a small shell script to simplify the build process:

```bash
# Clone the repository
git clone https://github.com/josemri/plyr.git
cd plyr

# Run the build script
./run.sh
```

## Project Structure

```
plyr/
├── app/src/main/java/com/plyr/
│   ├── database/      # Room entities
│   ├── network/       # API integration
│   ├── ui/            # Compose screens
│   ├── viewmodel/     # State management
│   ├── service/       # Background services
│   └── utils/         # Utilities
├── gradle/            # Dependencies
├── run.sh
└── README.md
```

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.CAMERA" />
```

## Roadmap

- [x] **Smart Recommendations** - Playlist recommendations based on your music taste
- [ ] **Drag & Drop** - Reorder songs in playlists with long press and drag
- [ ] **Now Playing Indicator** - Highlight current track with color change in playlists
- [ ] **Fix add to playlist songs** - When swipe song to add to playlist, playlists don't show up
- [x] **Fix dark Mode** - Some screens have white background or not enough contrast


## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

_plyr is Free Software: You can use, study, share, and improve it at will. Specifically you can redistribute and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl-3.0.en.html) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This project uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), originally created by [Team NewPipe](https://github.com/TeamNewPipe), licensed under GPL-3.0.

----

<div align="center">
  <b>Made with ♫ by josemri</b>
</div>
