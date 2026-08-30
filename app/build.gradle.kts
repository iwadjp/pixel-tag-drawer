plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.iwadjp.pixeltagdrawer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iwadjp.pixeltagdrawer"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            // F-Droid の Reproducible Builds 用。release build に git revision を
            // APK (META-INF/version-control-info.textproto) へ埋め込ませない。
            // 同一ソースを別 commit / 別 checkout から build しても F-Droid 側の
            // build artifact と byte 単位で比較できるようにする。
            vcsInfo {
                include = false
            }
        }
    }

    buildFeatures {
        compose = true
        // 「おすすめ」ソートで自己パッケージ(Pixel Tag Drawer自身)を除外する判定に
        // BuildConfig.APPLICATION_ID を使うため有効化する。
        buildConfig = true
    }

    // Robolectric + Compose UI テスト (JVM単体テスト) 用。実機/エミュレータ不要で
    // レイアウトの回帰 (タグ管理パネルが可視か・終了操作が押せるか) を検証する。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    // Room (タグDBの土台。今回は画面へは未接続)
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Robolectric 上で Compose のセマンティクスツリーを検証するための最小構成。
    // ui-test-manifest は公式ドキュメント通り debugImplementation で追加する
    // (testImplementation だとテスト用マニフェストへ ComponentActivity の
    // intent-filter が正しく反映されず、Robolectric 上で解決できなかった)。
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}
