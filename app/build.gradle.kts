plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "ru.aensidhe.dreamclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.aensidhe.dreamclock"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(21)
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.datastore)
    implementation(libs.protobuf.javalite)
}
