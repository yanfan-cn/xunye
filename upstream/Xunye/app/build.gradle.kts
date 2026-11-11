import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

apply(from = "download.gradle")


val appName = "xunye"

// SPEC-108/122 debug: MAJOR.MINOR.PATCH-beta (odometer 0–9).
// SPEC-244 release: separate file, MAJOR.MINOR.PATCH (no -beta); aloha+systemWv share one bump.
fun Properties.loadVersionFile(file: File) {
    if (file.exists()) load(FileInputStream(file))
}

fun odometerBump(major: Int, minor: Int, patch: Int): Triple<Int, Int, Int> {
    var nextMajor = major
    var nextMinor = minor
    var nextPatch = patch + 1
    if (nextPatch > 9) {
        nextPatch = 0
        nextMinor += 1
    }
    if (nextMinor > 9) {
        nextMinor = 0
        nextMajor += 1
    }
    return Triple(nextMajor, nextMinor, nextPatch)
}

fun taskLeafNames(): List<String> =
    gradle.startParameter.taskNames.map { it.substringAfterLast(':') }

val assembleLeaves = taskLeafNames().filter { it.startsWith("assemble") && it != "assemble" }
val wantsReleaseAssemble = assembleLeaves.any {
    it.contains("Release", ignoreCase = true) || it.contains("NoR8", ignoreCase = true)
}
val wantsDebugAssemble = assembleLeaves.any { it.contains("Debug", ignoreCase = true) }
if (wantsReleaseAssemble && wantsDebugAssemble) {
    throw GradleException(
        "SPEC-244: do not mix debug and release assemble in one Gradle invocation " +
            "(release uses version-release.properties; debug uses version.properties).",
    )
}

val useReleaseVersion = wantsReleaseAssemble
val versionPropsFile = if (useReleaseVersion) {
    file("version-release.properties")
} else {
    file("version.properties")
}
val versionProps = Properties().apply { loadVersionFile(versionPropsFile) }
val versionCodeValue = versionProps.getProperty("VERSION_CODE", "1").toInt()
val versionMajor = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
val versionMinor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
val versionPatch = versionProps.getProperty("VERSION_PATCH", "0").toInt()
val projectVersionName = if (useReleaseVersion) {
    "$versionMajor.$versionMinor.$versionPatch"
} else {
    "$versionMajor.$versionMinor.$versionPatch-beta"
}

