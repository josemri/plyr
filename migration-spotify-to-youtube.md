# Plan de Migració: Eliminar Spotify, substituir per YouTube

**Data inici:** 2026-08-20
**Data finalització:** 2026-08-20
**Estat:** ✅ COMPLETAT

---

## Resum de la migració

S'ha eliminat **tota la dependència de Spotify API** de l'app, substituint la funcionalitat per YouTube o solucions locals. L'app és 100% YouTube-only (llevat del parsing de URLs Spotify per NFC/QR que es manté intencionadament).

### Mètriques finals

| Mètrica | Abans | Després | Canvi |
|---|---|---|---|
| Arxius Kotlin (main) | 69 | 62 | -7 |
| Línies de codi | ~19.600 | ~12.663 | -6.937 (~35%) |
| SpotifyRepository | 1642 línies | 0 | Eliminat |
| Entitats DB | `spotifyId` / `spotifyTrackId` | `remoteId` / `remoteTrackId` | Renombrat |
| Tests | 148 | 144 | -4 (tests de models Spotify eliminats) |
| Imports Spotify | ~30 | 0 | Eliminats |
| Tipus `SpotifyTrack/Playlist/Artist` | ~40 usos | 0 | Renombrats a `AppTrack/Playlist/Artist` |
| Camp `spotifyUrl` | ~13 usos | 0 | Renombrat a `shareUrl` |
| Traduccions Spotify | ~30 claus | 0 | Eliminades |
| Bugs resolts | B3 (runBlocking) | ✅ | Eliminat amb Config |
| Seguretat | S1+S2 (secrets Spotify) | ✅ | Eliminats amb SpotifyRepository |

---

## Fases completades

### ✅ Fase 0 — Preparació (no trencar res)

**Data:** 2026-08-20
**Tests:** 148/148 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 0.1 | Renombrat `spotifyId` → `remoteId`, `spotifyTrackId` → `remoteTrackId` a entitats + DAO + FK + extensions + 20+ fitxers | ✅ |
| 0.2 | `toSpotifyPlaylist()` → `toAppPlaylist()`, `toSpotifyTrack()` → `toAppTrack()` | ✅ |
| 0.3 | Creada `MIGRATION_6_7` (`ALTER TABLE RENAME COLUMN`) — preserva dades existents | ✅ |
| 0.4 | Database version 6 → 7 | ✅ |

---

### ✅ Fase 1 — Eliminar la cerca Spotify del SearchScreen

**Data:** 2026-08-20
**Tests:** 148/148 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 1.1 | Eliminada cerca Spotify (tracks/àlbums/playlists/artistes) de SearchScreen (~1584→~430 línies) | ✅ |
| 1.2 | Eliminada vista detall àlbum Spotify inline | ✅ |
| 1.3 | Eliminat `SpotifyArtistDetailView.kt` sencer | ✅ |
| 1.4 | Eliminat toggle `sp:` / `yt:` — només queda YouTube per defecte | ✅ |
| 1.5 | Eliminat `SearchEngine.SPOTIFY` de l'enum | ✅ |
| 1.6 | Eliminades crides a `SpotifyRepository.searchAll()` i `getAlbumTracks()` | ✅ |
| 1.7 | Netegades traduccions | ✅ |

---

### ✅ Fase 2 — Eliminar PlaylistScreen Spotify (la més grossa)

**Data:** 2026-08-20
**Tests:** 148/148 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 2.1 | Eliminades funcions de sync de `PlaylistLocalRepository` (938→280 línies) | ✅ |
| 2.2 | Eliminada secció "Liked Songs" | ✅ |
| 2.3 | Eliminada secció "Saved Albums" | ✅ |
| 2.4 | Eliminada secció "Followed Artists" + detall artista | ✅ |
| 2.5 | Eliminat `<sync>` button | ✅ |
| 2.6 | Cerca tracks només amb YouTubeSearchManager | ✅ |
| 2.7 | Eliminades funcions follow/unfollow/update de Spotify | ✅ |
| 2.8 | Models pas a `SpotifyTrack` (ara `AppTrack`) | ✅ |
| 2.9 | Eliminat `Recommendations.kt` (231 línies) | ✅ |
| 2.10 | Eliminat `SpotifyPlaylistDetailView` | ✅ |
| 2.11 | CreatePlaylistScreen netejada (sense toggle Spotify) | ✅ |

---

### ✅ Fase 3 — Netejar components UI compartits

**Data:** 2026-08-20
**Tests:** 148/148 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 3.1 | `SongListItem.kt` netejat (877→418 línies) | ✅ |
| 3.2 | `SongMenuDialog.kt` netejat (424→~180 línies) | ✅ |
| 3.3 | Eliminat `SongList.kt` (codi mort) | ✅ |
| 3.4 | `ShareDialog.kt` netejat (sense fallback Spotify URL) | ✅ |
| 3.5 | `HomeScreen.kt` netejat (sense cerca Spotify) | ✅ |
| 3.6 | `PlaylistScreen.kt` — URLs Spotify→YouTube als Song objects | ✅ |

---

### ✅ Fase 4 — Eliminar SpotifyRepository i infraestructura

