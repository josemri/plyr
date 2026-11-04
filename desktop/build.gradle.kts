import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
}

/**
 * Versión del paquete de escritorio (M34): la misma que Android. Se lee
 * `versionName` de `app/build.gradle.kts` —la única fuente de verdad, la que
 * incrementa el workflow de release— para que los instalables y el comprobador
 * de actualizaciones no se desincronicen de la app Android.
 */
val appVersionName: String = Regex("""versionName\s*=\s*"([^"]+)"""")
    .find(rootProject.file("app/build.gradle.kts").readText())
    ?.groupValues?.get(1)
    ?: error("No se pudo leer versionName de app/build.gradle.kts")

/**
 * Fuente generado con la versión para el código (M34). `UpdateChecker` lo usa
 * para comparar con la última release sin duplicar el número a mano.
 */
val generateVersionSource = tasks.register("generateVersionSource") {
    val version = appVersionName
    val outputDir = layout.buildDirectory.dir("generated/plyr-version/kotlin")
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/plyr/desktop/Version.kt")
        file.parentFile.mkdirs()
        file.writeText(
            "// Generado por Gradle desde versionName de :app (M34). No editar.\n" +
                "package com.plyr.desktop\n\n" +
                "/** Versión del paquete de escritorio, sincronizada con Android. */\n" +
                "const val PLYR_VERSION: String = \"$version\"\n",
        )
    }
}

kotlin.sourceSets["main"].kotlin.srcDir(generateVersionSource)

// Análisis de código muerto/estructura (M15): misma config que `:app`.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
    // `:desktop` hereda bastante composable/controlador ya portado: el baseline
    // acepta como deuda el análisis estructural actual (LongMethod/
    // CyclomaticComplexMethod/TooManyFunctions) y evita que vuelva a colarse.
    // El código nuevo (M15 en adelante) no está en el baseline: se analiza.
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

// SQLDelight escribe Kotlin generado en `build/generated`: se analiza el código
// del proyecto, no el generado (el filtro es por ruta absoluta porque cada
// carpeta generada es una raíz de fuentes).
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    // Motor de audio de escritorio (M4). Requiere `libvlc` instalado en el sistema.
    implementation(libs.vlcj)
    // Persistencia de preferencias de escritorio (M9): misma familia de claves
    // que Android, con `java.util.prefs` como backend.
    implementation(libs.multiplatform.settings)
    // Descarga de portadas para la caché de disco (M9, usada por la copia M10).
    implementation(libs.okhttp)
    // Lectura del JSON de GitHub releases en el comprobador de actualizaciones
    // (M15). En Android lo aporta el SDK; aquí hay que declararlo.
    implementation(libs.json)
    // Persistencia de escritorio (M6): SQLDelight sustituye a Room.
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.junit)
    // `MapSettings` para aislar las preferencias en los tests (M9).
    testImplementation(libs.multiplatform.settings.test)
    // Test de UI de Compose (runComposeUiTest) para la navegación de M5.
    testImplementation(libs.compose.ui.test.junit4)
    // Coil 3 para carga de portadas (M8)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    // Integración nativa de Linux (M13): MPRIS sobre D-Bus para las teclas
    // multimedia y los metadatos que ven los clientes (playerctl, GNOME...).
    implementation(libs.dbus.java.core)
    runtimeOnly(libs.dbus.java.native.unixsocket)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
}

/**
 * Base de datos de escritorio (M6). SQLDelight genera `PlyrDatabase` a partir de
 * los esquemas `.sq` de `src/main/sqldelight`, equivalentes a las entidades Room
 * de Android (`playlists`, `tracks`, `search_history`, versión 8).
 */
sqldelight {
    databases {
        create("PlyrDatabase") {
            packageName.set("com.plyr.desktop.database")
        }
    }
}

/**
 * Prueba de sonido por línea de comandos (M4).
 *
 * `./gradlew :desktop:soundCheck --args="artista canción"` busca la canción en
 * YouTube, extrae la URL de audio y la reproduce por VLC. Es la verificación
 * "headless" del motor, complementaria a la ventana de `:desktop:run`.
 *
 * Para probar con un VLC no estándar (p. ej. extraído en `/tmp`), pásalo con
 * `-PvlcDir=/ruta/a/libvlc` o `PLYR_VLC_DIR`: se propaga a todas las tareas que
 * arrancan un JVM (`run`, `soundCheck`, `test`) como `PLYR_VLC_DIR` y
 * `LD_LIBRARY_PATH`.
 */
val vlcDir: String? = (findProperty("vlcDir") as String?) ?: System.getenv("PLYR_VLC_DIR")

