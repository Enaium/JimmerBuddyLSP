plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":project-structure"))
    implementation(project(":lang-parser"))
    implementation(libs.coroutines)
    implementation(libs.byte.buddy)
    implementation(libs.kotlinpoet)
    implementation(libs.javapoet)
    implementation(libs.jimmer.core)
    implementation(libs.jimmer.sql)
    implementation(libs.jimmer.apt)
    implementation(libs.jimmer.ksp)
    implementation(libs.jimmer.dto.compiler)
    implementation(libs.symbol.processing.api)
    implementation(libs.jspecify)
    implementation(libs.logback)
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