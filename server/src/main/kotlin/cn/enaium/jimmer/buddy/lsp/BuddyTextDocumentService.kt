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

import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.service.DocumentCompletionService
import cn.enaium.jimmer.buddy.lsp.service.DocumentFoldingRangeService
import cn.enaium.jimmer.buddy.lsp.service.DocumentFormattingService
import cn.enaium.jimmer.buddy.lsp.service.DocumentHoverService
import cn.enaium.jimmer.buddy.lsp.service.DocumentSemanticTokensFullService
import cn.enaium.jimmer.buddy.lsp.service.DtoDocumentSyncService
import cn.enaium.jimmer.buddy.lsp.service.JavaDocumentSyncService
import cn.enaium.jimmer.buddy.lsp.service.KotlinDocumentSyncService
import cn.enaium.jimmer.buddy.project.structure.Project
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class BuddyTextDocumentService(project: Project) : TextDocumentService {
    private val documentManager = DocumentManager()
    private val documentCompletionService = DocumentCompletionService(project, documentManager)
    private val documentFoldingRangeService = DocumentFoldingRangeService(documentManager)
    private val documentSemanticTokensFullService = DocumentSemanticTokensFullService(documentManager)
    private val documentSyncServices =
        listOf(
            JavaDocumentSyncService(project, documentManager),
            KotlinDocumentSyncService(project, documentManager),
            DtoDocumentSyncService(project, documentManager)
        )
    private val documentFormattingService = DocumentFormattingService(documentManager)
    private val documentHoverService = DocumentHoverService(project, documentManager)

    override fun didOpen(params: DidOpenTextDocumentParams) {
        documentSyncServices.forEach { it.didOpen(params) }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        documentSyncServices.forEach { it.didChange(params) }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentSyncServices.forEach { it.didClose(params) }
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        documentSyncServices.forEach { it.didSave(params) }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        return documentSemanticTokensFullService.semanticTokensFull(params)
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        return documentFoldingRangeService.foldingRange(params)
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return documentCompletionService.completion(params)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        return documentFormattingService.formatting(params)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return documentHoverService.hover(params)
    }
}