/** Variables de entorno para usar un VLC fuera de las rutas del sistema. */
fun vlcEnvironment(): Map<String, String> {
    if (vlcDir == null) return emptyMap()
    return buildMap {
        put("PLYR_VLC_DIR", vlcDir)
        put(
            "LD_LIBRARY_PATH",
            listOf(vlcDir, "$vlcDir/vlc", System.getenv("LD_LIBRARY_PATH")).filterNotNull().joinToString(":"),
        )
        // `libvlc` ya no admite `--plugin-path` (VLC ≥ 3), así que la única
        // forma de apuntarle unos plugins fuera de su ruta compilada es esta
        // variable de entorno. Sin ella, `-PvlcDir` localizaría la biblioteca
        // pero no tendría módulos con los que reproducir.
        File(vlcDir, "vlc/plugins").takeIf { it.isDirectory }?.let {
            put("VLC_PLUGIN_PATH", it.absolutePath)
        }
    }
}

/** Propaga el VLC no estándar al JVM que arranque la tarea. */
fun JavaExec.applyVlcEnv() {
    vlcEnvironment().forEach { (key, value) -> environment(key, value) }
}

// `run` (Compose), `soundCheck` (CLI) y los tests comparten el mismo entorno.
tasks.withType<JavaExec>().configureEach { applyVlcEnv() }

tasks.withType<Test>().configureEach {
    // Aísla las preferencias reales del usuario (M9): el backend por defecto de
    // `DesktopConfig` usa `java.util.prefs`, así que los tests escriben en una
    // raíz temporal en lugar de `~/.java/.userPrefs`.
    systemProperty(
        "java.util.prefs.userRoot",
        layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath,
    )
    if (vlcDir != null) {
        vlcEnvironment().forEach { (key, value) -> environment(key, value) }
    }
}

tasks.register<JavaExec>("soundCheck") {
    group = "application"
    description = "Reproduce una canción de YouTube con VLCJ (prueba de sonido de M4)."
    mainClass.set("com.plyr.desktop.SoundCheckKt")
    classpath = sourceSets["main"].runtimeClasspath
}

/**
 * Firma y notarización de macOS (M35). Se leen del entorno porque en CI vienen
 * de secretos; en local no están y el `.dmg` se empaqueta igual. `MACOS_SIGN_*`
 * activan la firma (Developer ID Application); `APPLE_*` la notarización.
 */
val macIdentity: String? = providers.environmentVariable("MACOS_SIGN_IDENTITY").orNull
val macKeychain: String? = providers.environmentVariable("MACOS_SIGN_KEYCHAIN").orNull
val appleId: String? = providers.environmentVariable("APPLE_ID").orNull
val applePassword: String? = providers.environmentVariable("APPLE_APP_PASSWORD").orNull
val appleTeamId: String? = providers.environmentVariable("APPLE_TEAM_ID").orNull

compose.desktop {
    application {
        mainClass = "com.plyr.desktop.MainKt"
        nativeDistributions {
            // Instalables por SO (M14). jpackage solo puede generar los del SO
            // anfitrión, por eso se declaran todos: Linux -> .deb/.rpm/AppImage,
            // Windows -> .msi y macOS -> .dmg. Cada plataforma construye los suyos
            // con `packageDistributionForCurrentOS`.
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Rpm,
                TargetFormat.AppImage,
                TargetFormat.Msi,
                TargetFormat.Dmg,
            )
            packageName = "plyr"
            // Misma versión que Android (M34), leída de `app/build.gradle.kts`.
            packageVersion = appVersionName
            description = "Reproductor de música minimalista (YouTube + listas locales)."
            vendor = "josemri"
            copyright = "© 2026 josemri — GPL-3.0-or-later"
            // VLCJ (JNA), dbus-java (JNA + unixsocket) y SQLite-JDBC cargan clases
            // por reflexión y JNI, así que jlink no puede deducirlas: se incluyen
            // todos los módulos del JDK para que la app arranque sin Java instalado.
            includeAllModules = true
            // Icono de la app (M36): el mismo `ic_launcher` de Android en las tres
            // plataformas. jpackage exige un formato distinto por SO —PNG en Linux,
            // `.icns` en macOS y `.ico` en Windows— y los tres se regeneran desde
            // `app/src/main/ic_launcher-playstore.png` con `packaging/make-icons.sh`.
            linux {
                iconFile.set(project.file("packaging/icon.png"))
                appCategory = "Audio"
                menuGroup = "Audio"
                debMaintainer = "josemri@users.noreply.github.com"
                rpmLicenseType = "GPL-3.0-or-later"
            }
            macOS {
                // Icono del `.app` y del `.dmg` (M36).
                iconFile.set(project.file("packaging/icon.icns"))
                // Identificador inverso-DNS del bundle (M35): es el
                // `CFBundleIdentifier` del `.app` y lo exige la firma.
                bundleID = "io.github.josemri.plyr"
                // Firma y notarización opcionales (M35): solo se activan si el
                // entorno trae los datos (en CI, desde secretos). En local no se
                // firma y el `.dmg` se genera igual (firma ad-hoc en Apple Silicon).
                //
                // Ojo con la comprobación: en CI un secreto ausente llega como
                // variable definida pero **vacía**, y vacío no es nula. Con
                // `!= null` se activaba la firma con identidad `""` y jpackage
                // revienta con `Could not find certificate for ''`; por eso aquí
                // se usa `isNullOrBlank`.
                if (!macIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(macIdentity)
                        macKeychain?.takeIf { it.isNotBlank() }?.let { keychain.set(it) }
                    }
                }
                if (!appleId.isNullOrBlank() &&
                    !applePassword.isNullOrBlank() &&
                    !appleTeamId.isNullOrBlank()
                ) {
                    notarization {
                        appleID.set(appleId)
                        password.set(applePassword)
                        teamID.set(appleTeamId)
                    }
                }
            }
            windows {
                // Icono del `.msi` y del acceso directo (M36).
                iconFile.set(project.file("packaging/icon.ico"))
                // Sin estas tres opciones jpackage instala sin preguntar nada
                // y sin dejar huella: ni acceso directo de escritorio, ni
                // entrada en el menú de inicio, ni selector de carpeta.
                // El diálogo con dos casillas para elegir accesos directos
                // (`--win-shortcut-prompt`) se añade más abajo, porque la DSL
                // no lo expone.
                menu = true
                shortcut = true
                dirChooser = true
            }
        }
    }
}

