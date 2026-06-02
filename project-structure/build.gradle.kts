plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.langParser)
    implementation(libs.gradle.tooling.api)
    implementation(libs.jackson.dataformat.smile)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.coroutines)
    implementation(libs.sqlite)
    implementation(libs.jimmer.sql)
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}