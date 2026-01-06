<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="192" align="left" style="margin-right:16px;"/>

**A minimalist, terminal-inspired music player for Android**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)

Built this music player because I wanted something simple with a terminal aesthetic. Streams from YouTube and Spotify, supports voice commands, has gesture controls for quick actions, and works with local files too. The UI is ASCII-inspired with monospace fonts everywhere. Also throws in auto theme switching based on your phone's light sensor.


## Build from Source

There is a bash script in case you want to build from source please take a pick at the `run.sh` file for more details.
```bash
git clone https://github.com/josemri/plyr.git
cd plyr
./run.sh  # APK will be in app/release/plyr.apk
```


## Project Structure

```
plyr/
├── app/src/main/java/com/plyr/
│   ├── assistant/     # Voice assistant integration
│   ├── database/      # Room entities
│   ├── network/       # API clients (Spotify, YouTube, AcoustID, Last.fm)
│   ├── ui/            # Compose screens & components
│   ├── viewmodel/     # State management
│   ├── service/       # Background playback service
│   └── utils/         # Utilities
├── gradle/            # Dependencies
└── run.sh             # Build script
```


## Permissions

```xml
INTERNET              # Stream music and fetch metadata
RECORD_AUDIO          # Voice assistant and song recognition
FOREGROUND_SERVICE    # Background playback
WAKE_LOCK             # Keep playing when screen off
POST_NOTIFICATIONS    # Playback controls
CAMERA                # QR code sharing
READ_EXTERNAL_STORAGE # Save and load local songs
READ_MEDIA_AUDIO      # Local music files
```


## Roadmap

- [x] **Smart Recommendations** - Playlist recommendations based on your music taste
- [x] **Fix dark Mode** - Some screens have white background or not enough contrast
- [x] **assistant integration** - User will be able to ask my assistant to play songs, albums etc...
- [x] **Now Playing Indicator** - Highlight current track with color change in playlists
- [x] **Fix add to playlist songs** - When swipe song to add to playlist, playlists don't show up
- [x] **Fix accesibility** - Control bar sometimes does not detect clicks because of this
- [ ] **Drag & Drop** - Reorder songs in playlists with long press and drag
- [ ] **Fix loop and repeat** - Loop and repeat buttons don't as expected
- [ ] **Lyrics Support** - Show lyrics for current song if available
- [ ] **Sleep Timer** - Stop playback after a set time
- [ ] **Widget Support** - Home screen widget for playback controls
- [ ] **Android Auto** - Support for Android Auto interface



## License

[![GNU GPLv3](https://www.gnu.org/graphics/gplv3-127x51.png)](https://www.gnu.org/licenses/gpl-3.0.en.html)

**_plyr** is Free Software: You can use, study, share, and improve it at will. Specifically you can redistribute and/or modify it under the terms of the [GNU General Public License](https://www.gnu.org/licenses/gpl-3.0.en.html) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
This project uses:

- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), originally created by [Team NewPipe](https://github.com/TeamNewPipe), licensed under GPL-3.0.
- [AcoustID](https://acoustid.org/) by [Lukáš Lalinský](https://oxygene.sk/), uses Chromaprint (LGPL-2.1+) and the AcoustID web service (terms of use apply).
- [Last.fm](https://www.last.fm/) API by [Last.fm](https://www.last.fm/), subject to their [API Terms of Service](https://www.last.fm/api/tos).

---

<div align="center">
  <b>Made with ♫ by <a href="https://github.com/josemri">josemri</a></b>
  <br><br>
  <sub>If you find this useful, give it a ⭐️</sub>
</div>
