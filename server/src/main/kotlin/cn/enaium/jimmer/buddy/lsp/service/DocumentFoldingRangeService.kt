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
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.lsp.utility.range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Range
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentFoldingRangeService(val documentManager: DocumentManager) : DocumentServiceAdapter() {
    private val ranges = mutableListOf<Range>()

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        ranges.clear()
        val path = URI.create(params.textDocument.uri).toPath()
        path.extension != "dto" && return CompletableFuture.completedFuture(emptyList())
        val document = documentManager.getDocument(params.textDocument.uri) as? DtoDocument
            ?: return CompletableFuture.completedFuture(emptyList<FoldingRange>())
        return CoroutineScope(Dispatchers.Default).future {
            dto(document.cst)
            ranges.map {
                FoldingRange(it.start.line, it.end.line - 1).apply {
                    kind = FoldingRangeKind.Region
                }
            }
        }
    }

    private fun positiveProp(positivePropContext: DtoParser.PositivePropContext) {
        positivePropContext.dtoBody()?.also { dtoBody ->
            ranges.add(dtoBody.range())
            dtoBody.explicitProp()?.takeIf { it.isNotEmpty() }?.forEach { explicitProp ->
                explicitProp(explicitProp)
            }
        }
    }

    private fun explicitProp(explicitPropContext: DtoParser.ExplicitPropContext) {
        explicitPropContext.positiveProp()?.also {
            positiveProp(it)
        }
        explicitPropContext.aliasGroup()?.aliasGroupBody()?.also {
            ranges.add(it.range())
            it.positiveProp()?.takeIf { it.isNotEmpty() }?.forEach { positiveProp ->
                positiveProp(positiveProp)
            }
        }
    }

    private fun dto(ast: DtoParser.DtoContext): List<Range> {
        ast.dtoType().forEach { dtoType ->
            dtoType.dtoBody()?.also { dtoTypeBody ->
                ranges.add(dtoTypeBody.range())
                dtoTypeBody.explicitProp()?.takeIf { it.isNotEmpty() }?.forEach { explicitProp ->
                    explicitProp(explicitProp)
                }
            }
        }
        return ranges
    }
}