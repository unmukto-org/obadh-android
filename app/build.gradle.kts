import java.time.OffsetDateTime
import java.util.Properties
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** Short git revision, with -dirty for an uncommitted tree: lets About say what is installed. */
fun gitRevision(): String = try {
    fun git(vararg args: String): String {
        val process = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return out
    }
    val rev = git("rev-parse", "--short", "HEAD")
    if (git("status", "--porcelain").isNotEmpty()) "$rev-dirty" else rev
} catch (e: Exception) {
    ""
}

fun buildTimeUtc(): String =
    OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " UTC"

// Release signing comes from keystore.properties at the repo root (git-ignored), with keys
// storeFile, storePassword, keyAlias, keyPassword. Without it a release build is UNSIGNED and
// Android refuses to install it. `-PdebugSign` signs with the local debug key instead, which is
// enough to test a release build on a device that already has the debug build (same signature).
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.unmukto.obadh"
    compileSdk = 35

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "org.unmukto.obadh"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Provenance shown in About: lets you tell what is actually installed.
        buildConfigField("String", "GIT_REVISION", "\"${gitRevision()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${buildTimeUtc()}\"")
        // Engine ships as per-ABI .so under src/main/jniLibs (scripts/build-rust-android.sh).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = when {
                project.hasProperty("debugSign") -> signingConfigs.getByName("debug")
                keystoreProps.isNotEmpty() -> signingConfigs.getByName("release")
                else -> null
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
    // The models are NOT stored uncompressed any more. The engine opens them by path, so the first
    // launch copies them out of the APK to private storage regardless; letting the APK deflate them
    // cuts the download by about 24 MB (the n-gram falls to ~36%, the autocorrect FST to ~50%) at the
    // cost of one slower first launch. See docs/build-and-release.md.
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
}
