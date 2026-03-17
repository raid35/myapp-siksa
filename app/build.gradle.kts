plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.siksa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.siksa"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "Siksa_2026_V3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources {
            localeFilters += listOf("ar", "en")
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        compose = true
    }
}
    dependencies {
        val media3_version = "1.9.2"

        // --- المحرك الأساسي وواجهة المستخدم ---
        implementation("androidx.media3:media3-exoplayer:$media3_version")
        implementation("androidx.media3:media3-ui:$media3_version")
        implementation("androidx.media3:media3-common:${media3_version}")
        implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
        implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
        implementation("androidx.media3:media3-exoplayer-rtsp:$media3_version")
        implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3_version")
        implementation("androidx.media3:media3-datasource-okhttp:$media3_version")
        implementation("androidx.media3:media3-datasource-rtmp:$media3_version")
        implementation("androidx.media3:media3-datasource-cronet:$media3_version")
        implementation("androidx.media3:media3-session:$media3_version")



    // Core & UI (استدعاء من libs لأنها تعمل لديك)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation("androidx.browser:browser:1.8.0")

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // UI Extensions & TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.constraintlayout.compose)

    // الشبكات
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("io.github.pdvrieze.xmlutil:core-android:1.0.0-rc2")
    implementation("io.github.pdvrieze.xmlutil:serialization:1.0.0-rc2")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
