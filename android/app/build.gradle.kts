plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ifwqu.domainmail"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ifwqu.domainmail"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 与 build-apk.yml 里 cargo-ndk 交叉编译的三个 ABI 一致
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // CI 可用 -Pdm.keystore=... 或 DM_KEYSTORE 等环境变量注入正式签名。
        // 未注入时回落到 debug key，保证 assembleRelease 产物一定是已签名、可直接安装的 APK。
        val keystore = (findProperty("dm.keystore") as String?) ?: System.getenv("DM_KEYSTORE")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = (findProperty("dm.keystore.password") as String?)
                    ?: System.getenv("DM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = (findProperty("dm.key.alias") as String?)
                    ?: System.getenv("DM_KEY_ALIAS") ?: ""
                keyPassword = (findProperty("dm.key.password") as String?)
                    ?: System.getenv("DM_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        jniLibs { useLegacyPackaging = false }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    sourceSets {
        getByName("main") { jniLibs.srcDirs("src/main/jniLibs") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
