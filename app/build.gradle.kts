import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.GradleException
import java.util.Properties

private val localPropertiesFile = rootProject.file("local.properties")
private val signingProps = Properties().apply {
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}
private val signingStorePassword: String? = signingProps.getProperty("RELEASE_STORE_PASSWORD")
    ?: System.getenv("SIGNING_STORE_PASSWORD")
private val signingKeyPassword: String? = signingProps.getProperty("RELEASE_KEY_PASSWORD")
    ?: System.getenv("SIGNING_KEY_PASSWORD")
    ?: signingStorePassword
private val signingKeyAlias: String = signingProps.getProperty("RELEASE_KEY_ALIAS")
    ?: System.getenv("SIGNING_KEY_ALIAS")
    ?: "voxly"
private val signingEnabled: Boolean =
    !signingStorePassword.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()
private val isCiBuild: Boolean = System.getenv("CI").equals("true", ignoreCase = true)
private val debugUseReleaseSigning: Boolean = isCiBuild && signingEnabled

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            // Compose compiler metrics (uncomment to generate stability reports)
            // "-P",
            // "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${layout.buildDirectory.asFile.get().resolve("compose-metrics").absolutePath}",
            // "-P",
            // "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${layout.buildDirectory.asFile.get().resolve("compose-metrics").absolutePath}"
        )
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability-config.txt"))
}

android {
    namespace = "com.voxly"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.voxly"
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 52
        versionName = "1.7.4"

        @Suppress("DEPRECATION")
        resourceConfigurations += listOf("en", "zh-rCN")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_static")
            }
        }

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "channel"
    productFlavors {
        create("github") {
            isDefault = true
            dimension = "channel"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("voxly-release.keystore").takeIf { it.exists() }
                ?: file("voxly-release.keystore")
            storePassword = signingStorePassword ?: ""
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            if (debugUseReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
            val buildAbi = project.findProperty("buildAbi")?.toString()
            if (buildAbi != null) {
                //noinspection WrongGradleMethod
                include(*buildAbi.split(",").map { it.trim() }.toTypedArray())
            } else {
                include("arm64-v8a")
            }
            isUniversalApk = false
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = true
    }

    // Unit tests: allow Android framework methods to return default values
    // instead of throwing "not mocked". This is required by the regression
    // test in `CachedAudioFileIdentityTest` (lesson.md #25) which exercises
    // `AudioFile` in a plain JVM test — `AudioFile`'s companion initializer
    // calls `android.net.Uri.parse`, which would otherwise blow up the
    // test classloader. Default values (null / 0 / false) are acceptable
    // because the test never dereferences the parsed Uri.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    ndkVersion = "29.0.14206865"
}

// Fail fast: never produce unsigned dist/release APKs by mistake.
androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        val buildTypeName = variantBuilder.buildType
        if ((buildTypeName == "release" || buildTypeName == "dist") && !signingEnabled) {
            throw GradleException(
                "Signing is required for $buildTypeName builds. " +
                    "Set RELEASE_STORE_PASSWORD/RELEASE_KEY_PASSWORD in local.properties " +
                    "or SIGNING_STORE_PASSWORD/SIGNING_KEY_PASSWORD in environment."
            )
        }
        if (buildTypeName == "debug" && isCiBuild && signingEnabled && !debugUseReleaseSigning) {
            logger.warn(
                "CI debug build with signing enabled but signing not configured. " +
                    "Building without signing."
            )
        }
    }
    onVariants(selector().all()) { variant ->
        val versionName = project.extensions
            .getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
            .defaultConfig
            .versionName ?: ""
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            if (abi != null) {
                output.outputFileName = "Voxly-${versionName}-${abi}.apk"
            }
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose BOM (用于其他 Compose 依赖)
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    // 使用 Kotlin 2.3.10 配合 Compose alpha 版本
    implementation("androidx.compose.animation:animation:1.11.3")
    implementation("androidx.compose.animation:animation-core:1.11.3")
    implementation("androidx.compose.animation:animation-graphics:1.11.3")
    // Material3 Alpha 版本 - 覆盖 BOM 中的稳定版以使用最新特性
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    // Material Design 3 Expressive - RoundedPolygon 形状支持
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha22")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha22")
    // Material3 Adaptive Layout - for dual-pane and three-pane layouts
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.2.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Google Fonts - Variable Font支持 (使用Compose BOM中的版本)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.11.3")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Navigation 3 - 使用新的导航架构解决退出页面点击穿透问题
    implementation("androidx.navigation3:navigation3-runtime:1.1.3")
    implementation("androidx.navigation3:navigation3-ui:1.1.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0")
    // Navigation3 Scene Strategies (BottomSheet, ListDetail)
    // Using 1.3.0-alpha10 (latest available version with Navigation 3 + Adaptive integration)
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0-rc01")
    implementation("androidx.window:window:1.5.1")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Immutable Collections for Compose stability
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.0")

    // Audio Processing - Kyant0/taglib from Maven Central (supports Android SAF)
    // Replaces KTagLib which had JitPack reliability issues
    implementation("io.github.kyant0:taglib:1.0.6")

    // Note: Retrofit 2.9.0 + OkHttp 4.12.0 is the stable combination
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:converter-scalars:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Security - EncryptedSharedPreferences for proxy credentials
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.22.0")

    // Gson for JSON serialization (Retrofit)
    implementation("com.google.code.gson:gson:2.14.0")

    // Kotlinx Serialization for type-safe serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Retrofit Kotlinx Serialization Converter
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Room Database for caching
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // WorkManager for background enrichment jobs
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // Paging 3 for efficient large library handling
    implementation("androidx.paging:paging-runtime-ktx:3.5.0")
    implementation("androidx.paging:paging-compose:3.5.0")

    // Timber for logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Palette for color extraction from album art
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Baseline Profile - AOT optimization for startup performance
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Coil 3 - Image loading library
    implementation("io.coil-kt.coil3:coil:3.5.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("app.cash.turbine:turbine:1.2.1")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Keep preview tooling out of runtime APK.
    debugCompileOnly("androidx.compose.ui:ui-tooling")
}

// Force kotlin-metadata-jvm to match the Kotlin compiler version.
// Hilt 2.59.2 bundles an older copy of kotlin-metadata-jvm that rejects
// metadata emitted by Kotlin 2.4.0 with:
//   "Provided Metadata instance has version 2.4.0, while maximum supported
//    version is 2.3.0. To support newer versions, update the
//    kotlin-metadata-jvm library."
// The override must target ALL configurations (not just ksp*) because Hilt's
// aggregating-root processor runs in a separate `hiltJavaCompile` task with
// its own classpath. See lesson.md #18 for the upgrade protocol.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
            useVersion("2.4.0")
            because("Hilt bundles older kotlin-metadata-jvm; cannot read Kotlin 2.4.0 metadata")
        }
    }
}
