import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.feedlite"
    // Android 16 = API 36
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.feedlite"
        minSdk = 26
        targetSdk = 36
        versionCode = 36
        versionName = "1.35"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                // ★ v1.32 安全加固：不再内置默认口令。
                // 口令来源优先级：环境变量 FEEDLITE_STORE_PASS / FEEDLITE_KEY_PASS
                // → 本地 gitignored 的 keystore/keystore.properties。
                // keystore/ 目录整体被 .gitignore 排除，不会进入 VCS。
                val props = Properties().apply {
                    val f = rootProject.file("keystore/keystore.properties")
                    if (f.exists()) f.inputStream().use { load(it) }
                }
                val envStorePass: String? = System.getenv("FEEDLITE_STORE_PASS")
                storePassword = envStorePass
                    ?: props.getProperty("storePassword")
                    ?: error("缺少 release 签名口令：请设置环境变量 FEEDLITE_STORE_PASS 或创建 keystore/keystore.properties")
                keyAlias = "feedlite"
                val envKeyPass: String? = System.getenv("FEEDLITE_KEY_PASS")
                keyPassword = envKeyPass
                    ?: props.getProperty("keyPassword")
                    ?: error("缺少 release 签名口令：请设置环境变量 FEEDLITE_KEY_PASS 或创建 keystore/keystore.properties")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM 单测里解析 RSS 用 kxml2（自带 org.xmlpull API，替代 android.util.Xml stub）
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    debugImplementation(libs.androidx.ui.tooling)
}

// ★ v1.32：动效 token 防漂移校验（与 android-compose 端共用 tools/ 脚本）。
// 运行：gradlew verifyMotionTokens
tasks.register<Exec>("verifyMotionTokens") {
    group = "verification"
    description = "校验 MotionTokens.kt / styles.css / motion-tokens.md 三处 token 值一致"
    val script = rootProject.file("../tools/verify-motion-tokens.ps1").absolutePath
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script)
}
