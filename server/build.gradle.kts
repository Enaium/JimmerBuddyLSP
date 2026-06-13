plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

dependencies {
    implementation(project(":project-structure"))
    implementation(project(":lang-parser"))
    implementation(project(":dto-lang"))
    implementation(project(":codegen"))
    implementation(project(":formatter"))
    implementation(libs.lsp4j)
    implementation(libs.jackson.dataformat.smile)
    implementation(libs.jackson.module.kotlin)
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
    implementation(libs.sqlite)
    implementation(libs.logback)
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.kotlin)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "cn.enaium.jimmer.buddy.lsp.MainKt"
}

kotlin {
    jvmToolchain(25)
}