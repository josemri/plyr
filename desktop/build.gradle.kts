import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
    testImplementation(libs.junit)
    // Test de UI de Compose (runComposeUiTest) para la navegación de M5.
    testImplementation(compose.desktop.uiTestJUnit4)
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "plyr"
            packageVersion = "1.0.0"
        }
    }
}