if (assembleLeaves.isNotEmpty()) {
    val (nextMajor, nextMinor, nextPatch) = odometerBump(versionMajor, versionMinor, versionPatch)
    val nextCode = versionCodeValue + 1
    versionProps.setProperty("VERSION_MAJOR", nextMajor.toString())
    versionProps.setProperty("VERSION_MINOR", nextMinor.toString())
    versionProps.setProperty("VERSION_PATCH", nextPatch.toString())
    versionProps.setProperty("VERSION_CODE", nextCode.toString())
    versionProps.remove("VERSION_TENTHS")
    val storeComment = if (useReleaseVersion) {
        "SPEC-244 bumped after release assemble (odometer 0-9; no -beta)"
    } else {
        "Bumped after assemble (odometer 0-9 per digit)"
    }
    versionPropsFile.outputStream().use { versionProps.store(it, storeComment) }
    val nextLabel = if (useReleaseVersion) {
        "$nextMajor.$nextMinor.$nextPatch"
    } else {
        "$nextMajor.$nextMinor.$nextPatch-beta"
    }
    logger.lifecycle(
        "Xunye this assemble -> $projectVersionName (code $versionCodeValue); " +
            "next -> $nextLabel (code $nextCode)" +
            if (useReleaseVersion) " [release]" else " [debug]",
    )
}
val enableAbiSplits = providers.gradleProperty("enableAbiSplits")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    compileSdk = 37
    namespace = "io.legado.app"

    signingConfigs {
        if (project.hasProperty("RELEASE_STORE_FILE")) {
            create("myConfig") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
    applicationId = "com.xunye.app"
        // SPEC-251: Foxlet/Bergamot AAR requires API 28+.
        minSdk = 28
        targetSdk = 37
        versionCode = versionCodeValue
        versionName = System.getenv("APP_VERSION_NAME") ?: projectVersionName
        testInstrumentationRunner = "androidx.test.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        buildConfigField("String", "Cronet_Version", "\"${project.findProperty("CronetVersion")}\"")
        buildConfigField(
            "String",
            "Cronet_Main_Version",
            "\"${project.findProperty("CronetMainVersion")}\""
        )

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental" to "true",
                    "room.expandProjection" to "true",
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        getByName("release") {
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.getByName("myConfig")
            }
            manifestPlaceholders["app_name"] = "@string/app_name"
            isMinifyEnabled = true
            // SPEC-153: Bromium resolves its resources by name at runtime
            // (BromiumResources reads res/xml/bromium_res_index.xml); the static
            // resource shrinker cannot trace those lookups and stripped the
            // index + aw_context_menu_* / text_edit_suggestion_* etc., crashing
            // on long-press. Keep all resources; R8 minification stays on.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
        create("noR8") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-noR8"
        }
        getByName("debug") {
            // Xunye: same applicationId as probe / release for install overlay
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.getByName("myConfig")
            }
            manifestPlaceholders["app_name"] = "@string/app_name"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = enableAbiSplits
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        // Default product: bundled Aloha Chromium (决策基线 §3.5).
        // Named `aloha` (not `app`) so AGP wires src/aloha — flavor==module name
        // `:app` breaks default src/<flavor> discovery (SPEC-222).
        create("aloha") {
            dimension = "mode"
            isDefault = true
            manifestPlaceholders["APP_CHANNEL_VALUE"] = "app"
            buildConfigField("boolean", "USE_SYSTEM_WEBVIEW", "false")
        }
        // SPEC-222: side-path without Aloha — system WebView only.
        create("systemWv") {
            dimension = "mode"
            manifestPlaceholders["APP_CHANNEL_VALUE"] = "systemWv"
            versionNameSuffix = "-systemwv"
            buildConfigField("boolean", "USE_SYSTEM_WEBVIEW", "true")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes.add("META-INF/*")
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        noCompress += "bin"
        noCompress += "dat"
        noCompress += "pak"
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    lint {
        baseline = file("lint-baseline.xml")
        checkDependencies = true
        // UnusedResources 在基线里占 1223/1621 条、12218 行（全文件的 75%），几乎全是
        // 跟随上游时留下的资源；release 本来就开了精确资源压缩，它们不会进 APK。
        // 关掉它让基线只剩真正值得盯的那部分，新增问题照样报红。
        disable += "UnusedResources"
    }

    testOptions {
        unitTests {
            // 阅读器核心在构造期就读字符串资源（TextPageFactory 的 keepSwipeTip、
            // TextPage 的默认 text/title、ReadView 的无障碍动作名）。不打开这个，
            // Robolectric 下取任何 R.string 都是 Resources$NotFoundException，
            // 整条阅读器测试线（Track D·D1c）就起不来。
            isIncludeAndroidResources = true
            // SPEC-148 R1a: MainNavigator（SPEC-124/145）与 BrowserVideoOfferBus
            // （SPEC-145/147）的 Diag Log.i 走 JVM 纯单测路径时 android.util.Log 未
            // mock 直接崩（7 个用例）。返回默认值后 Log 调用静默通过，产品代码不动。
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "false")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    // baselineProfile module optional; omitted temporarily if tree incomplete
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.bundles.androidTest)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)
    implementation(libs.core.ktx)
    implementation(libs.appcompat.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    // SPEC-222: androidx.webkit only on systemWv (Aloha ships support_lib_boundary).
    "systemWvImplementation"(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.flexbox)
    implementation(libs.gson)
    // SPEC-171: on-device EN/JA → zh for reading-mode translation.
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.service)
    implementation(libs.media.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    // SPEC-132: ExoMedia wrapper (Apache 2.0); aligns Media3 via Gradle resolution.
    implementation(libs.exomedia)
    implementation(libs.splitties.appctx)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
    implementation(libs.liveeventbus)
    implementation(libs.jsoup)
    implementation(libs.json.path)
    implementation(libs.jsoupxpath)
    implementation(libs.intellij.markdown)
    implementation(project(":modules:book"))
    implementation(project(":modules:rhino"))
    implementation(libs.okhttp)
    // Aloha embeds org.chromium.base — keep only Cronet API stubs to avoid duplicate classes.
    implementation(files("cronetlib/cronet_api.jar"))
    // SPEC-222: Aloha Core only on default `aloha` flavor (not systemWv).
    "alohaImplementation"(files("libs/aloha-bromium-localbuild.aar"))
    // SPEC-251: Mozilla Bergamot via Foxlet (arm64 AAR; models downloaded on demand).
    implementation(files("libs/foxlet-v0.5.0.aar"))
    implementation("com.google.guava:guava:33.3.1-android")
    // Cimoc A adapters (SPEC-016)
    implementation("com.alibaba:fastjson:1.2.83")
    // SPEC-123: bypass Android 14 hidden-API blocklist (setHiddenApiExemptions) to enable DrawFn hardware path
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation(libs.protobuf.javalite)
    implementation(libs.glide.glide)
    implementation(libs.glide.okhttp)
    ksp(libs.glide.ksp)
    implementation(libs.androidsvg)
    implementation(libs.glide.svg)
    implementation(libs.glide.recyclerview)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.zxing.lite)
    implementation(libs.colorpicker)
    implementation(libs.colorpicker.compose)
    implementation(libs.libarchive)
    implementation(libs.commons.text)
    implementation(libs.markwon.core)
    implementation(libs.markwon.image.glide)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.quick.chinese.transfer.core)
    // 书源 JS 通过 Packages.cn.hutool.* 调用，保留在 classpath（应用代码不依赖，见 AGENTS.md）
    implementation(libs.hutool.crypto)
    //noinspection GradleDependency
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
    // Xunye: accompanist-webview pulls androidx.webkit (duplicate with Aloha)
    // implementation(libs.accompanist.webview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // 显式保持 navigationevent 1.2 系列，保留已分离输入的预测式返回崩溃修复。
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.reorderable)
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
    implementation(libs.capsule)
    implementation(libs.backdrop)
    implementation(libs.lyricViewx)
    implementation(libs.timber)
}

