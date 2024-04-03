plugins {
//    id("com.android.library")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The magic plugin that automates the cargo integration
    id("com.github.willir.rust.cargo-ndk-android")
}

android {
    namespace = "alt.nainapps.sharepaste.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Essential! Note that UniFFI dictates the minimum supported version, and this may change with new releases
    implementation("net.java.dev.jna:jna:5.12.0@aar")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

cargoNdk {
    module  = "../rust"  // Directory containing Cargo.toml
    librariesNames = stringArrayOf("libfoobar.so", "libpbcli.so")
    targets = stringArrayOf("arm64", "arm")
}

android.libraryVariants.all { variant ->
    // Define the super task
    val generateBindings: TaskProvider<DefaultTask> =
        tasks.register("generate${variant.name.capitalize()}UniFFIBindings") {
            dependsOn(
                "generate${variant.name.capitalize()}BindingsForLibfoobar",
                "generate${variant.name.capitalize()}BindingsForLibpbcli"
            )
        }

    // Define the first sub-task for generating bindings for libfoobar
    val generateBindingsForLibfoobar: TaskProvider<Exec> = tasks.register(
        "generate${variant.name.capitalize()}BindingsForLibfoobar",
        Exec::class.java
    ) {
        workingDir = file("../../rust")
        commandLine(
            "cargo",
            "run",
            "-p",
            "uniffi-bindgen",
            "generate",
            "--library",
            "../android/core/src/main/jniLibs/arm64-v8a/libfoobar.so",
            "--language",
            "kotlin",
            "--out-dir",
            "${buildDir}/generated/source/uniffi/${variant.name}/java"
        )
        dependsOn("buildCargoNdk${variant.name.capitalize()}")
    }

    // Define the second sub-task for generating bindings for libpbcli
    val generateBindingsForLibpbcli: TaskProvider<Exec> = tasks.register(
        "generate${variant.name.capitalize()}BindingsForLibpbcli",
        Exec::class.java
    ) {
        workingDir = file("../../rust")
        commandLine(
            "cargo",
            "run",
            "-p",
            "uniffi-bindgen",
            "generate",
            "--library",
            "../android/core/src/main/jniLibs/arm64-v8a/libpbcli.so",
            "--language",
            "kotlin",
            "--out-dir",
            "${buildDir}/generated/source/uniffi/${variant.name}/java"
        )
        dependsOn("buildCargoNdk${variant.name.capitalize()}")
    }

    variant.javaCompileProvider.get().dependsOn(generateBindings)

    // Some stuff here is broken, since Android Tests don't run after running gradle build,
    // but do otherwise. Also CI is funky.
    tasks.named("compile${variant.name.capitalize()}Kotlin").configure {
        dependsOn(generateBindings)
    }

    tasks.named("connectedDebugAndroidTest").configure {
        dependsOn(generateBindings)
    }

    val sourceSet = variant.sourceSets.find { it.name == variant.name }
    sourceSet?.java?.srcDir(file("${buildDir}/generated/source/uniffi/${variant.name}/java"))

    // UniFFI tutorial notes that they made several attempts like this but were unsuccessful coming
    // to a good solution for forcing the directory to be marked as generated (short of checking in
    // project files, I suppose).
    // idea.module.generatedSourceDirs += file("${buildDir}/generated/source/uniffi/${variant.name}/java/uniffi")

    sourceSet?.java?.srcDir("src/main/java")
}