import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.voxly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voxly"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.1"

        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("en", "zh-rCN")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Load signing configuration from local.properties or environment variables
    val localProperties = rootProject.file("local.properties")
    val signingEnabled = if (localProperties.exists()) {
        Properties().apply { load(localProperties.inputStream()) }
            .getProperty("RELEASE_STORE_PASSWORD") != null
    } else {
        System.getenv("SIGNING_STORE_PASSWORD") != null
    }

    signingConfigs {
        create("release") {
            val storePass = if (localProperties.exists()) {
                Properties().apply { load(localProperties.inputStream()) }
                    .getProperty("RELEASE_STORE_PASSWORD")
            } else null

            storeFile = file("voxly-release.keystore")
            storePassword = storePass ?: System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = "voxly"
            keyPassword = storePass ?: System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        create("dist") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dist"
            // AGP limitation: debuggable=true disables R8 optimization/obfuscation.
            // Keep this variant slim for distribution testing.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-debug.pro"
            )
            matchingFallbacks += listOf("debug")
            if (signingEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    //noinspection WrongGradleMethod
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.59.1")
    ksp("com.google.dagger:hilt-android-compiler:2.59.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Audio Processing - Kyant0/taglib from Maven Central (supports Android SAF)
    // Replaces KTagLib which had JitPack reliability issues
    implementation("io.github.kyant0:taglib:1.0.5")

    // Networking for MusicBrainz API
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.13.2")

    // Room Database for caching
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("app.cash.turbine:turbine:1.2.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Keep preview tooling out of runtime APK.
    debugCompileOnly("androidx.compose.ui:ui-tooling")
}
