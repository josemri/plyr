import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "com.plyr.core"
        compileSdk = 36
        minSdk = 24
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

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        // NewPipe/OkHttp son librerías JVM: las usan tanto el escritorio como
        // Android, así que viven en el source set compartido.
        jvmSharedMain.dependencies {
            implementation(libs.newpipe.extractor)
            implementation(libs.okhttp)
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
