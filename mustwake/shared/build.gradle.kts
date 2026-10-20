import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    android {
        minSdk = 26
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        namespace = "com.mustwake.alarm.shared"
        androidResources.enable = true
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.nav)
            implementation(libs.backdrop)
            implementation(libs.kyant.shapes)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(project(":data"))
        }
    }
}
