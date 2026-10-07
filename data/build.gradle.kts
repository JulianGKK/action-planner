plugins { id("com.android.library") }
android {
    namespace = "org.pag.data"
    compileSdk = 37
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":domain"))
    implementation(project(":security"))
    testImplementation("junit:junit:4.13.2")
}
