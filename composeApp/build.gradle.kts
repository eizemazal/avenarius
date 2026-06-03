import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
}

// ktlint: style/format checks. `./gradlew ktlintCheck` to verify, `ktlintFormat`
// to auto-fix. Rules are tuned in the repo-root .editorconfig.
ktlint {
    version.set(libs.versions.ktlintTool.get())
    // Generated sources (e.g. Compose resources' Res.kt) are excluded via the
    // repo-root .editorconfig (`[**/build/**] ktlint = disabled`).
}

kotlin {
    // ----- Android target (produces the APK) -----
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // ----- Desktop target (JVM) — reuses everything in commonMain -----
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        // commonMain = the code shared by Android AND desktop:
        // the Max protocol client, data models, repository, ViewModel and Compose UI.
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp) // Ktor engine for Android
            implementation(libs.androidx.media3.exoplayer) // in-app video playback
            implementation(libs.androidx.media3.ui)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio) // Ktor engine for desktop JVM
        }
    }
}

android {
    namespace = "com.avenarius.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.avenarius.app"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        // Version is overridable from CI (tag-driven release): AVENARIUS_VERSION_NAME
        // comes from the git tag, AVENARIUS_VERSION_CODE from the CI run number.
        versionCode = System.getenv("AVENARIUS_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("AVENARIUS_VERSION_NAME") ?: "1.0.0"
    }

    // Signing: in CI we inject a keystore via env vars (see .github/workflows).
    // Locally, with no keystore configured, the release APK is left unsigned
    // and the debug APK (auto-signed with the debug key) is what you install.
    val storeFilePath = System.getenv("AVENARIUS_KEYSTORE")
    if (storeFilePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("AVENARIUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AVENARIUS_KEY_ALIAS")
                keyPassword = System.getenv("AVENARIUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 code shrinking + obfuscation. Library consumer rules (Ktor, Coil,
            // kotlinx-serialization, coroutines) are applied automatically; our extra
            // keeps live in proguard-rules.pro.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Generate the resource accessors (Res.drawable.*) under a stable, explicit
// package so UI code imports `com.avenarius.app.resources.Res` predictably.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.avenarius.app.resources"
}

compose.desktop {
    application {
        mainClass = "com.avenarius.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Avenarius"
            packageVersion = "1.0.0"
        }
    }
}
