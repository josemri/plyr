---
type: Domain Concept
title: "QR, NFC y share"
description: "Generación/decodificación de QR compartida y política única de URLs. El id real de YouTube manda; una lista de Spotify no se comparte como vídeo."
resource: repo://core/src/jvmSharedMain/kotlin/com/plyr/utils/QrCodec.kt
tags: [qr, nfc, share, zxing, urls]
module: core
generated: { by: human:josep, at: 2026-11-05T00:00:00Z }
status: stable
stale_after: 2027-05-05
tests: [core/src/jvmTest/kotlin/com/plyr/utils/QrCodecTest.kt, core/src/commonTest/kotlin/com/plyr/ui/components/ShareUrlPolicyTest.kt, core/src/commonTest/kotlin/com/plyr/ui/components/PlaylistShareTest.kt]
---

# QR

`QrCodec` concentra ZXing y la conversión a píxeles:

- `generate(content, size = 512)` → `QrMatrix` o `null` si está vacío/falla.
- `toArgb(matrix)` → píxeles en orden de filas.
- `decode(pixels, width, height)` → texto o `null`.

Cada plataforma solo aporta el transporte: `Bitmap` en Android, `ImageIO`/Skia
en escritorio. Se prueba con round-trip generar → píxeles → decodificar, sin
cámara.

# Política de URL

`ShareUrlPolicy.resolve` decide qué se comparte y es **la única fuente de
verdad**:

| Tipo | Regla |
|---|---|
| `APP` | URL de descarga; si hay una montada, se usa. |
| `TRACK` | Gana el `youtubeVideoId`. Una `shareUrl` solo entra si no hay id. |
| `PLAYLIST` | Usa `playlistOrigin`: `SPOTIFY` → URL de Spotify; `YOUTUBE` → lista de YouTube. |

Errores históricos: B52 (se compartía
`youtube.com/watch?v=spotify_<hash>...`) y B53 (una lista de Spotify se
compartía como vídeo). Por eso `ShareableItem.playlistOrigin` es obligatorio en
la práctica para listas.

# ScanEvent

La comunicación entre el escaneo de QR y las pantallas se hace mediante
`ScanEvent` (en `app/src/main/java/com/plyr/utils/ScanEvent.kt`), que expone
`onScanned(result)`, `consumeResult()` y `clear()`. Un resultado de escaneo
(`ScanResult`) se parsea con `UrlParser.parseScanText`.

# Plataformas

- Android: `ui/components/QRDialog.kt` (generar), `QrScannerDialog.kt` (leer).
- Escritorio: `qr/QrImages.kt`, `qr/QrShareDialog.kt`,
  `qr/RecommendationSender.kt`.

# Al cambiar

- No construyas URLs en la pantalla: pasa datos a `ShareUrlPolicy`.
- No deduzcas el origen por prefijos del id ni por la descripción.
- Mantén el payload compatible entre Android y escritorio.
- Tests: `QrCodecTest`, `ShareUrlPolicyTest`, `PlaylistShareTest`.
