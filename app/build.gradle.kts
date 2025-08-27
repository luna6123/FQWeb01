import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keyProps = Properties()
val keyPropsFile: File = rootProject.file("keystore/keystore.properties")
// 如果keystore目录不存在，创建它
if (!keyPropsFile.parentFile.exists()) {
    keyPropsFile.parentFile.mkdirs()
    // 创建一个空的keystore.properties文件
    keyPropsFile.createNewFile()
}
if (keyPropsFile.exists()) {
    keyProps.load(`java.io`.FileInputStream(keyPropsFile))
}

android {
    namespace = "me.fycz.fqweb"
    compileSdk = 33

    defaultConfig {
        applicationId = "me.fycz.fqweb"
        minSdk = 24
        targetSdk = 33
        versionCode = 142
        versionName = "1.4.2-frpc-debug"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("myConfig") {
            keyAlias = keyProps["keyAlias"].toString()
            keyPassword = keyProps["keyPassword"].toString()
            storeFile = file(keyProps["storeFile"].toString())
            storePassword = keyProps["storePassword"].toString()
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keyPropsFile.exists() && keyProps.getProperty("storeFile", "").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("myConfig")
            }
        }
    }

    splits {
        abi {
            reset()
            isEnable = true
            isUniversalApk = true  // If true, also generate a universal APK
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    android.applicationVariants.all {
        outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach {
                val abi = it.getFilter("ABI") ?: "universal"
                val fileName = "FQWeb_Frpc_v${defaultConfig.versionName}_$abi.apk"
                it.outputFileName = fileName
            }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    compileOnly(files("libs/api-82.jar"))

    //webServer
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    //frpc
    implementation(files("libs/frpclib.aar"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
