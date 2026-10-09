plugins { id("com.android.library") }
android {
    namespace = "org.unmukto.obadh.engine"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    sourceSets["main"].jniLibs.srcDir("../app/src/main/jniLibs")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
