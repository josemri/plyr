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

        // Código que usa APIs de la JVM (java.*) presentes tanto en el JVM de
        // escritorio como en Android/ART. No puede vivir en commonMain (que es
        // multiplataforma estricto) pero sí compartirse entre ambos targets.
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain)
        }

        getByName("jvmMain").dependsOn(jvmSharedMain)
        getByName("androidMain").dependsOn(jvmSharedMain)

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("commonTest").dependencies {
            implementation(libs.junit)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.json)
        }
    }
}
