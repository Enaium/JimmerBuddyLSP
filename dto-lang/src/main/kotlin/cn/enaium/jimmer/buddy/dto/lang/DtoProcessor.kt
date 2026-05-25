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

package cn.enaium.jimmer.buddy.dto.lang

import cn.enaium.jimmer.buddy.dto.lang.utility.overlaps
import cn.enaium.jimmer.buddy.lang.parser.node.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeListener
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.xpath.XPath
import java.util.*

/**
 * @author Enaium
 */
class DtoProcessor(val source: String) {

    fun lex(): DtoLexer {
        return DtoLexer(CharStreams.fromString(source))
    }

    fun parse(): DtoParser.DtoContext {
        return DtoParser(CommonTokenStream(DtoLexer(CharStreams.fromString(source)))).dto()
    }

    fun findCursor(line: Int, column: Int): ParserRuleContext? {
        val dtoParser = DtoParser(CommonTokenStream(DtoLexer(CharStreams.fromString(source))))
        val results = mutableListOf<ParserRuleContext>()
        dtoParser.addParseListener(object : ParseTreeListener {
            override fun visitTerminal(node: TerminalNode) {

            }

            override fun visitErrorNode(node: ErrorNode) {

            }

            override fun enterEveryRule(ctx: ParserRuleContext) {

            }

            override fun exitEveryRule(ctx: ParserRuleContext) {
                if (ctx.overlaps(line, column)) {
                    results.add(ctx)
                }
            }
        })
        dtoParser.dto()
        return results.minByOrNull { it.stop.line - it.stop.line }
    }

    fun findTrace(line: Int, column: Int): List<String> {
        val trace = mutableListOf<String>()
        when (val cursor = findCursor(line, column)) {
            is DtoParser.DtoBodyContext -> {
                var parent: RuleContext? = cursor.parent
                while (parent != null) {
                    if (parent is DtoParser.PositivePropContext) {
                        parent.prop?.text?.also {
                            trace.add(it)
                        }
                    }
                    parent = parent.parent
                }
            }
        }
        return trace.reversed()
    }

    fun findProps(classes: Map<String, ClassNode>, line: Int, column: Int): List<MemberNode> {
        return findProps(classes, findTrace(line, column))
    }

    fun findProps(classes: Map<String, ClassNode>, trace: List<String>): List<MemberNode> {
        val dtoParser = DtoParser(CommonTokenStream(DtoLexer(CharStreams.fromString(source))))
        val dto = dtoParser.dto()
        val exportName = (XPath.findAll(dto, "/dto/exportStatement/typeParts/qualifiedName", dtoParser)
            .firstOrNull() as? DtoParser.QualifiedNameContext)?.let {
            it.parts.joinToString(".") { part -> part.text }
        } ?: return emptyList<MemberNode>()

        if (trace.isEmpty()) {
            return classes.getMembers(exportName)
        } else {
            val trace = ArrayDeque(trace)
            var lastQualifiedName: String? = exportName

            while (trace.isNotEmpty() && lastQualifiedName != null) {
                val propName = trace.poll()
                classes.getMembers(lastQualifiedName).find { it.name == propName }?.also { memberNode ->
                    when (memberNode) {
                        is MethodNode -> {
                            memberNode.type
                        }

                        is PropertyNode -> {
                            memberNode.type
                        }

                        else -> null
                    }?.let { it as? ClassTypeNode }?.also {
                        lastQualifiedName = it.qualifiedName
                    }
                }
            }

            lastQualifiedName?.also {
                return classes.getMembers(lastQualifiedName)
            }
        }
        return emptyList()
    }

    fun Map<String, ClassNode>.getMembers(qualifiedName: String): List<MemberNode> {
        val members = mutableListOf<MemberNode>()
        (this[qualifiedName] as? InterfaceNode)?.also { interfaceNode ->
            val supers = ArrayDeque<TypeNode>()
            supers.addAll(interfaceNode.supers)

            while (supers.isNotEmpty()) {
                val classTypeNode = supers.poll() as? ClassTypeNode ?: continue
                (this[classTypeNode.qualifiedName] as? InterfaceNode)?.also { interfaceNode ->
                    interfaceNode.members.forEach { memberNode ->
                        members.add(memberNode)
                    }
                    supers.addAll(interfaceNode.supers)
                }
            }

            interfaceNode.members.forEach {
                members.add(it)
            }
        }
        return members
    }
}