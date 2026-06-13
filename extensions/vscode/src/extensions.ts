import * as path from "node:path";
import * as fs from "node:fs";
import * as os from "node:os";
import * as vscode from "vscode";
import {
  LanguageClient,
  type LanguageClientOptions,
  type ServerOptions, TransportKind,
} from "vscode-languageclient/node";

const serverJar = path.join(os.homedir(), "JimmerBuddyLSP", "server.jar");
const embedJar = path.join(__dirname, "server.jar");

function ensureServerJar() {
  if (fs.existsSync(serverJar)) {
    try {
      fs.unlinkSync(serverJar);
    } catch (e) {}
  }

  fs.mkdirSync(path.join(os.homedir(), "JimmerBuddyLSP"), { recursive: true });
  fs.copyFileSync(embedJar, serverJar);
}

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
  ensureServerJar()
  const serverOptions: ServerOptions = {
    command: "java",
    args: ["--enable-native-access=ALL-UNNAMED", "-cp", serverJar, "cn.enaium.jimmer.buddy.lsp.MainKt"],
    transport: TransportKind.stdio
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [
      { scheme: "file", pattern: "**/*.java" },
      { scheme: "file", pattern: "**/*.kt" },
      { scheme: "file", pattern: "**/*.dto" },
    ],
  };

  client = new LanguageClient(
    "jimmerBuddyLSP",
    "Jimmer Buddy LSP",
    serverOptions,
    clientOptions,
  );

  client.start();
}

export function deactivate() {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
