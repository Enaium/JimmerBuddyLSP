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
import cn.enaium.jimmer.buddy.lang.parser.utility.findParent
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.lsp.utility.copy
import cn.enaium.jimmer.buddy.lsp.utility.overlaps
import cn.enaium.jimmer.buddy.lsp.utility.range
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentDefinitionService(val project: Project, val documentManager: DocumentManager) : DocumentServiceAdapter() {
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return CoroutineScope(Dispatchers.Default).future {
            val locations = mutableListOf<Location>()
            val document = documentManager.getDocument(params.textDocument.uri) as? DtoDocument
                ?: return@future Either.forLeft(locations)


            val dtoProcessor = DtoProcessor(document.content)
            val findCursor = dtoProcessor.findCursor(params.position.line, params.position.character)

            findCursor?.findParent<DtoParser.TypePartsContext>()?.qualifiedName()?.parts?.lastOrNull()?.also {
                if (it.text == document.type?.name) {
                    val range = it.range()
                    if (range.overlaps(params.position)) {
                        locations.add(Location(document.type.classNode.path.toUri().toString(), range))
                    }
                }
            } ?: findCursor?.findParent<DtoParser.ImportStatementContext>()?.also {
                it.importedType().isNotEmpty() && return@also
                val qualifiedName = it.qualifiedName()
                val range = qualifiedName.parts.lastOrNull()?.range() ?: return@also
                if (range.overlaps(params.position)) {
                    project.environment.getIndex().findClass(qualifiedName.text)?.also {
                        locations.add(Location(it.path.toUri().copy("jar").toString(), range))
                    }
                }
            } ?: findCursor?.findParent<DtoParser.ImportedTypeContext>()?.also {
                val qualifiedName = it.findParent<DtoParser.ImportStatementContext>()?.qualifiedName() ?: return@also
                val range = it.name.range()
                if (range.overlaps(params.position)) {
                    project.environment.getIndex().findClass("${qualifiedName.text}.${it.name}")?.also {
                        locations.add(Location(it.path.toUri().toString(), range))
                    }
                }
            } ?: findCursor?.findParent<DtoParser.DtoTypeContext>()?.name?.also {
                val range = it.range()
                if (range.overlaps(params.position)) {
                    val packageName = document.cst.exportStatement()?.packageParts()?.qualifiedName()?.text
                        ?: document.type?.packageName?.let { "$it.dto" }
                        ?: return@also

                    val buildDirectory =
                        project.environment.modules.sortedByDescending { it.directory.nameCount }.find {
                            URI.create(params.textDocument.uri).toPath().startsWith(it.directory)
                        }?.buildDirectory ?: return@also

                    if (project.environment.isKotlinProject) {
                        (buildDirectory / "generated/ksp").takeIf { it.exists() }?.listDirectoryEntries()
                            ?.forEach { entry ->
                                document.cst.dtoType().forEach { dtoType ->
                                    val generated =
                                        entry / "kotlin/${packageName.replace('.', '/')}/${dtoType.name.text}.kt"
                                    if (generated.exists()) {
                                        locations.add(Location(generated.toUri().toString(), range))
                                    }
                                }
                            }
                    } else if (project.environment.isJavaProject) {
                        (buildDirectory / "generated/sources/annotationProcessor/java").takeIf { it.exists() }
                            ?.listDirectoryEntries()?.forEach { entry ->
                                document.cst.dtoType().forEach { dtoType ->
                                    val generated = entry / "${packageName.replace('.', '/')}/${dtoType.name.text}.java"
                                    if (generated.exists()) {
                                        locations.add(Location(generated.toUri().toString(), range))
                                    }
                                }
                            }
                    }
                }
            }

            return@future Either.forLeft(locations)
        }
    }
}