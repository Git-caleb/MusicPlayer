import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    kotlin("kapt")
}

android {
    namespace = "com.b230408.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.b230408.musicplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 加载签名配置
    val keystorePropertiesFile = rootProject.file("app/keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"]?.toString() ?: ""
                keyPassword = keystoreProperties["keyPassword"]?.toString() ?: ""
                val storeFileStr = keystoreProperties["storeFile"]?.toString()
                if (storeFileStr != null) {
                    storeFile = file(storeFileStr)
                }
                storePassword = keystoreProperties["storePassword"]?.toString() ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 如果keystore存在，使用签名配置；否则使用调试签名
            if (keystorePropertiesFile.exists() && file(keystoreProperties["storeFile"] as String).exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // 使用调试签名作为备用（仅用于测试）
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Material Icons Extended (更新版本以支持 AutoMirrored 图标)
    implementation("androidx.compose.material:material-icons-extended:1.7.2")
    
    // Media3 for audio playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Image loading
    implementation(libs.coil.compose)
    
    // Permissions
    // 使用直接依赖，避免版本解析问题
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    
    // ID3 Tag reading
    // Try org.jaudiotagger first, fallback to direct dependency if needed
    implementation("org.jaudiotagger:jaudiotagger:2.0.1")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
