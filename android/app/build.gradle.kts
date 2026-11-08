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
        // 覆盖安装要求 versionCode 严格递增；CI 用 github.run_number 注入。
        versionCode = System.getenv("DM_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("DM_VERSION_NAME") ?: "0.3.0"
        // 与 build-apk.yml 里 cargo-ndk 交叉编译的三个 ABI 一致
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // CI 从 Actions Secret 恢复固定的 PKCS12 密钥，保证每次构建签名一致，
        // 手机才能覆盖安装升级。本地不配置时回落到 debug key（仅用于本机调试）。
        val keystore = (findProperty("dm.keystore") as String?) ?: System.getenv("DM_KEYSTORE")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                // openssl 生成的 .p12 是 PKCS12，不显式声明会被当成 JKS 解析失败
                storeType = "PKCS12"
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
