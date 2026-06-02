plugins {
    java
    antlr
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.coroutines)
    implementation(libs.jimmer.sql)
    implementation(libs.jackson.module.kotlin)
    ksp(libs.jimmer.ksp)
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