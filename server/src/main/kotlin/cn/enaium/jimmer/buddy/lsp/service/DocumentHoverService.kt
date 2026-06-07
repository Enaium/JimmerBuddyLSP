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
import cn.enaium.jimmer.buddy.dto.lang.ImmutableProp
import cn.enaium.jimmer.buddy.dto.lang.utility.findPropTrace
import cn.enaium.jimmer.buddy.dto.lang.utility.type
import cn.enaium.jimmer.buddy.lang.parser.utility.findParent
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentHoverService(val project: Project, val documentManager: DocumentManager) : DocumentServiceAdapter() {
    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val document =
            documentManager.getDocument(params.textDocument.uri) as? DtoDocument
                ?: return CompletableFuture.completedFuture(null)
        return CoroutineScope(Dispatchers.IO).future {
            val dtoProcessor = DtoProcessor(document.content)
            dtoProcessor.findCursor(params.position.line, params.position.character)?.also { cursor ->
                cursor.findParent<DtoParser.ExportStatementContext>()?.also { exportStatement ->
                    return@future Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                        ## Export
                        `${exportStatement.typeParts().qualifiedName().parts.joinToString(".") { it.text }}`
                        ## Package
                        `${exportStatement.packageParts().qualifiedName().parts.joinToString(".") { it.text }}`
                    """.trimIndent(),
                        )
                    )
                }

                cursor.findParent<DtoParser.ImportStatementContext>()?.also { importStatement ->
                    val qualifiedName = importStatement.qualifiedName()
                    val types = if (importStatement.importedType().isEmpty()) {
                        "`${qualifiedName.parts.last().text}`"
                    } else {
                        importStatement.importedType().joinToString(", ") { "`${it.text}`" }
                    }
                    val packageName = if (importStatement.importedType().isEmpty()) {
                        qualifiedName.parts.subList(0, qualifiedName.parts.size - 1).joinToString(".") { it.text }
                    } else {
                        qualifiedName.parts.joinToString(".") { it.text }
                    }

                    return@future Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                            ## Import
                            `$packageName`
                            ## Types
                            $types
                        """.trimIndent()
                        ),
                    )
                }

                (cursor as? DtoParser.PositivePropContext)?.also { positivePropContext ->
                    val name = positivePropContext.prop.text
                    val trace = cursor.findPropTrace()
                    val immutableType = dtoProcessor.findType(
                        project.environment,
                        trace,
                        document.type ?: return@also
                    ) ?: return@also
                    val pattern = positivePropContext.findParent<DtoParser.AliasPatternContext>()
                    return@future Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                        ## $name ${pattern?.pattern(name) ?: positivePropContext.alias()?.name?.let { "`${it.text}`" } ?: ""}
                        Trace: `${(listOf("this") + trace).joinToString(".")}`
                        
                        From: `${immutableType.name}`
                        
                        Type: `${immutableType.properties.values.find { it.name == name }?.type()?.description}`
                        
                  """.trimIndent()
                        )
                    )
                }

                cursor.findParent<DtoParser.MacroContext>()?.also { macroContext ->
                    val immutableType = dtoProcessor.findType(
                        project.environment,
                        cursor.findPropTrace(),
                        document.type ?: return@also
                    ) ?: return@also
                    val pattern = macroContext.findParent<DtoParser.AliasPatternContext>()
                    return@future Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                                ## ${macroContext.name.text}
                                ${
                                when (macroContext.name.text) {
                                    "allScalars" -> {
                                        val results = mutableListOf<ImmutableProp>()
                                        if (macroContext.args.isEmpty()) {
                                            results.addAll(immutableType.properties.values)
                                        } else {
                                            macroContext.args.forEach { arg ->
                                                when (arg.text) {
                                                    "this", document.type.name -> results.addAll(immutableType.declaredProperties.values)
                                                    else -> {
                                                        immutableType.superTypes.find { it.name == arg.text }?.declaredProperties?.values?.also {
                                                            results.addAll(it)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        results.filter { isAutoScalar(it) }
                                    }

                                    "allReferences" -> immutableType.properties.values.filter { isAutoReference(it) }
                                    else -> emptyList()
                                }.joinToString {
                                    "`${
                                        pattern?.let { pattern ->
                                            pattern.pattern(it.name)
                                        } ?: it.name
                                    }`"
                                }
                            }
                            """.trimIndent()
                        )
                    )
                }
            }

            null
        }
    }

    private fun DtoParser.AliasPatternContext.pattern(name: String): String {
        val original = this.original
        val replace = this.replacement?.text ?: ""
        return if (this.prefix != null) {
            if (original == null) {
                "`${replace}${name.replaceFirstChar { it.uppercase() }}`"
            } else {
                "`${
                    name.replaceFirst(
                        original.text,
                        replace
                    )
                }`"
            }
        } else if (this.suffix != null) {
            if (original == null) {
                "`${name}${replace}`"
            } else {
                "`${
                    name.replaceFirst(
                        original.text,
                        replace
                    )
                }`"
            }
        } else {
            if (original != null) {
                "`${name.replaceFirst(original.text, replace)}`"
            } else {
                ""
            }
        }
    }

    private fun isAutoReference(baseProp: ImmutableProp): Boolean {
        return baseProp.isAssociation(true) && !baseProp.isList && !baseProp.isTransient
    }

    private fun isAutoScalar(baseProp: ImmutableProp): Boolean {
        return !baseProp.isFormula &&
                !baseProp.isTransient &&
                !baseProp.isIdView &&
                !baseProp.isManyToManyView &&
                !baseProp.isList &&
                !baseProp.isAssociation(true) &&
                !baseProp.isLogicalDeleted &&
                !baseProp.isExcludedFromAllScalars
    }
}