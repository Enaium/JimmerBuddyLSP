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

import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.lsp.utility.range
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentCodeLensService(val project: Project, val documentManager: DocumentManager) : DocumentServiceAdapter() {
    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        return CoroutineScope(Dispatchers.Default).future {
            val codeLens = mutableListOf<CodeLens>()
            val document = documentManager.getDocument(params.textDocument.uri) as? DtoDocument
                ?: return@future codeLens

            val packageName = document.cst.exportStatement()?.packageParts()?.qualifiedName()?.text
                ?: document.type?.packageName?.let { "$it.dto" }
                ?: return@future codeLens

            val buildDirectory = project.environment.modules.sortedByDescending { it.directory.nameCount }.find {
                URI.create(params.textDocument.uri).toPath().startsWith(it.directory)
            }?.buildDirectory ?: return@future codeLens

            if (project.environment.isKotlinProject) {

                (buildDirectory / "generated/ksp").takeIf { it.exists() }?.listDirectoryEntries()?.forEach { entry ->
                    document.cst.dtoType().forEach { dtoType ->
                        val generated = entry / "kotlin/${packageName.replace('.', '/')}/${dtoType.name.text}.kt"
                        if (generated.exists()) {
                            codeLens.add(
                                CodeLens(
                                    dtoType.name.range(),
                                    Command("Generated", ""),
                                    null
                                )
                            )
                        }
                    }
                }
            } else if (project.environment.isJavaProject) {
                (buildDirectory / "generated/sources/annotationProcessor/java").takeIf { it.exists() }
                    ?.listDirectoryEntries()?.forEach { entry ->
                        document.cst.dtoType().forEach { dtoType ->
                            val generated = entry / "${packageName.replace('.', '/')}/${dtoType.name.text}.java"
                            if (generated.exists()) {
                                codeLens.add(
                                    CodeLens(
                                        dtoType.name.range(),
                                        Command("Generated", ""),
                                        null
                                    )
                                )
                            }
                        }
                    }
            }
            codeLens
        }
    }
}