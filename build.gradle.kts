plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // KSP version は AGP 9.0 組み込みの Kotlin 2.2.10 に合わせる
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
