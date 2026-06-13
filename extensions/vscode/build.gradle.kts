import kotlin.io.path.div

tasks.register<Exec>("build") {
    description = ""
    doFirst {
        project(":server").tasks.named<Jar>("shadowJar").get().outputs.files.files.forEach { file ->
            file.copyTo((projectDir.toPath() / "out/server.jar").toFile(), true)
        }
    }
    executable = providers.exec {
        commandLine("which", "bun")
    }.standardOutput.asText.get().trim()
    args("run", "package")
    dependsOn(project(":server").tasks.named<Jar>("shadowJar"))
}