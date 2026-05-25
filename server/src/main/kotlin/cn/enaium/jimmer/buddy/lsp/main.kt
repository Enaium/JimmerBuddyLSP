/*
 * Copyright 2026 Enaium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.enaium.jimmer.buddy.lsp

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

/**
 * @author Enaium
 */
fun main(args: Array<String>) {
    args.find { it.startsWith("--port=") }?.also {
        it.split("=")[1].let { port ->
            val server = ServerSocket(port.toInt())
            val accept = server.accept()
            start(accept.getInputStream(), accept.getOutputStream())
        }
    } ?: start(System.`in`, System.out)
}

var client: LanguageClient? = null

fun start(input: InputStream, output: OutputStream) {
    val server = BuddyServer()
    val launcher = Launcher.createLauncher(server, LanguageClient::class.java, input, output)
    client = launcher.remoteProxy
    launcher.startListening()
}