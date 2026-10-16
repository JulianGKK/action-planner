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
    defaultConfig { buildConfigField("boolean", "PAG_DEMO_ONLY", "false"); manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a" }
    buildTypes {
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "PAG_DEMO_ONLY", "true")
            manifestPlaceholders["pagTestTargetPackage"] = "org.pag.prototype.a.demo"
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
tasks.matching { it.name in setOf("validateSigningDebug", "validateSigningDebugAndroidTest", "validateSigningDemo", "validateSigningDemoAndroidTest") }.configureEach { dependsOn(preparePrototypeDebugKeystore) }
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
        check(actual == setOf("android.permission.USE_BIOMETRIC",
            "org.pag.prototype.a.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")) { "Unexpected permissions: $actual" }
        val activities = document.getElementsByTagName("activity")
        check(activities.length == 1)
        val launcher = activities.item(0) as org.w3c.dom.Element
        check(launcher.getAttributeNS(namespace, "name") == "org.pag.prototype.MainActivity")
        check(launcher.getAttributeNS(namespace, "exported") == "true")
        listOf("activity-alias", "provider", "receiver", "service").forEach {
            check(document.getElementsByTagName(it).length == 0) { "Unexpected component: $it" }
        }
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