**Data:** 2026-08-20
**Tests:** 148/148 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 4.1 | Eliminat `SpotifyRepository.kt` (1642 línies) | ✅ |
| 4.2 | Eliminat `SpotifyTokenManager.kt` | ✅ |
| 4.3 | Eliminat `SpotifyAuthEvent.kt` | ✅ |
| 4.4 | Cread `SpotifyModels.kt` (després `AppModels.kt`) amb data classes extretes | ✅ |
| 4.5 | `Config.kt` netejat (659→429 línies, 15 funcions Spotify eliminades) | ✅ |
| 4.6 | `ConfigScreen.kt` netejat (secció Spotify eliminada) | ✅ |
| 4.7 | `MediaMetadataExtractor.kt` netejat (sense Spotify metadata + `MediaType.SPOTIFY_*`) | ✅ |
| 4.8 | `FeedScreen.kt` netejat (branques Spotify eliminades) | ✅ |
| 4.9 | `MainActivity.kt` netejat (sense `handleSpotifyCallback`) | ✅ |

---

### ✅ Fase 5 — Neteja final i renombrat de models

**Data:** 2026-08-20
**Tests:** 144/144 ✔

| Pas | Què s'ha fet | Estat |
|---|---|---|
| 5.1 | Renombrat `SpotifyTrack` → `AppTrack` | ✅ |
| 5.2 | Renombrat `SpotifyPlaylist` → `AppPlaylist` | ✅ |
| 5.3 | Renombrat `SpotifyArtist` → `AppArtist` | ✅ |
| 5.4 | Renombrat `SpotifyImage` → `AppImage` | ✅ |
| 5.5 | Renombrat `SpotifyAlbumSimple` → `AppAlbumSimple` | ✅ |
| 5.6 | Renombrat `SpotifyModels.kt` → `AppModels.kt` | ✅ |
| 5.7 | Renombrat `SpotifyModelsTest.kt` → `AppModelsTest.kt` (tests actualitzats) | ✅ |
| 5.8 | Renombrat `spotifyUrl` → `shareUrl` a Song + ShareableItem (8 fitxers) | ✅ |
| 5.9 | Eliminades ~30 claus traducció Spotify (4 idiomes) | ✅ |
| 5.10 | Comentaris netejats (8 fitxers) | ✅ |
| 5.11 | `spotify_search_` → `yt_search_` als IDs temporals | ✅ |
| 5.12 | Eliminats `@SerializedName` dels models (ja no són models d'API) | ✅ |
| 5.13 | Test `TranslationsTest` actualitzat (clau `search_spotify` eliminada de coreKeys) | ✅ |
| 5.14 | Test `SpotifyModelsTest` — tests `SpotifyAlbum`/`SpotifyArtistFull` eliminats (models ja no existeixen) | ✅ |
| 5.15 | Test `ModelDefaultsTest` — import Song + constructor actualitzat | ✅ |

---

## Què queda de Spotify (intencionadament)

Les 13 referències "spotify" restants al codi font són **legítimes** i no s'han d'eliminar:

| Ubicació | Motiu |
|---|---|
| `PlaylistDatabase.kt:39-40` | SQL de migració `MIGRATION_6_7` (referències als noms antics de columnes — correcte) |
| `UrlParser.kt` (10 línies) | Parsing d'URLs Spotify per NFC/QR (l'usuari pot escanejar un codi Spotify) |
| `ScanResult.kt:1` | Camp `source: String` pot ser `"spotify"` quan es parseja una URL |

---

## Funcionalitats eliminades (sense substitució)

1. **Àlbums** — YouTube no té concepte d'àlbum; els resultats de cerca són vídeos individuals
2. **Artistes seguits** — Eliminat
3. **Sync automàtic** — Les playlists es creen/editen manualment
4. **Liked Songs remot** — Només queda la playlist local `liked_songs`
5. **Recomanacions** — Last.fm + Spotify eliminat; queda Last.fm a `Config` però sense UI activa
6. **Cobertura musical** — YouTube té catàleg diferent (covers, remescles)
7. **Metadades riques** — Sense gèneres, popularitat, data de llançament via Spotify API

---

## Bugs resolts amb la migració

| Bug | Descripció | Com s'ha resolt |
|---|---|---|
| **B3** | `getSpotifyAccessToken()` amb `runBlocking` a main thread (riesgo ANR) | Eliminat amb les funcions Spotify de Config |
| **S1** | Distribució de secrets via Supabase anon key | SupabaseClient mantingut però sense relació amb Spotify |
| **S2** | OAuth amb client_secret al dispositiu | Eliminat |
| **S4** | Tokens a SharedPreferences plano amb backup | Eliminat (ja no hi ha tokens Spotify) |

---

## Per fer (pendent)

- [ ] Activar `isMinifyEnabled = true` + R8 (els models Spotify ja no depenen de Gson)
- [ ] Netjar `SupabaseClient.kt` si ja no s'usa per a res (Last.fm key?)
- [ ] Netjar `AndroidManifest.xml` (intent filter `plyr://spotify` i cleartext Spotify)
- [ ] Provar en dispositiu físic el flux complet

---

*Document generat automàticament després de completar la migració Spotify → YouTube (2026-08-20).*
