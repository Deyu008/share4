plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.deyu.share"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deyu.share"
        minSdk = 26
        targetSdk = 35
        versionCode = 4000
        versionName = "4.0.0-alpha1"
        ndk {
            abiFilters += "arm64-v8a"   // cum 桥 so 为 arm64;模拟器走 arm 转译,真机原生
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true          // R8 代码裁剪(material-icons-extended 等未用类全部剔除)
            isShrinkResources = true        // 资源收缩
            signingConfig = signingConfigs.getByName("debug")   // 便于直接安装;正式分发再换签名
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("dev.chrisbanes.haze:haze:1.2.2")   // App 内毛玻璃(高级材质等价,RenderEffect)
    implementation("androidx.media3:media3-exoplayer:1.4.1")  // 视频播放
    implementation("androidx.media3:media3-ui:1.4.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
