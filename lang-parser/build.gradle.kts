plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.antlr)
    implementation(libs.coroutines)
    implementation(libs.jimmer.sql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.kotlin)
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