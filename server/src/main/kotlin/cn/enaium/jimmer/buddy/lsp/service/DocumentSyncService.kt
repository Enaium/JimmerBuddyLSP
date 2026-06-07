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
import cn.enaium.jimmer.buddy.lsp.utility.DelayedExecutionQueue
import cn.enaium.jimmer.buddy.project.structure.Project
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * @author Enaium
 */
abstract class DocumentSyncService(val project: Project, val documentManager: DocumentManager) :
    DocumentServiceAdapter() {

    val deq = DelayedExecutionQueue()

    override fun didOpen(params: DidOpenTextDocumentParams) {
        try {
            project.environment.getIndex()
        } catch (e: Throwable) {
            return
        }
        val content = params.textDocument.text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.OPEN)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        try {
            project.environment.getIndex()
        } catch (e: Throwable) {
            return
        }
        val content = params.contentChanges[0].text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.CHANGE)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {

    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        try {
            project.environment.getIndex()
        } catch (e: Throwable) {
            return
        }
        val content = params.text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.SAVE)
    }

    abstract fun validate(content: String, uri: String, type: Type)

    enum class Type {
        OPEN, CHANGE, CLOSE, SAVE
    }

    fun getGenDirectory(path: Path): Path? {
        val module =
            project.environment.modules.sortedByDescending { it.directory.nameCount }
                .find { path.startsWith(it.directory) }
                ?: return null
        val sourceDirectory =
            module.sourceDirectories.find { path.startsWith(it.parent) }?.relativeTo(module.directory)
                ?: return null
        val buildDirectory = module.buildDirectory
        val main = sourceDirectory.subpath(1, 2).name
        return when {
            project.environment.isKotlinProject -> {
                buildDirectory / "generated/ksp" / main / "kotlin"
            }

            project.environment.isJavaProject -> {
                buildDirectory / "generated/sources/annotationProcessor/java" / main
            }

            else -> null
        }
    }
}