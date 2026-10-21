import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Release signing comes from keystore.properties (local, git-ignored) or QUANTM_KEYSTORE_* env vars (CI)
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = signing.getProperty(key) ?: System.getenv("QUANTM_KEYSTORE_" + key.uppercase())

val appVersion = providers.gradleProperty("quantm.version").orElse("1.0.0").get()
// 1.2.3 -> 1002003999 for the release; dev builds (scripts/dev-build.js) use 1002003001, 002... below it
val appVersionCode = providers.gradleProperty("quantm.versionCode").orNull?.toInt() ?: run {
    val (major, minor, patch) = appVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }.plus(listOf(0, 0, 0)).take(3)
    (major * 10000 + minor * 100 + patch) * 1000 + 999
}
// Dev builds update from the private dev repository, public builds from GitHub releases
val devToken = rootProject.file("dev-update-token.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()

android {
    namespace = "app.quantm.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.quantm.android"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "UPDATE_REPO", "\"quantm-app/Quantm-Android\"")
        buildConfigField("String", "UPDATE_TOKEN", "\"\"")
        resValue("string", "app_name", "Quantm")
    }

    signingConfigs {
        create("release") {
            val store = signingValue("file")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("password")
                keyAlias = signingValue("alias")
                keyPassword = signingValue("keyPassword") ?: signingValue("password")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Quantm Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
        // release code (R8-shrunk) that's still debuggable, for testing what users get
        create("qa") {
            initWith(getByName("release"))
            isDebuggable = true
            applicationIdSuffix = ".qa"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "Quantm QA")
        }
        create("dev") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            matchingFallbacks += "release"
            resValue("string", "app_name", "Quantm Dev")
            buildConfigField("String", "UPDATE_REPO", "\"drumplayer1234/Quantm-Dev\"")
            buildConfigField("String", "UPDATE_TOKEN", "\"$devToken\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
