allprojects {
    group = "cn.enaium.jimmer.buddy.lsp"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }

    tasks.withType<Test> {
        maxHeapSize = "3G"
    }
}