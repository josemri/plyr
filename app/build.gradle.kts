import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseKeystore) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// S9/S10: valores de red que se pueden sobreescribir por entorno
// (local.properties o variables de entorno) sin tocar el código. La anon key de
// Supabase es "publishable" por diseño y la cookie de modo restringido de YouTube
// lleva un valor funcional por defecto; el override sirve para apuntar a otro
// proyecto o rotar la cookie sin recompilar el fuente.
val networkProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun configValue(name: String, default: String): String =
    networkProperties.getProperty(name) ?: System.getenv(name) ?: default

android {
    namespace = "com.plyr"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.josemri.plyr"
        minSdk = 24
        // compileSdk 37 lo exigen las librerías actuales (core-ktx 1.19+,
        // Compose 1.12). El salto de targetSdk a 37 cambia comportamiento en
        // runtime (audio en segundo plano, NFC...) y necesita pruebas en
        // dispositivo, así que se hará en un hito aparte.
        targetSdk = 36
        versionCode = 13
        versionName = "1.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${configValue("SUPABASE_URL", "https://mpfioblwpghlkulsryzu.supabase.co")}\""
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${configValue("SUPABASE_ANON_KEY", "sb_publishable_CBiixr2FXsfTXwMhRFB5Qg_c5T7ePQ8")}\""
        )
        buildConfigField(
            "String",
            "YOUTUBE_RESTRICTED_MODE_COOKIE",
            "\"${configValue("YOUTUBE_RESTRICTED_MODE_COOKIE", "PREF=f2=8000000")}\""
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // B41: R8 activo; antes los proguardFiles eran inertes y los
            // Log.d/e con datos sensibles (B32/B33) llegaban al APK publicado.
            isMinifyEnabled = true
            // Los `ascii_*` que se resuelven por nombre están protegidos en
            // res/raw/keep.xml, así que se puede activar el shrink completo.
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Los iconos nativos de Android se mantienen intactos a propósito: el
        // redondeo de esta tanda solo afecta a los ejecutables de escritorio.
        // Añadir <monochrome> cambiaría el icono, así que se silencia solo
        // este aviso (no se desactiva ninguna otra comprobación).
        disable += "MonochromeLauncherIcon"
        // Ver la nota de targetSdk: es una decisión consciente, no un olvido.
        disable += "OldTargetApi"
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
}

// Room genera Kotlin con KSP en `build/generated`: se analiza el código del
// proyecto, no el generado (mismo filtro que en `:core` y `:desktop`).
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
}

// AGP 9.4 expone `VariantOutput.outputFileName` en la API pública; la API
// antigua (`applicationVariants`/`BaseVariantOutputImpl`) ya está obsoleta.
androidComponents {
    onVariants { variant ->
        val fileName = when (variant.buildType) {
            "release" -> "plyr.apk"
            "debug" -> "plyr-debug.apk"
            else -> "plyr-${variant.buildType}.apk"
        }
        variant.outputs.forEach { output -> output.outputFileName.set(fileName) }
    }
}

dependencies {

    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.runtime.livedata)

    implementation(libs.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.ui.text)

    testImplementation(libs.junit)
    // org.json real en los unit tests: el del android.jar mockable lanza "not mocked"
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // zxing explícito: los tests instrumentados decodifican el QR generado.
    androidTestImplementation(libs.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.okhttp)

    // NewPipe Extractor
    implementation(libs.newpipe.extractor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil for image loading
    implementation(libs.coil.compose)
}