/**
 * Añade al `.deb` lo que jpackage no genera (M14): la dependencia de `libvlc`
 * (VLCJ la necesita en tiempo de ejecución) y la ficha AppStream. Se ejecuta
 * después de `packageDeb`, de forma que `./gradlew :desktop:packageDeb` (o
 * `packageDistributionForCurrentOS`) deja el instalable final.
 */
val patchDeb = tasks.register<Exec>("patchDeb") {
    group = "distribution"
    description = "Inyecta la ficha AppStream y la dependencia de libvlc en el .deb generado."
    dependsOn("packageDeb")
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
    val debDir = layout.buildDirectory.dir("compose/binaries/main/deb")
    val script = project.file("packaging/patch-deb.sh")
    val metainfo = project.file("packaging/io.github.josemri.plyr.metainfo.xml")
    doFirst {
        val deb = debDir.get().asFile.listFiles { file -> file.extension == "deb" }
            ?.maxByOrNull { it.lastModified() }
            ?: error("No se encontró ningún .deb en ${debDir.get().asFile}")
        commandLine("bash", script.absolutePath, deb.absolutePath, metainfo.absolutePath)
    }
}

// `packageDeb` lo registra el plugin de Compose de forma perezosa, así que se
// engancha el parche con `matching` en vez de `named`.
tasks.matching { it.name == "packageDeb" }.configureEach { finalizedBy(patchDeb) }

/**
 * Pregunta por los accesos directos en el instalador de Windows (M37).
 *
 * `--win-shortcut-prompt` (JDK 17+) añade a la secuencia una pantalla con dos
 * casillas —«Crear acceso directo en el escritorio» y «Crear acceso directo en
 * el menú de inicio»— y es el usuario quien decide. Sin ella, `--win-shortcut`
 * y `--win-menu` (las opciones `shortcut`/`menu` de `nativeDistributions`)
 * crean los accesos directos sin preguntar.
 *
 * La DSL de Compose 1.12.x no expone esta opción ni admite argumentos sueltos
 * de jpackage; `freeArgs` es la única vía y está sin usar en todo el plugin.
 * Solo se añade a los formatos de Windows, porque en el resto `--win-*` haría
 * fallar a jpackage.
 */
tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Msi || targetFormat == TargetFormat.Exe) {
        freeArgs.add("--win-shortcut-prompt")
    }
}

/** ¿Está el ejecutable `name` en el PATH? */
fun toolOnPath(name: String): Boolean =
    System.getenv("PATH")?.split(File.pathSeparator)?.any { File(it, name).canExecute() } == true

// `packageDistributionForCurrentOS` construye todos los formatos compatibles
// con el SO anfitrión. En Linux incluye `.rpm`, que jpackage solo reconoce si
// `rpmbuild` (paquete `rpm-build`) está en el PATH; sin él la tarea falla. Se
// desactiva en ese caso para que el comando genérico funcione igualmente.
tasks.matching { it.name == "packageRpm" }.configureEach {
    onlyIf { toolOnPath("rpmbuild") }
}
