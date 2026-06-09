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

package cn.enaium.jimmer.buddy.lang.parser.utility

import org.treesitter.*

/**
 * @author Enaium
 */
fun TSNode.text(content: String): String? {
    val start = this.startByte
    val end = this.endByte
    if (start > content.length || end > content.length) {
        return null
    }
    return content.substring(start, end)
}

fun TSNode.field(name: String): TSNode? {
    return try {
        this.getChildByFieldName(name)
    } catch (e: Exception) {
        return null
    }
}

fun TSNode.types(vararg type: String): List<TSNode> {
    val types = mutableListOf<TSNode>()
    try {
        for (i in 0 until this.childCount) {
            val child = this.getChild(i)
            if (child.type in type) {
                types.add(child)
            }
        }
    } catch (e: Exception) {
        return types
    }
    return types
}

fun TSNode.parent(): TSNode? {
    return this.parent?.takeIf { !it.isNull }
}

fun TSNode.prevNamedSibling(): TSNode? {
    return this.prevNamedSibling?.takeIf { !it.isNull }
}

fun TSNode.findParent(type: String): TSNode? {
    if (this.type == type) {
        return this
    }

    val parent = this.parent()
    while (parent != null) {
        return parent.findParent(type)
    }
    return parent
}

fun TSNode.query(language: TSLanguage, query: String): List<Pair<String, TSNode>> {
    val result = mutableListOf<Pair<String, TSNode>>()
    val query = TSQuery(language, query)
    val cursor = TSQueryCursor()
    cursor.exec(query, this)
    val match = TSQueryMatch()
    while (cursor.nextMatch(match)) {
        match.captures.forEach { capture ->
            result.add(query.getCaptureNameForId(capture.index) to capture.node)
        }
    }
    return result
}