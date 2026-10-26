import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.detekt)
}

// Análisis de código muerto/estructura (M15): misma config que `:app`.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("detekt.yml"))
    // `:core` es código ya portado desde Android: el baseline fija el estado
    // estructural actual y deja que el código nuevo se analice de verdad.
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude { it.file.invariantSeparatorsPath.contains("/generated/") }
    baseline.set(layout.projectDirectory.file("detekt-baseline.xml"))
}

kotlin {
    android {
        namespace = "com.plyr.core"
        compileSdk = 37
        minSdk = 24
        // Tests de host (JVM) del target Android: comparten commonTest con
        // :core:jvmTest, pero validan también la compilación Android.
        withHostTest {}
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        val jvmMain = getByName("jvmMain")
        val jvmTest = getByName("jvmTest")

        // Código que usa APIs de la JVM (java.*) presentes tanto en el JVM de
        // escritorio como en Android/ART. No puede vivir en commonMain (que es
        // multiplataforma estricto) pero sí compartirse entre ambos targets.
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain)
        }

        jvmMain.dependsOn(jvmSharedMain)
        getByName("androidMain").dependsOn(jvmSharedMain)

        // El proyecto usa una jerarquía explícita (jvmSharedMain no encaja en
        // la plantilla por defecto JVM+Android), así que las aristas de test
        // también se declaran a mano (ver gradle.properties).
        jvmTest.dependsOn(commonTest)
        getByName("androidHostTest").dependsOn(commonTest)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        // NewPipe/OkHttp son librerías JVM: las usan tanto el escritorio como
        // Android, así que viven en el source set compartido.
        jvmSharedMain.dependencies {
            implementation(libs.newpipe.extractor)
            implementation(libs.okhttp)
            // ZXing: genera/decodifica QR desde código compartido (M11). En
            // Android ya lo aportaba `:app`; aquí queda también para escritorio.
            implementation(libs.core)
        }
        commonTest.dependencies {
            implementation(libs.junit)
        }
        // org.json: en Android lo aporta el propio SDK, pero el JVM de escritorio
        // necesita el artefacto. Declararlo aquí deja el AAR de Android sin
        // duplicar org.json (el compilador lo resuelve de android.jar).
        jvmMain.dependencies {
            implementation(libs.json)
        }
        jvmTest.dependencies {
            implementation(libs.json)
        }
    }
}