// SPEC-108/244: copy APK to 巡页{version}.apk；systemWv → 巡页{version}-systemwv.apk.
// Only touch the build-type folder matching this assemble (release vs debug).
val xunyeApkVersionLabel = projectVersionName
val xunyeRenameReleaseOnly = useReleaseVersion
val renameXunyeApks = tasks.register("renameXunyeApks") {
    val versionLabel = xunyeApkVersionLabel
    val releaseOnly = xunyeRenameReleaseOnly
    val apkRoot = layout.buildDirectory.dir("outputs/apk")
    doLast {
        val root = apkRoot.get().asFile
        if (!root.exists()) return@doLast
        val buildTypeSeg = if (releaseOnly) "/release/" else "/debug/"
        val candidates = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .filter { !it.name.startsWith("巡页") }
            .filter {
                it.path.replace('\\', '/').lowercase().contains(buildTypeSeg)
            }
            .toList()
        candidates.groupBy { it.parentFile }.forEach { (dir, list) ->
            val apk = list.firstOrNull { !it.name.contains("unsigned", ignoreCase = true) }
                ?: list.firstOrNull()
                ?: return@forEach
            val pathLower = apk.path.replace('\\', '/').lowercase()
            val isSystemWv = pathLower.contains("/systemwv/")
            val fileLabel = if (isSystemWv && !versionLabel.endsWith("-systemwv")) {
                "$versionLabel-systemwv"
            } else {
                versionLabel
            }
            val dest = File(dir, "巡页${fileLabel}.apk")
            apk.copyTo(dest, overwrite = true)
            println("SPEC-108/244 APK -> ${dest.absolutePath}")
        }
    }
}
listOf(
    "assembleAlohaDebug",
    "assembleAlohaRelease",
    "assembleAlohaNoR8",
    "assembleSystemWvDebug",
    "assembleSystemWvRelease",
).forEach { assembleName ->
    tasks.matching { it.name == assembleName }.configureEach {
        finalizedBy(renameXunyeApks)
    }
}

