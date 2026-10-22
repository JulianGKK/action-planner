import java.util.zip.ZipFile
plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "org.pag.prototype"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.pag.prototype.a"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.0-alpha.2"
        testInstrumentationRunner = "org.pag.prototype.I3LifecycleProbe"
    }
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig {
        buildConfigField("boolean", "PAG_DEMO_ONLY", "false")
        buildConfigField("boolean", "PAG_P76_PREVIEW", "false")
        buildConfigField("boolean", "PAG_P77_RUNTIME_E2E", "false")
        manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a"
    }
    buildTypes {
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PAG_DEMO_ONLY", "true")
            buildConfigField("boolean", "PAG_P76_PREVIEW", "false")
            manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a.demo"
        }
        create("p76demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p76demo"
            versionNameSuffix = "-p76demo"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PAG_DEMO_ONLY", "true")
            buildConfigField("boolean", "PAG_P76_PREVIEW", "true")
            buildConfigField("boolean", "PAG_P77_RUNTIME_E2E", "false")
            manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a.p76demo"
        }
        create("p77runtime") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p77runtime"
            versionNameSuffix = "-p77runtime-test"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PAG_DEMO_ONLY", "false")
            buildConfigField("boolean", "PAG_P76_PREVIEW", "false")
            buildConfigField("boolean", "PAG_P77_RUNTIME_E2E", "true")
            manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a.p77runtime"
        }
    }
    // Default instrumentation names remain debug; opt in only for demo probes.
    testBuildType = providers.gradleProperty("pagTestBuildType").orElse("debug").get()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Even debug signing stays inside this project, never in ~/.android.
    signingConfigs.getByName("debug") {
        storeFile = rootProject.file(".prototype-cache/debug.keystore")
    }
}
androidComponents {
    beforeVariants(selector().withBuildType("demo")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
kotlin { jvmToolchain(17) }
val preparePrototypeDebugKeystore = tasks.register("preparePrototypeDebugKeystore") {
    val destination = rootProject.file(".prototype-cache/debug.keystore")
    outputs.file(destination)
    doLast {
        if (!destination.exists()) {
            destination.parentFile.mkdirs()
            providers.exec {
                // Conventional public Android debug credentials, never a release/product key.
                commandLine("${System.getProperty("java.home")}/bin/keytool", "-genkeypair",
                    "-keystore", destination.absolutePath, "-storepass", "android", "-keypass", "android",
                    "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                    "-dname", "CN=Android Debug,O=Android,C=US")
            }.result.get().assertNormalExitValue()
        }
    }
}
tasks.matching { it.name in setOf("validateSigningDebug", "validateSigningDebugAndroidTest", "validateSigningDemo", "validateSigningDemoAndroidTest", "validateSigningP76demo", "validateSigningP76demoAndroidTest", "validateSigningP77runtime", "validateSigningP77runtimeAndroidTest") }.configureEach { dependsOn(preparePrototypeDebugKeystore) }
val verifyPrototypeManifest = tasks.register("verifyPrototypeManifest") {
    dependsOn("processDebugManifest")
    doLast {
        val manifest = layout.buildDirectory.file(
            "intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
        ).get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val namespace = "http://schemas.android.com/apk/res/android"
        val permissions = document.getElementsByTagName("uses-permission")
        val actual = (0 until permissions.length).map {
            (permissions.item(it) as org.w3c.dom.Element).getAttributeNS(namespace, "name")
        }.toSet()
        check(actual == setOf("android.permission.USE_BIOMETRIC", "android.permission.CAMERA",
            "org.pag.prototype.a.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) { "Unexpected permissions: $actual" }
        val cameraFeatures = document.getElementsByTagName("uses-feature")
        val optionalFeatures = (0 until cameraFeatures.length).associate {
            val feature = cameraFeatures.item(it) as org.w3c.dom.Element
            feature.getAttributeNS(namespace,"name") to feature.getAttributeNS(namespace,"required")
        }
        listOf("android.hardware.camera", "android.hardware.camera.any", "android.hardware.camera.autofocus").forEach {
            check(optionalFeatures[it] == "false") { "Camera hardware must remain optional" }
        }
        val activities = document.getElementsByTagName("activity")
        check(activities.length == 1)
        val launcher = activities.item(0) as org.w3c.dom.Element
        check(launcher.getAttributeNS(namespace, "name") == "org.pag.prototype.MainActivity")
        check(launcher.getAttributeNS(namespace, "exported") == "true")
        listOf("activity-alias", "provider", "receiver").forEach {
            check(document.getElementsByTagName(it).length == 0) { "Unexpected component: $it" }
        }
        val services=document.getElementsByTagName("service")
        check(services.length==1)
        val renderer=services.item(0) as org.w3c.dom.Element
        check(renderer.getAttributeNS(namespace,"name")=="org.pag.prototype.product.PdfRenderService")
        check(renderer.getAttributeNS(namespace,"exported")=="false")
        check(renderer.getAttributeNS(namespace,"isolatedProcess")=="true")
        check(renderer.getAttributeNS(namespace,"process")==":pdf_renderer")
        val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        check(application.getAttributeNS(namespace, "allowBackup") == "false")
        check(application.getAttributeNS(namespace, "fullBackupContent") == "@xml/backup_rules")
        check(application.getAttributeNS(namespace, "dataExtractionRules") == "@xml/data_extraction_rules")
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyPrototypeManifest) }
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":security"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
}

val verifyDemoManifest = tasks.register("verifyDemoManifest") {
    dependsOn("processDemoManifest")
    doLast {
        val manifest = layout.buildDirectory.file("intermediates/merged_manifests/demo/processDemoManifest/AndroidManifest.xml").get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val namespace = "http://schemas.android.com/apk/res/android"
        check(document.documentElement.getAttribute("package") == "org.pag.prototype.a.demo")
        val permissions = document.getElementsByTagName("uses-permission")
        val actual = (0 until permissions.length).map { (permissions.item(it) as org.w3c.dom.Element).getAttributeNS(namespace, "name") }.toSet()
        check(actual == setOf("org.pag.prototype.a.demo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) { "Unexpected demo permissions: $actual" }
        val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        check(application.getAttributeNS(namespace, "label") == "PaG Demo")
        check(application.getAttributeNS(namespace, "allowBackup") == "false")
        check(application.getAttributeNS(namespace, "usesCleartextTraffic") == "false")
        val activities = document.getElementsByTagName("activity")
        check(activities.length == 1)
        check((activities.item(0) as org.w3c.dom.Element).getAttributeNS(namespace, "name") == "org.pag.prototype.MainActivity")
        listOf("activity-alias", "provider", "receiver", "service").forEach { check(document.getElementsByTagName(it).length == 0) }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyDemoManifest) }
val verifyP76DemoIsolation = tasks.register("verifyP76DemoIsolation") {
    dependsOn("processP76demoManifest")
    doLast {
        val manifest = layout.buildDirectory.file("intermediates/merged_manifests/p76demo/processP76demoManifest/AndroidManifest.xml").get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(manifest)
        val namespace = "http://schemas.android.com/apk/res/android"
        check(document.documentElement.getAttribute("package") == "org.pag.prototype.a.p76demo")
        val buildConfig = layout.buildDirectory.file("generated/source/buildConfig/p76demo/org/pag/prototype/BuildConfig.java").get().asFile.readText()
        check("PAG_DEMO_ONLY = true" in buildConfig && "PAG_P76_PREVIEW = true" in buildConfig)
        val permissions = document.getElementsByTagName("uses-permission")
        val actual = (0 until permissions.length).map {
            (permissions.item(it) as org.w3c.dom.Element).getAttributeNS(namespace, "name")
        }.filter { it.isNotEmpty() }.toSet()
        check(actual == setOf("org.pag.prototype.a.p76demo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) { "Unexpected permissions: $actual" }
        check(document.getElementsByTagName("provider").length == 0)
        check(document.getElementsByTagName("receiver").length == 0)
        check(document.getElementsByTagName("service").length == 0) { "P76 UI preview must not package product services" }
        val app = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        check(app.getAttributeNS(namespace, "allowBackup") == "false")
        check(app.getAttributeNS(namespace, "usesCleartextTraffic") == "false")
        check(app.getAttributeNS(namespace, "label") == "PaG P76 UI Vorschau")
    }
}
val verifyP77RuntimeIsolation = tasks.register("verifyP77RuntimeIsolation") {
    dependsOn("processP77runtimeManifestForPackage", "generateP77runtimeBuildConfig", "processP77runtimeAndroidTestManifest")
    doLast {
        val factory=javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        }
        val base=layout.buildDirectory.dir("intermediates/packaged_manifests").get().asFile
        val main=factory.newDocumentBuilder().parse(File(base,"p77runtime/processP77runtimeManifestForPackage/AndroidManifest.xml"))
        val test=factory.newDocumentBuilder().parse(File(base,"p77runtimeAndroidTest/processP77runtimeAndroidTestManifest/AndroidManifest.xml"))
        check(main.documentElement.getAttribute("package")=="org.pag.prototype.a.p77runtime")
        check(test.getElementsByTagName("instrumentation").let { nodes ->
            (0 until nodes.length).any { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS("http://schemas.android.com/apk/res/android","name")=="org.pag.prototype.P77RuntimeE2EProbe" }
        })
        val build=layout.buildDirectory.file("generated/source/buildConfig/p77runtime/org/pag/prototype/BuildConfig.java").get().asFile.readText()
        check("PAG_P77_RUNTIME_E2E = true" in build && "PAG_P76_PREVIEW = false" in build && "PAG_DEMO_ONLY = false" in build)
        val app=main.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        check(app.getAttributeNS("http://schemas.android.com/apk/res/android","allowBackup")=="false")
    }
}
val verifyReleaseVariantSafety = tasks.register("verifyReleaseVariantSafety") {
    dependsOn("assembleRelease")
    doLast {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val manifestFile = layout.buildDirectory.file(
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
        ).get().asFile
        val manifest = factory.newDocumentBuilder().parse(manifestFile)
        check(manifest.documentElement.getAttribute("package") == "org.pag.prototype.a")
        check(manifest.getElementsByTagName("instrumentation").length == 0)
        val services = manifest.getElementsByTagName("service")
        check(services.length == 1)
        check((services.item(0) as org.w3c.dom.Element).getAttributeNS(
            "http://schemas.android.com/apk/res/android", "name"
        ) == "org.pag.prototype.product.PdfRenderService")
        check("P77RuntimeE2EProbe" !in manifestFile.readText())

        val build = layout.buildDirectory.file(
            "generated/source/buildConfig/release/org/pag/prototype/BuildConfig.java"
        ).get().asFile.readText()
        check("PAG_P77_RUNTIME_E2E = false" in build)
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = apkDirectory.listFiles()?.filter { it.extension == "apk" }.orEmpty()
        check(apks.size == 1) { "Expected one release APK, found ${apks.map { it.name }}" }
        ZipFile(apks.single()).use { zip ->
            val dexEntries = zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d+)?\\.dex")) }
            val dexText = dexEntries.joinToString("\n") { entry ->
                zip.getInputStream(entry).use { String(it.readBytes(), Charsets.ISO_8859_1) }
            }
            listOf("P77RuntimeLaunchProvider", "P77ProductRuntimeHarness", "P80ContentFrames", "P81RuntimeFaults", "P82CleanupFaults", "P83MemoryDiagnostics", "P85PagingDiagnostics", "P11SyntheticFixture",
                "P77 ISOLATED SYNTHETIC TEST KEY").forEach { forbidden ->
                check(forbidden !in dexText) { "Release APK contains isolated P77/test-auth code: $forbidden" }
            }
        }
    }
}
