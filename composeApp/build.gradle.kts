import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalAnimationApi")
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    jvm("desktop")
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(compose.animation)
                
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                
                implementation(compose.materialIconsExtended)
                
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.compose.adaptive)
                implementation(libs.androidx.compose.adaptive.layout)
                implementation(libs.androidx.compose.adaptive.navigation3)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                
                implementation(libs.okhttp)
                implementation(libs.logging.interceptor)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)
                
                implementation(libs.play.services.location)
                implementation(libs.accompanist.permissions)
                
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.example.pdf_everything"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.pdf_everything"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "pdf-everything"
            packageVersion = "1.0.0"

            // ── Windows file association (spec §35) ────────────────────
            windows {
                menuGroup = "PDF Everything"
                // Register .pdf file association in the installer
                dirChooser = true
                perUserInstall = true
                // The upgradeUuid must be unique and stable for in-place upgrades
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }

            // ── macOS Info.plist entries ─────────────────────────────
            macOS {
                // File type associations are declared in Info.plist
                // Compose Desktop doesn't expose a DSL for this yet;
                // the app bundle will need a post-build script.
                bundleID = "com.example.pdf-everything"
            }

            // ── Linux .desktop entry ─────────────────────────────────
            linux {
                // The .desktop file MimeType is set by registerFileAssociation()
                // at runtime (see PlatformService.desktop actual).
                menuGroup = "Office"
            }
        }
    }
}

dependencies {
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
