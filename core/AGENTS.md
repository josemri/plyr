# :core — KMP compartido

Lee antes `../AGENTS.md` y `../okf/systems/kmp-core.md`.

## Source sets (jerarquía manual)

```text
commonMain      Kotlin estricto: kotlin*, kotlinx*, com.plyr*
  ├── jvmSharedMain   java.* + NewPipe + OkHttp + ZXing (JVM y Android/ART)
  │     ├── jvmMain   (escritorio; añade org.json)
  │     └── androidMain
  └── commonTest      JUnit 4, sin JVM
        ├── jvmTest
        └── androidHostTest
```

`gradle.properties` fija `kotlin.mpp.applyDefaultHierarchyTemplate=false` porque
esta jerarquía no encaja en la plantilla. Las aristas se declaran a mano en
`core/build.gradle.kts`. **No las "simplifiques"** sin comprobar los cuatro
targets.

## Qué va dónde

| Necesitas | Source set |
|---|---|
| Lógica pura, modelos, cola, parsers, i18n | `commonMain` |
| `java.util`, `java.time`, `java.security`, `org.json` | `jvmSharedMain` / `jvmMain` |
| NewPipe, OkHttp, ZXing | `jvmSharedMain` |
| `android.*` | `androidMain` de `:core` (evítalo) o `:app` |

Regla práctica: si dudas, `commonMain`. Si el compilador se queja de una API JVM,
baja a `jvmSharedMain`, nunca metas Android.

## Reglas específicas

- **No añadas dependencias de UI** (Compose, Android) a `:core`.
- **No añadas `Context`, `Uri`, `Bitmap`** a `:core`. Las plataformas aportan el
  transporte; `:core` aporta la lógica (ver `QrCodec`, `ExportManifest`).
- **La lógica pura se testea aquí**, no en `:app`. Si un test de `:app` no usa
  Android, muévelo.
- **Contratos persistidos** (`ExportManifest`, `ImportManifest`, `TrackIdentity`,
  `Tombstones`, `QrCodec`, `UrlParser`) son API pública de facto entre
  plataformas: cambia con cuidado y con test de ida y vuelta.
- **detekt**: `:core:detektMainJvm` con baseline. No lo amplíes.

## Tests

```bash
./run.sh verify unit   # :core:jvmTest + :core:testAndroidHostTest
```

- `commonTest`: cero dependencias de JVM; es el sitio preferido.
- `jvmTest`: usa `java.*`, red (con `assumeTrue`) o `org.json`.
- `androidHostTest`: valida que `commonMain` también compila para Android.
- Todo `jvmTest` debe ser determinista y offline salvo los `*SmokeTest`, que se
  omiten sin conexión.
