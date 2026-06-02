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

import cn.enaium.jimmer.buddy.dto.lang.utility.findPropTrace
import cn.enaium.jimmer.buddy.lang.parser.node.*
import cn.enaium.jimmer.buddy.lang.parser.utility.overlaps
import cn.enaium.jimmer.buddy.project.structure.Environment
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
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

    fun findTrace(line: Int, column: Int): List<String>? {
        val cursor = findCursor(line, column)
        return cursor?.findPropTrace()
    }

    fun findProps(
        environment: Environment,
        immutableType: ImmutableType, line: Int, column: Int
    ): List<MemberNode> {
        return findProps(environment, immutableType, findTrace(line, column))
    }

    fun findProps(
        environment: Environment,
        immutableType: ImmutableType,
        trace: List<String>?
    ): List<MemberNode> {
        trace ?: return listOf()
        if (trace.isEmpty()) {
            return environment.getMembers(immutableType.qualifiedName)
        } else {
            val trace = ArrayDeque(trace)
            var lastQualifiedName: String? = immutableType.qualifiedName

            while (trace.isNotEmpty() && lastQualifiedName != null) {
                val propName = trace.poll()
                environment.getMembers(lastQualifiedName).find { it.name == propName }?.also { memberNode ->
                    when (memberNode) {
                        is MethodNode -> {
                            memberNode.type
                        }

                        is PropertyNode -> {
                            memberNode.type
                        }

                        else -> null
                    }?.let { it as? ClassTypeNode }?.also {
                        if (it.arguments.isEmpty()) {
                            lastQualifiedName = it.qualifiedName
                        } else {
                            (it.arguments.firstOrNull() as? ClassTypeNode)?.also {
                                lastQualifiedName = it.qualifiedName
                            }
                        }
                    }
                }
            }

            lastQualifiedName?.also {
                return environment.getMembers(lastQualifiedName)
            }
        }
        return emptyList()
    }

    fun Environment.getMembers(qualifiedName: String): List<MemberNode> {
        val members = mutableListOf<MemberNode>()
        (findClass(qualifiedName) as? InterfaceNode)?.also { interfaceNode ->
            val supers = ArrayDeque<TypeNode>()
            supers.addAll(interfaceNode.supers)

            while (supers.isNotEmpty()) {
                val classTypeNode = supers.poll() as? ClassTypeNode ?: continue
                (classTypeNode.qualifiedName?.let { findClass(it) } as? InterfaceNode)?.also { interfaceNode ->
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

    fun findType(environment: Environment, trace: List<String>?, immutableType: ImmutableType): ImmutableType? {
        trace ?: return null
        if (trace.isEmpty()) {
            return immutableType
        } else {
            val trace = ArrayDeque(trace)
            var lastQualifiedName: String? = immutableType.qualifiedName

            while (trace.isNotEmpty() && lastQualifiedName != null) {
                val propName = trace.poll()
                environment.getMembers(lastQualifiedName).find { it.name == propName }?.also { memberNode ->
                    when (memberNode) {
                        is MethodNode -> {
                            memberNode.type
                        }

                        is PropertyNode -> {
                            memberNode.type
                        }

                        else -> null
                    }?.let { it as? ClassTypeNode }?.also {
                        if (it.arguments.isEmpty()) {
                            lastQualifiedName = it.qualifiedName
                        } else {
                            (it.arguments.firstOrNull() as? ClassTypeNode)?.also {
                                lastQualifiedName = it.qualifiedName
                            }
                        }
                    }
                }
            }

            lastQualifiedName?.also {
                return immutableType.context.ofType(it)
            }
        }
        return null
    }
}