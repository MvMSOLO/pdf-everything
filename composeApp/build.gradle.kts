import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
                
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                
                implementation(compose.materialIconsExtended)
                
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                
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
                implementation("com.tom-roush:pdfbox-android:2.0.27.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.apache.pdfbox:pdfbox:${libs.versions.pdfbox.get()}")
            }
        }
    }
}

android {
    namespace = "com.example.pdf_everything"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pdf_everything"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "pdf-everything"
            packageVersion = "1.0.1"
            description = "Document-first PDF editor and viewer"
            vendor = "PDF Everything"
            fileAssociation(
                mimeType = "application/pdf",
                extension = "pdf",
                description = "Portable Document Format"
            )
        }
    }
}

dependencies {
        }
