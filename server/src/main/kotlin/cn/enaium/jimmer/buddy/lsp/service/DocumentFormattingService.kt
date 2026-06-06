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

import cn.enaium.jimmer.buddy.dto.lang.DtoLexer
import cn.enaium.jimmer.buddy.dto.lang.DtoParser
import cn.enaium.jimmer.buddy.formatter.Formatter
import cn.enaium.jimmer.buddy.formatter.SpaceBuilder
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.DtoDocument
import cn.enaium.jimmer.buddy.lsp.utility.position
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentFormattingService(val documentManager: DocumentManager) : DocumentServiceAdapter() {
    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        val path = URI.create(params.textDocument.uri).toPath()
        path.extension != "dto" && return CompletableFuture.completedFuture(emptyList())
        val document = documentManager.getDocument(params.textDocument.uri) as? DtoDocument
            ?: return CompletableFuture.completedFuture(emptyList())

        val tokens = document.token.tokens
        val cst = document.cst

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        fun collectRuleInstances(ctx: ParserRuleContext) {
            val ruleIndex = ctx.ruleIndex
            if (ruleIndex >= 0) {
                ruleInstances.computeIfAbsent(ruleIndex) { mutableListOf() }
                    .add(ctx.start.tokenIndex..ctx.stop.tokenIndex)
            }
            for (i in 0 until ctx.childCount) {
                val child = ctx.getChild(i)
                if (child is ParserRuleContext) {
                    collectRuleInstances(child)
                }
            }
        }
        collectRuleInstances(cst)

        val formatted = Formatter(tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.DOT, 0)
                .around(DtoLexer.COMMA, 0, 1)
                .around(DtoLexer.COLON, 0, 1)
                .around(DtoLexer.SEMICOLON, 0, 1)
                .around(DtoLexer.RIGHT_ARROW, 1)
                .around(DtoLexer.EQUAL, 1)
                .around(DtoLexer.LEFT_PARENTHESIS, 0)
                .around(DtoLexer.RIGHT_PARENTHESIS, 0)
                .around(DtoLexer.AT, 0)
                .around(DtoLexer.HASH, 0)
                .around(DtoLexer.AS, 0)
                .between(DtoLexer.RIGHT_ARROW, DtoLexer.PACKAGE, 1)
                .between(DtoLexer.AS, DtoLexer.LEFT_PARENTHESIS, 0)
                .ruleAround(DtoParser.RULE_explicitProp, 0)
                .ruleAround(DtoParser.RULE_macro, 0)
                .tokenAndRuleBetween(DtoLexer.Identifier, DtoParser.RULE_dtoBody, 1)
                .tokenAndRuleBetween(DtoLexer.EXPORT, DtoParser.RULE_typeParts, 1)
                .tokenAndRuleBetween(DtoLexer.PACKAGE, DtoParser.RULE_packageParts, 1)
                .ruleAndRuleBetween(DtoParser.RULE_explicitProp, DtoParser.RULE_dtoBody, 1)
                .indent(DtoParser.RULE_dtoBody)
                .indent(DtoParser.RULE_aliasGroupBody)
                .indent(DtoParser.RULE_enumBody),
            ruleInstances = ruleInstances
        )

        return CompletableFuture.completedFuture(
            listOf(
                TextEdit(
                    Range(
                        tokens.first().position(),
                        tokens.last().position()
                    ),
                    formatted
                )
            )
        )
    }
}
