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

package cn.enaium.jimmer.buddy.lsp.service

import cn.enaium.jimmer.buddy.codegen.gen.AptGen
import cn.enaium.jimmer.buddy.codegen.gen.KspGen
import cn.enaium.jimmer.buddy.codegen.utility.toDtoFile
import cn.enaium.jimmer.buddy.dto.lang.Context
import cn.enaium.jimmer.buddy.dto.lang.DocumentDtoCompiler
import cn.enaium.jimmer.buddy.dto.lang.DtoLexer
import cn.enaium.jimmer.buddy.dto.lang.DtoParser
import cn.enaium.jimmer.buddy.dto.lang.DtoProcessor
import cn.enaium.jimmer.buddy.dto.lang.ImmutableType
import cn.enaium.jimmer.buddy.lsp.client
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.lsp.exception.DiagnosticException
import cn.enaium.jimmer.buddy.lsp.utility.findProjectDir
import cn.enaium.jimmer.buddy.project.structure.Project
import org.antlr.v4.runtime.*
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.OsFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.io.Reader
import java.net.URI
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DtoDocumentSyncService(project: Project, documentManager: DocumentManager) :
    DocumentSyncService(project, documentManager) {

    override fun validate(
        content: String,
        uri: String,
        type: Type
    ) {
        val path = URI.create(uri).toPath()
        path.extension != "dto" && return

        val context = Context(project)
        val baseErrorListener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = listOf(Diagnostic().apply {
                        range =
                            Range(Position(line - 1, charPositionInLine), Position(line - 1, charPositionInLine + 1))
                        severity = DiagnosticSeverity.Error
                        message = Either.forLeft(msg)
                    })
                })
            }
        }

        val token = CommonTokenStream(DtoLexer(CharStreams.fromString(content)))
        val cst = DtoParser(token).apply {
            removeErrorListeners()
            addErrorListener(baseErrorListener)
        }.dto()

        var immutableType: ImmutableType? = null
        try {
            val documentDtoCompiler =
                DocumentDtoCompiler(findProjectDir(path, project.environment.directories)?.let { path.toDtoFile(it) }
                    ?: throw DiagnosticException("Could not find project directory"))
            documentDtoCompiler.compile(
                context.ofType(documentDtoCompiler.sourceTypeName)?.also {
                    immutableType = it
                }
                    ?: throw DiagnosticException("No immutable type '${documentDtoCompiler.sourceTypeName}' found. Please use the export statement.")
            )
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = emptyList()
            })

            when (type) {
                Type.CHANGE -> {
                    deq.schedule("genDto", 2000) {
                        val module =
                            project.environment.modules.find { path.startsWith(it.directory) } ?: return@schedule
                        val genDir = getGenDirectory(path) ?: return@schedule

                        val dtoFile = DtoFile(
                            object : OsFile {
                                override fun getAbsolutePath(): String {
                                    return path.absolutePathString()
                                }

                                override fun openReader(): Reader {
                                    return content.reader()
                                }
                            },
                            module.directory.name,
                            path.parent.relativeTo(module.directory).joinToString("/"),
                            emptyList(),
                            path.name
                        )

                        when {
                            project.environment.isKotlinProject -> {
                                KspGen(module.directory, project.environment, genDir, emptyMap()).dtoFileProcess(
                                    setOf(
                                        dtoFile
                                    )
                                )
                            }

                            project.environment.isJavaProject -> {
                                AptGen(module.directory, project.environment, genDir, emptyMap()).dtoFileProcess(
                                    setOf(
                                        dtoFile
                                    )
                                )
                            }
                        }
                    }
                }

                else -> {}
            }
        } catch (dtoAst: DtoAstException) {
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = listOf(Diagnostic().apply {
                    range = Range(
                        Position(dtoAst.lineNumber - 1, dtoAst.colNumber),
                        Position(dtoAst.lineNumber - 1, dtoAst.colNumber + 1)
                    )
                    severity = DiagnosticSeverity.Error
                    message = Either.forLeft(dtoAst.message)
                })
            })
        } catch (e: DiagnosticException) {
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = listOf(Diagnostic().apply {
                    range = Range(Position(0, 0), Position(0, 1))
                    severity = DiagnosticSeverity.Error
                    message = Either.forLeft(e.message)
                })
            })
        }

        when (type) {
            Type.OPEN, Type.CHANGE, Type.SAVE -> {
                documentManager.openOrUpdateDocument(
                    uri, DtoDocument(content, context, immutableType, token, cst)
                )
            }

            Type.CLOSE -> documentManager.closeDocument(uri)
        }
    }
}