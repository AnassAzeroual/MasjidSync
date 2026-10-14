import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing material lives in .key_store/keystore.properties, which is gitignored. A root-level
// keystore.properties is still honoured for backward compatibility. If neither exists (fresh clone,
// CI) the release build falls back to debug signing instead of failing.
val keystorePropertiesFile = listOf(
    rootProject.file(".key_store/keystore.properties"),
    rootProject.file("keystore.properties"),
).firstOrNull { it.exists() }

val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile != null) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// `storeFile` is resolved relative to the properties file that declared it, so the keystore can live
// next to keystore.properties inside .key_store/ instead of at the repo root.
val keystoreStoreFile: File? = keystorePropertiesFile?.let { propertiesFile ->
    keystoreProperties.getProperty("storeFile")?.let { declared ->
        val path = File(declared)
        if (path.isAbsolute) path else propertiesFile.parentFile.resolve(path)
    }
}

// A properties file that points at a missing keystore is a hard misconfiguration: fail loudly rather
// than silently producing a debug-signed "release" build.
if (keystoreStoreFile != null && !keystoreStoreFile.exists()) {
    throw GradleException(
        "Release signing is configured but the keystore is missing: $keystoreStoreFile\n" +
            "Fix `storeFile` in ${keystorePropertiesFile!!.name}, or remove that file to fall back to " +
            "debug-signed release artifacts.",
    )
}

android {
    namespace = "com.mosquetracker.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mosquetracker.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystoreStoreFile != null) {
            create("release") {
                storeFile = keystoreStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // minSdk 26 means JAR (v1) signing is unnecessary; v3 is what enables key rotation.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking. Room/Compose/Hilt ship their own consumer rules,
            // and this app does no reflection-based serialization, so it is safe.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Real release signing when keystore.properties is present, debug signing otherwise
            // so `assembleRelease` still works for anyone who clones the repo.
            signingConfig = if (keystoreStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Adopt Kotlin's future default for annotations on constructor properties
        // (e.g. Hilt qualifiers like @ApplicationContext). Removes KT-73255 warnings.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    // Export generated Room schema so migrations are reviewable in git.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.tooling)

    debugImplementation(libs.androidx.ui.tooling)
}
