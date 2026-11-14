import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// release 서명: 키는 저장소에 넣지 않는다.
// local.properties에 `shimpyo.signing=/경로/서명.properties`가 있으면 그 파일(storeFile, storePassword, keyAlias, keyPassword)로 서명
fun loadProps(f: File): Properties = Properties().apply { if (f.exists()) f.inputStream().use { load(it) } }
val signingProps: Properties? = loadProps(rootProject.file("local.properties"))
    .getProperty("shimpyo.signing")?.let { file(it) }?.takeIf { it.exists() }?.let { loadProps(it) }

android {
    namespace = "com.daebangeo.shimpyo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daebangeo.shimpyo"
        // 공모전 요구사항: Android API 28 이상
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.2"
    }

    // AAOS 표준 차량 속성(기어 등)을 CarPropertyManager로 읽기 위해 필요
    useLibrary("android.car")

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    testImplementation(libs.junit)
}
