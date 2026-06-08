allprojects {
    group = "cn.enaium.jimmer.buddy.lsp"
    version = rootProject.properties["version"].toString()

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }

    tasks.withType<Test> {
        maxHeapSize = "3G"
    }
}