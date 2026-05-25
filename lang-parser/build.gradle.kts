plugins {
    java
    antlr
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.coroutines)
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