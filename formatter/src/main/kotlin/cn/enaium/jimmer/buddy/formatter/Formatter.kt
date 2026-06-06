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

package cn.enaium.jimmer.buddy.formatter

import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.Token

/**
 * @author Enaium
 */
class Formatter(private val tokens: List<Token>) {
    private val result = tokens.toMutableList()

    fun process(
        builder: SpaceBuilder,
        ruleInstances: Map<Int, List<IntRange>>? = null
    ): String {
        val snapshot = tokens.toList()

        val indentModels = mutableListOf<SpaceBuilder.Indent>()
        builder.build().forEach { model ->
            when (model) {
                is SpaceBuilder.Around -> processAround(snapshot, model, builder.spaceToken)
                is SpaceBuilder.Between -> processBetween(snapshot, model, builder.spaceToken)
                is SpaceBuilder.RuleAround -> {
                    ruleInstances?.let { processRuleAround(snapshot, model, builder.spaceToken, it) }
                }
                is SpaceBuilder.RuleBetween -> {
                    ruleInstances?.let { processRuleBetween(snapshot, model, builder.spaceToken, it) }
                }
                is SpaceBuilder.TokenAndRuleBetween -> {
                    ruleInstances?.let { processTokenAndRuleBetween(snapshot, model, builder.spaceToken, it) }
                }
                is SpaceBuilder.RuleAndRuleBetween -> {
                    ruleInstances?.let { processRuleAndRuleBetween(snapshot, model, builder.spaceToken, it) }
                }
                is SpaceBuilder.Indent -> indentModels.add(model)
            }
        }

        val text = result.filter { it.type != Token.EOF }.joinToString("") { it.text }

        return if (indentModels.isNotEmpty() && ruleInstances != null) {
            applyIndentation(text, indentModels, ruleInstances)
        } else {
            text
        }
    }

    private fun processAround(snapshot: List<Token>, model: SpaceBuilder.Around, spaceToken: Int) {
        for (token in snapshot) {
            if (token.type != model.token) continue
            model.beforeSpaceCount?.let { adjustBefore(snapshot, token, spaceToken, it) }
            model.afterSpaceCount?.let { adjustAfter(snapshot, token, spaceToken, it) }
        }
    }

    private fun processBetween(snapshot: List<Token>, model: SpaceBuilder.Between, spaceToken: Int) {
        val visible = snapshot.filter { it.type != spaceToken && it.type != Token.EOF }
        for (i in 0 until visible.size - 1) {
            val prev = visible[i]
            val next = visible[i + 1]
            if (prev.type == model.beforeToken && next.type == model.afterToken) {
                val count = countSpacesBetween(result, prev, next, spaceToken)
                adjustCount(result, prev, next, spaceToken, count, model.spaceCount)
            }
        }
    }

    private fun processRuleAround(
        snapshot: List<Token>,
        model: SpaceBuilder.RuleAround,
        spaceToken: Int,
        ruleInstances: Map<Int, List<IntRange>>
    ) {
        val instances = ruleInstances[model.ruleIndex] ?: return
        for (instance in instances) {
            val firstToken = snapshot.find { it.tokenIndex == instance.first } ?: continue
            val lastToken = snapshot.find { it.tokenIndex == instance.last } ?: continue
            model.beforeSpaceCount?.let { adjustBefore(snapshot, firstToken, spaceToken, it) }
            model.afterSpaceCount?.let { adjustAfter(snapshot, lastToken, spaceToken, it) }
        }
    }

    private fun processRuleBetween(
        snapshot: List<Token>,
        model: SpaceBuilder.RuleBetween,
        spaceToken: Int,
        ruleInstances: Map<Int, List<IntRange>>
    ) {
        val instances = ruleInstances[model.ruleIndex] ?: return
        val visible = snapshot.filter { it.type != spaceToken && it.type != Token.EOF }
        for (i in 0 until visible.size - 1) {
            val prev = visible[i]
            val next = visible[i + 1]
            if (prev.type == model.beforeToken && next.type == model.afterToken
                && instances.any { prev.tokenIndex in it && next.tokenIndex in it }
            ) {
                val count = countSpacesBetween(result, prev, next, spaceToken)
                adjustCount(result, prev, next, spaceToken, count, model.spaceCount)
            }
        }
    }

    private fun processTokenAndRuleBetween(
        snapshot: List<Token>,
        model: SpaceBuilder.TokenAndRuleBetween,
        spaceToken: Int,
        ruleInstances: Map<Int, List<IntRange>>
    ) {
        val instances = ruleInstances[model.ruleIndex] ?: return
        for (token in snapshot) {
            if (token.type != model.token) continue
            val ruleInstance = instances
                .filter { it.first > token.tokenIndex }
                .minByOrNull { it.first } ?: continue
            val ruleStart = snapshot.first { it.tokenIndex == ruleInstance.first }
            // Preserve cross-line spacing (indentation)
            if (token.line < ruleStart.line) continue
            val count = countSpacesBetween(result, token, ruleStart, spaceToken)
            if (count != model.spaceCount) {
                adjustCount(result, token, ruleStart, spaceToken, count, model.spaceCount)
            }
        }
    }

