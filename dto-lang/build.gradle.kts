plugins {
    antlr
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    antlr(libs.antlr)
    implementation(projects.projectStructure)
    implementation(projects.langParser)
    implementation(projects.codegen)
    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.symbol.processing.api)
    implementation(libs.jimmer.dto.compiler)
    implementation(libs.jimmer.core)
    implementation(libs.jimmer.apt)
    implementation(libs.jimmer.ksp)
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