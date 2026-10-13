import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    // Motor de audio de escritorio (M4). Requiere `libvlc` instalado en el sistema.
    implementation(libs.vlcj)
    // Persistencia de preferencias de escritorio (M9): misma familia de claves
    // que Android, con `java.util.prefs` como backend.
    implementation(libs.multiplatform.settings)
    // Descarga de portadas para la caché de disco (M9, usada por la copia M10).
    implementation(libs.okhttp)
    // Persistencia de escritorio (M6): SQLDelight sustituye a Room.
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.junit)
    // `MapSettings` para aislar las preferencias en los tests (M9).
    testImplementation(libs.multiplatform.settings.test)
    // Test de UI de Compose (runComposeUiTest) para la navegación de M5.
    testImplementation(compose.desktop.uiTestJUnit4)
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

/** Propaga el VLC no estándar al JVM que arranque la tarea. */
fun JavaExec.applyVlcEnv() {
    if (vlcDir == null) return
    environment("PLYR_VLC_DIR", vlcDir)
    environment(
        "LD_LIBRARY_PATH",
        listOf(vlcDir, "$vlcDir/vlc", System.getenv("LD_LIBRARY_PATH")).filterNotNull().joinToString(":"),
    )
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
        environment("PLYR_VLC_DIR", vlcDir)
        environment(
            "LD_LIBRARY_PATH",
            listOf(vlcDir, "$vlcDir/vlc", System.getenv("LD_LIBRARY_PATH")).filterNotNull().joinToString(":"),
        )
    }
}

tasks.register<JavaExec>("soundCheck") {
    group = "application"
    description = "Reproduce una canción de YouTube con VLCJ (prueba de sonido de M4)."
    mainClass.set("com.plyr.desktop.SoundCheckKt")
    classpath = sourceSets["main"].runtimeClasspath
}

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
            packageVersion = "1.3.0"
            description = "Reproductor de música minimalista (YouTube + listas locales)."
            vendor = "josemri"
            copyright = "© 2026 josemri — GPL-3.0-or-later"
            // VLCJ (JNA), dbus-java (JNA + unixsocket) y SQLite-JDBC cargan clases
            // por reflexión y JNI, así que jlink no puede deducirlas: se incluyen
            // todos los módulos del JDK para que la app arranque sin Java instalado.
            includeAllModules = true
            linux {
                iconFile.set(project.file("packaging/icon.png"))
                appCategory = "Audio"
                menuGroup = "Audio"
                debMaintainer = "josemri@users.noreply.github.com"
                rpmLicenseType = "GPL-3.0-or-later"
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
val patchDeb by tasks.registering(Exec::class) {
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
