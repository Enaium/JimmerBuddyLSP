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

package cn.enaium.jimmer.buddy.lsp.service.sync

import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.service.DocumentServiceAdapter
import cn.enaium.jimmer.buddy.lsp.service.sync.AbstractDocumentSyncService.Type
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams

/**
 * @author Enaium
 */
class DocumentSyncService(val project: Project, val documentManager: DocumentManager) : DocumentServiceAdapter() {

    private val services = listOf(
        JavaDocumentSyncService(project, documentManager),
        KotlinDocumentSyncService(project, documentManager),
        DtoDocumentSyncService(project, documentManager),
    )

    override fun didOpen(params: DidOpenTextDocumentParams) {
        try {
            project.environment.getIndex()
        } catch (e: Throwable) {
            return
        }
        val content = params.textDocument.text
        content.isBlank() && return
        CoroutineScope(Dispatchers.Default).launch {
            validate(content, params.textDocument.uri, Type.OPEN)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        try {
            project.environment.getIndex()
        } catch (e: Throwable) {
            return
        }
        val content = params.contentChanges[0].text
        content.isBlank() && return
        CoroutineScope(Dispatchers.Default).launch {
            validate(content, params.textDocument.uri, Type.CHANGE)
        }
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
        CoroutineScope(Dispatchers.Default).launch {
            validate(content, params.textDocument.uri, Type.SAVE)
        }
    }

    suspend fun validate(content: String, uri: String, type: Type) {
        services.forEach { it.validate(content, uri, type) }
    }
}