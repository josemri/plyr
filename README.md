# _plyr

Android music plyr built with Kotlin and Jetpack Compose. Stream music from YouTube, organize playlists, and control playback.

## Build

Made a small shell script to simplify the build process:

```bash
# Clone the repository
git clone https://github.com/yourusername/plyr.git
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
└── README.md
```

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

##  License

none yet

---

**Made with ♫ by me**
