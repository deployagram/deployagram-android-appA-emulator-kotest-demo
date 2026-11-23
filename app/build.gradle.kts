plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.deployagram.demo.appa"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deployagram.demo.appa"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Ends each test's sequence diagram in the Deployagram Collector when the test finishes.
        testInstrumentationRunnerArguments["listener"] = "com.deployagram.junit4.DeployagramRunListener"
        // The emulator reaches the host at 10.0.2.2. DEPLOYAGRAM_COLLECTOR_URL overrides the port, for example http://10.0.2.2:1153.
        testInstrumentationRunnerArguments["deployagram.collectorUrl"] =
            System.getenv("DEPLOYAGRAM_COLLECTOR_URL") ?: "http://10.0.2.2:1152"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources {
            // JNA (pulled in by the Kotest and MockWebServer test dependencies) ships duplicate license files.
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
            // mockwebserver and mockwebserver3 both ship this multi-release manifest. Either copy is fine.
            pickFirsts += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

// Kotlin must target the same JVM as Java (17). Otherwise Android Studio's own JDK (21) sets Kotlin's target.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    androidTestImplementation("io.kotest:kotest-runner-junit4:6.1.2")
    androidTestImplementation("io.kotest:kotest-assertions-core:6.1.2")
    // Deployagram: the listener and Collector client, and the MockWebServer recorder. Published on Maven Central.
    androidTestImplementation("com.deployagram:deployagram-junit4:1.1.0")
    androidTestImplementation("com.deployagram:deployagram-mockwebserver:1.1.0")
}
