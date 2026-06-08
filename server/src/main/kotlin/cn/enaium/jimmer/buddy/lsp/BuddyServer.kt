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

import cn.enaium.jimmer.buddy.codegen.gen.AptGen
import cn.enaium.jimmer.buddy.codegen.gen.KspGen
import cn.enaium.jimmer.buddy.lsp.utility.SemanticType
import cn.enaium.jimmer.buddy.lsp.utility.process
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

/**
 * @author Enaium
 */
class BuddyServer : LanguageServer {

    val project = Project()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        return CoroutineScope(Dispatchers.Default).future {
            params.rootUri?.also {
                project.environment.directories.add(URI.create(it).toPath())
            }

            CoroutineScope(Dispatchers.Default).launch {
                process("Sync project") {
                    project.process()
                    client?.registerCapability(
                        RegistrationParams(
                            listOf(
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/semanticTokens",
                                    SemanticTokensWithRegistrationOptions().apply {
                                        legend = SemanticTokensLegend().apply {
                                            tokenTypes = SemanticType.entries.map { it.type }
                                        }
                                        setFull(true)
                                    }
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/foldingRange",
                                    FoldingRangeProviderOptions()
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/completion",
                                    CompletionOptions().apply {
                                        triggerCharacters = listOf("*", "@", "#")
                                        completionItem = CompletionItemOptions().apply {
                                            labelDetailsSupport = true
                                        }
                                    }
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/formatting",
                                    DocumentFormattingRegistrationOptions()
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/hover",
                                    HoverRegistrationOptions()
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/codeLens",
                                    CodeLensOptions().apply {
                                        resolveProvider = true
                                    }
                                ),
                                Registration(
                                    UUID.randomUUID().toString(),
                                    "textDocument/definition",
                                    DefinitionOptions()
                                )
                            )
                        )
                    )
                    client?.refreshSemanticTokens()
                    client?.refreshCodeLenses()
                    client?.refreshFoldingRanges()
                }

                process("Gen source and dto") {
                    project.environment.modules.forEach { module ->
                        module.sourceDirectories.forEach { sourceDir ->
                            val sourceDirectory = sourceDir.relativeTo(module.directory)
                            sourceDirectory.subpath(0, 1).name == module.buildDirectory.name && return@forEach
                            val main = sourceDirectory.subpath(1, 2).name
                            val language = sourceDirectory.subpath(2, 3).name
                            val classes =
                                project.environment.findClasses(sourceDir).toSet().takeIf { it.isNotEmpty() }
                                    ?: return@forEach
                            if (project.environment.isKotlinProject) {
                                val kspGen = KspGen(
                                    module.directory,
                                    project.environment,
                                    module.buildDirectory / "generated/ksp" / main / language,
                                    emptyMap()
                                )
                                kspGen.sourceProcess(classes)
                                kspGen.dtoPathProcess((sourceDir.parent / "dto").walk().filter { it.extension == "dto" }
                                    .toSet())
                            } else if (project.environment.isJavaProject) {
                                val aptGen = AptGen(
                                    module.directory,
                                    project.environment,
                                    module.buildDirectory / "generated/sources/annotationProcessor" / language / main,
                                    emptyMap()
                                )
                                aptGen.sourceProcess(classes)
                                aptGen.dtoPathProcess((sourceDir.parent / "dto").walk().filter { it.extension == "dto" }
                                    .toSet())
                            }
                        }
                    }
                }
            }

            InitializeResult(ServerCapabilities().apply {
                setTextDocumentSync(TextDocumentSyncOptions().apply {
                    openClose = true
                    change = TextDocumentSyncKind.Full
                    setSave(SaveOptions().apply {
                        includeText = true
                    })
                })
            })
        }
    }

    override fun shutdown(): CompletableFuture<Any> {
        project.exit()
        return CompletableFuture.completedFuture(true)
    }

    override fun exit() {

    }

    override fun getTextDocumentService(): TextDocumentService {
        return BuddyTextDocumentService(project)
    }

    override fun getWorkspaceService(): WorkspaceService {
        return BuddyWorkspaceService(project)
    }

    override fun setTrace(params: SetTraceParams) {
    }
}