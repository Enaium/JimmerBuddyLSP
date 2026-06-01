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

import cn.enaium.jimmer.buddy.dto.lang.DtoParser
import cn.enaium.jimmer.buddy.dto.lang.DtoProcessor
import cn.enaium.jimmer.buddy.dto.lang.utility.PropType
import cn.enaium.jimmer.buddy.dto.lang.utility.findPropTrace
import cn.enaium.jimmer.buddy.dto.lang.utility.type
import cn.enaium.jimmer.buddy.lang.parser.node.AnnotationClassNode
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentCompletionService(val project: Project, val documentManager: DocumentManager) : DocumentServiceAdapter() {
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val path = URI.create(params.textDocument.uri).toPath()
        path.extension != "dto" && return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        val document = documentManager.getDocument(params.textDocument.uri) as? DtoDocument
            ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        return CoroutineScope(Dispatchers.Default).future {

            val triggerChar = params.context?.triggerCharacter

            return@future when (triggerChar) {
                "*" -> params.star(document)
                "@" -> params.at(document)
                "#" -> params.hash(document)
                null -> params.type(document)
                else -> null
            }?.let { Either.forLeft(it) } ?: Either.forLeft(emptyList())
        }
    }

    fun CompletionParams.star(document: DtoDocument): List<CompletionItem> {
        val position = this.position
        position.character < 3 && return emptyList()
        val line = document.content.split("\n")[position.line]
        val before3Chars = line.substring(position.character - 3, this.position.character)
        before3Chars != "/**" && return emptyList()
        return listOf(CompletionItem("DocComment").apply {
            insertText = "\n * $0 \n */"
            kind = CompletionItemKind.Text
            insertTextFormat = InsertTextFormat.Snippet
        })
    }

    fun CompletionParams.at(document: DtoDocument): List<CompletionItem> {
        var sort = 0
        val annotationClassNodes = project.environment.classes.values.filterIsInstance<AnnotationClassNode>()
        return annotationClassNodes.map { annotationClassNode ->
            CompletionItem(annotationClassNode.let {
                if (annotationClassNode.qualifiedName.contains(".")) {
                    annotationClassNode.qualifiedName.substringAfterLast(".")
                } else {
                    annotationClassNode.qualifiedName
                }
            }).apply {
                kind = CompletionItemKind.Class
                sortText = "${sort++}"
                labelDetails = CompletionItemLabelDetails().apply {
                    detail = " (from ${annotationClassNode.qualifiedName})"
                }
                val sortedImportStatements =
                    document.cst.importStatement()
                        .sortedWith { o1, o2 -> o2.IMPORT().symbol.line - o1.IMPORT().symbol.line }
                val exportStatement = document.cst.exportStatement()
                var importLine = if (exportStatement != null) {
                    val exportLine =
                        if (exportStatement.typeParts().qualifiedName().parts.isNotEmpty()) {
                            exportStatement.packageParts().qualifiedName().parts.last().line
                        } else if (exportStatement.typeParts().qualifiedName().parts.isNotEmpty()) {
                            exportStatement.typeParts().qualifiedName().parts.last().line
                        } else {
                            exportStatement.EXPORT().symbol.line
                        }
                    Range(Position(exportLine, 0), Position(exportLine, 0))
                } else {
                    Range(Position(0, 0), Position(0, 0))
                }
                if (sortedImportStatements.isNotEmpty()) {
                    val lastImportLine = sortedImportStatements.first().IMPORT().symbol.line
                    importLine = Range(Position(lastImportLine, 0), Position(lastImportLine, 0))
                }

                if (sortedImportStatements.none { importStatement ->
                        val joinToString =
                            importStatement.qualifiedName().parts.joinToString(".") { token -> token.text }
                        joinToString == annotationClassNode.qualifiedName || (annotationClassNode.qualifiedName.startsWith(
                            joinToString
                        ) && importStatement.importedType()
                            .map { type -> type.text }
                            .contains(annotationClassNode.qualifiedName.substring(joinToString.length + 1)))
                    }) {
                    additionalTextEdits = listOf(
                        TextEdit(importLine, "import ${annotationClassNode.qualifiedName}\n")
                    )
                }
            }
        }
    }

    fun CompletionParams.hash(document: DtoDocument): List<CompletionItem> {
        return listOf("allScalars", "allReferences").map { name ->
            CompletionItem(name).apply {
                kind = CompletionItemKind.Function
                labelDetails = CompletionItemLabelDetails().apply {
                    description = "macro"
                }
                insertText = "#$name"
                insertTextFormat = InsertTextFormat.Snippet
            }
        }
    }

    fun CompletionParams.type(document: DtoDocument): List<CompletionItem> {
        return props(document)
    }

    fun CompletionParams.props(document: DtoDocument): List<CompletionItem> {
        val dtoProcessor = DtoProcessor(document.content)
        val findCursor = dtoProcessor.findCursor(
            this.position.line,
            this.position.character
        )

        if (findCursor !is DtoParser.PositivePropContext) {
            return emptyList()
        }

        return dtoProcessor.findProps(
            project.environment.classes,
            document.type ?: return emptyList(),
            findCursor.findPropTrace()
        ).mapNotNull { memberNode ->
            val prop =
                project.environment.classes[memberNode.className]?.let { document.context.ofType(it.qualifiedName) }
                    ?.properties?.values
                    ?.find { it.name == memberNode.name } ?: return@mapNotNull null
            CompletionItem(memberNode.name).apply {
                kind = CompletionItemKind.Property
                val type = prop.type()
                labelDetails = CompletionItemLabelDetails().apply {
                    detail = "(from ${prop.declaringType.name})"
                    description = type.description
                }
                if (type == PropType.ASSOCIATION) {
                    insertText = "${prop.name} { \n\t$0\n}"
                    insertTextFormat = InsertTextFormat.Snippet
                } else if (type == PropType.RECURSIVE) {
                    insertText = "${prop.name}*"
                }
            }
        }
    }
}