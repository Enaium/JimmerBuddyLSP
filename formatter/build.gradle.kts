plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}

dependencies {
    implementation(libs.antlr)
    testImplementation(project(":dto-lang"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}