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

package cn.enaium.jimmer.buddy.lsp.service.completion

import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.service.DocumentServiceAdapter
import cn.enaium.jimmer.buddy.project.structure.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentCompletionService(val project: Project, val documentManager: DocumentManager) :
    DocumentServiceAdapter() {

    private val services = listOf(
        JavaDocumentCompletionService(project, documentManager),
        KotlinDocumentCompletionService(project, documentManager),
        DtoDocumentCompletionService(project, documentManager),
    )

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CoroutineScope(Dispatchers.Default).future {
            return@future Either.forLeft(services.flatMap { it.completion(params) })
        }
    }
}