plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "JimmerBuddyLSP"

include(
    "project-structure",
    "lang-parser",
    "dto-lang",
    "codegen",
    "formatter",
    "server",
    ":extensions:vscode",
    ":extensions:zed",
)