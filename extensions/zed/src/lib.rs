use zed_extension_api as zed;

struct JimmerBuddyLspExtension;

impl zed::Extension for JimmerBuddyLspExtension {
    fn new() -> Self {
        Self
    }

    fn language_server_command(
        &mut self,
        _language_server_id: &zed::LanguageServerId,
        worktree: &zed::Worktree,
    ) -> zed::Result<zed::Command> {
        let java = worktree
            .which("java")
            .ok_or_else(|| "Java not found in PATH".to_string())?;

        let home = worktree
            .shell_env()
            .iter()
            .find(|(key, _)| key == "HOME")
            .map(|(_, value)| value.clone())
            .ok_or_else(|| "HOME not found in environment".to_string())?;

        let server_jar = format!("{}/JimmerBuddyLSP/server.jar", home);

        Ok(zed::Command {
            command: java,
            args: vec![
                String::from("--enable-native-access=ALL-UNNAMED"),
                String::from("-cp"),
                server_jar,
                String::from("cn.enaium.jimmer.buddy.lsp.MainKt"),
            ],
            env: worktree.shell_env(),
        })
    }
}

zed::register_extension!(JimmerBuddyLspExtension);
