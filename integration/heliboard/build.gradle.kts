plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
val upstream = rootProject.file(".upstream/heliboard/app/src/main")
android {
    namespace = "helium314.keyboard.latin"
    compileSdk { version = release(37) }
    ndkVersion = "27.0.12077973"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            ndkBuild {
                arguments += listOf("APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true", "APP_LDFLAGS=-Wl,-z,common-page-size=16384")
            }
        }
        buildConfigField("String", "APPLICATION_ID", "\"org.unmukto.obadh\"")
        buildConfigField("String", "VERSION_NAME", "\"0.2.0\"")
        buildConfigField("int", "VERSION_CODE", "2")
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { viewBinding = true; buildConfig = true; compose = true }
    sourceSets["main"].apply {
        // Keep upstream R/JNI namespaces stable; Obadh code uses a separate engine module.
        java.setSrcDirs(listOf(upstream.resolve("java/com"), upstream.resolve("java/helium314")))
        kotlin.setSrcDirs(listOf(upstream.resolve("java/com"), upstream.resolve("java/helium314")))
        res.srcDir(upstream.resolve("res"))
        assets.srcDir(upstream.resolve("assets"))
        manifest.srcFile("AndroidManifest.xml")
    }
    externalNativeBuild { ndkBuild { path = upstream.resolve("jni/Android.mk") } }
    sourceSets["test"].apply {
        java.srcDir("tests")
        kotlin.srcDir("tests")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}
kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    api(project(":engine"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.github.skydoves:colorpicker-compose:1.1.3")
}
