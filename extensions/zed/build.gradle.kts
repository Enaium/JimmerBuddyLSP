import kotlin.io.path.div

tasks.register<Exec>("build") {
    description = ""
    executable = providers.exec {
        commandLine("which", "cargo")
    }.standardOutput.asText.get().trim()
    args("build", "--target", "wasm32-wasip2", "--release")
}