plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "no.solver.solverappdemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "no.solver.solverappdemo"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MS Client IDs - same as iOS Config.xcconfig
        buildConfigField("String", "SOLVER_MS_CLIENT_ID", "\"c31d3ea5-b6fa-4543-ac2d-e110bdf4a6e5\"")
        buildConfigField("String", "ZOHM_MS_CLIENT_ID", "\"ecc111e6-e21d-4a2f-85f3-1db37956f3df\"")

        // Vipps OAuth - same as iOS Config.xcconfig
        buildConfigField("String", "SOLVER_VIPPS_CLIENT_ID", "\"61c3e76e-a58a-4b00-aa7f-da59841ce776\"")
        buildConfigField("String", "SOLVER_VIPPS_SECRET", "\"MGlHSU9SSnlnaTE2dDk2OVhDblQ=\"")
        buildConfigField("String", "ZOHM_VIPPS_CLIENT_ID", "\"efca7a24-cf75-4cb2-a988-83b2af39c74f\"")
        buildConfigField("String", "ZOHM_VIPPS_SECRET", "\"7ba4818d-32dc-4869-846c-1998433ca77e\"")

        // App User Credentials for user registration - same as iOS Config.xcconfig
        buildConfigField("String", "APP_USER_OID", "\"961b7aa7-0530-495d-a3d0-5294340e608f\"")
        buildConfigField("String", "APP_USER_PASSWORD", "\"01535ff0-c56e-42d5-ad97-6012cdb7a423\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            // No suffix - use base applicationId for MSAL redirect URI matching
            // applicationIdSuffix = ".debug"
            buildConfigField("Boolean", "DEBUG_MODE", "true")
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
        }
        
        create("staging") {
            initWith(getByName("debug"))
            // No suffix - use base applicationId for MSAL redirect URI matching
            // applicationIdSuffix = ".staging"
            buildConfigField("Boolean", "DEBUG_MODE", "true")
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "DEBUG_MODE", "false")
            buildConfigField("Boolean", "IS_PRODUCTION", "true")
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

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization.converter)

    // Storage
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Microsoft Auth
    implementation(libs.msal)

    // Browser Custom Tabs (for Vipps OAuth)
    implementation(libs.browser)

    // AppAuth (OAuth library for Vipps - handles web fallback automatically)
    implementation(libs.appauth)

    // Image Loading
    implementation(libs.coil.compose)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
