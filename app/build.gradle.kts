import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Version comes from the git tag in CI (see .github/workflows/release.yml).
// These are only fallbacks for local builds; do not hand-bump them for a release.
val appVersionName: String = providers.gradleProperty("appVersionName").orNull?.takeIf { it.isNotBlank() }
    ?: System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "1.4.1"

val appVersionCode: Int = (providers.gradleProperty("appVersionCode").orNull?.takeIf { it.isNotBlank() }
    ?: System.getenv("APP_VERSION_CODE")?.takeIf { it.isNotBlank() })
    ?.toIntOrNull()
    ?: 10401

// GitHub OAuth Client ID (public for Device Flow)
val githubClientId = "Ov23linXkLavDNV3zFMB"

// Signing configuration from environment or local.properties
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { this.load(it) }
}
val signingKeystorePath = System.getenv("KEYSTORE_FILE") ?: localProps.getProperty("KEYSTORE_FILE")
val signingKeystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProps.getProperty("KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("KEY_ALIAS") ?: localProps.getProperty("KEY_ALIAS")
val signingKeyPassword = System.getenv("KEY_PASSWORD") ?: localProps.getProperty("KEY_PASSWORD")

// Check if all signing credentials are available and not empty
val hasSigningConfig = !signingKeystorePath.isNullOrBlank() && 
                       !signingKeystorePassword.isNullOrBlank() && 
                       !signingKeyAlias.isNullOrBlank() && 
                       !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.darkmintis.gitstore"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/kotlin")
            res.srcDirs("src/main/res")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    defaultConfig {
        applicationId = "com.darkmintis.gitstore"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${githubClientId}\"")
        buildConfigField("String", "VERSION_NAME", "\"${appVersionName}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingKeystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign the APK if signing config is available
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Koin DI
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // HTTP and serialization
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Logging
    implementation(libs.kermit)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    // Date-time
    implementation(libs.kotlinx.datetime)

    // Navigation 3
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.jetbrains.lifecycle.viewmodel)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)

    // Markdown
    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.coil3)

    // Liquid
    implementation(libs.liquid)

    // Data store
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.androidx.room.compiler)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.core.splashscreen)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.framework.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
