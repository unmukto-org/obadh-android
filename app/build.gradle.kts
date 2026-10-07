import java.time.OffsetDateTime
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

    buildTypes {
        release {
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
    // FSTs and the n-gram are already compact binaries; keep them uncompressed in the APK.
    androidResources { noCompress += listOf("fst", "bin") }
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