    private fun processRuleAndRuleBetween(
        snapshot: List<Token>,
        model: SpaceBuilder.RuleAndRuleBetween,
        spaceToken: Int,
        ruleInstances: Map<Int, List<IntRange>>
    ) {
        val beforeInstances = ruleInstances[model.ruleBefore] ?: return
        val afterInstances = ruleInstances[model.ruleAfter] ?: return
        for (beforeInstance in beforeInstances) {
            for (afterInstance in afterInstances) {
                val afterStart = afterInstance.first
                val beforeToken: Token?
                val afterToken: Token?
                if (beforeInstance.last < afterStart) {
                    // Non-overlapping: before ends before after starts
                    beforeToken = snapshot.find { it.tokenIndex == beforeInstance.last }
                    afterToken = snapshot.find { it.tokenIndex == afterStart }
                } else if (beforeInstance.first < afterStart) {
                    // Overlapping: after starts inside before — find the last meaningful
                    // token of beforeInstance that lies before afterStart
                    beforeToken = snapshot.asSequence()
                        .filter { it.tokenIndex in beforeInstance && it.tokenIndex < afterStart }
                        .filter { it.type != spaceToken && it.type != Token.EOF }
                        .maxByOrNull { it.tokenIndex }
                    afterToken = snapshot.find { it.tokenIndex == afterStart }
                } else {
                    continue
                }
                if (beforeToken == null || afterToken == null) continue
                if (beforeToken.line < afterToken.line) continue
                val count = countSpacesBetween(result, beforeToken, afterToken, spaceToken)
                if (count != model.spaceCount) {
                    adjustCount(result, beforeToken, afterToken, spaceToken, count, model.spaceCount)
                }
            }
        }
    }

    private fun adjustBefore(
        snapshot: List<Token>,
        target: Token,
        spaceToken: Int,
        desired: Int
    ) {
        val idx = snapshot.indexOf(target)
        if (idx <= 0) return

        var spaceCount = 0
        var prevNonSpace: Token? = null

        for (i in idx - 1 downTo 0) {
            val t = snapshot[i]
            if (t.type == spaceToken) {
                spaceCount++
            } else {
                prevNonSpace = t
                break
            }
        }

        val prev = prevNonSpace ?: return

        // Preserve indentation: spaces after a newline (different line) are indentation
        if (prev.line < target.line) return

        adjustCount(result, prev, target, spaceToken, spaceCount, desired)
    }

    private fun adjustAfter(
        snapshot: List<Token>,
        target: Token,
        spaceToken: Int,
        desired: Int
    ) {
        val idx = snapshot.indexOf(target)
        if (idx < 0 || idx >= snapshot.size - 1) return

        var spaceCount = 0
        var nextNonSpace: Token? = null

        for (i in idx + 1 until snapshot.size) {
            val t = snapshot[i]
            if (t.type == spaceToken) {
                spaceCount++
            } else {
                nextNonSpace = t
                break
            }
        }

        val next = nextNonSpace ?: return
        adjustCount(result, target, next, spaceToken, spaceCount, desired)
    }

    private fun countSpacesBetween(
        snapshot: List<Token>,
        before: Token,
        after: Token,
        spaceToken: Int
    ): Int {
        val start = snapshot.indexOf(before)
        val end = snapshot.indexOf(after)
        if (start < 0 || end < 0) return 0
        return (start + 1 until end).count { snapshot[it].type == spaceToken }
    }

    private fun adjustCount(
        result: MutableList<Token>,
        before: Token,
        after: Token,
        spaceToken: Int,
        current: Int,
        desired: Int
    ) {
        if (current == desired) return

        val iter = result.listIterator()
        var foundBefore = false
        val toRemove = current - desired
        var removed = 0

        while (iter.hasNext()) {
            val t = iter.next()
            if (t === before) {
                foundBefore = true
            } else if (foundBefore && t === after) {
                val toAdd = desired - current
                if (toAdd > 0) {
                    repeat(toAdd) {
                        iter.previous()
                        iter.add(CommonToken(spaceToken, " "))
                        iter.next()
                    }
                }
                break
            } else if (foundBefore && t.type == spaceToken && removed < toRemove) {
                iter.remove()
                removed++
            }
        }
    }

    private fun applyIndentation(
        text: String,
        indentModels: List<SpaceBuilder.Indent>,
        ruleInstances: Map<Int, List<IntRange>>
    ): String {
        val blockLineRanges = indentModels.flatMap { model ->
            ruleInstances[model.ruleIndex].orEmpty().mapNotNull { instance ->
                val startToken = tokens.find { it.tokenIndex == instance.first } ?: return@mapNotNull null
                val endToken = tokens.find { it.tokenIndex == instance.last } ?: return@mapNotNull null
                startToken.line to endToken.line
            }
        }

        val lines = text.split("\n")
        return lines.mapIndexed { idx, line ->
            val depth = blockLineRanges.count { (start, end) -> idx + 1 in (start + 1)..<end }
            if (depth > 0) {
                val indent = " ".repeat(depth * 4)
                val trimmed = line.trimStart()
                if (trimmed.isNotEmpty()) indent + trimmed else ""
            } else {
                line
            }
        }.joinToString("\n")
    }
}
