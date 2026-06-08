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

package cn.enaium.jimmer.buddy.dto.lang.utility

import com.squareup.javapoet.TypeName

/**
 * @author Enaium
 */
fun TypeName.simplify(): String {
    return this.toString().simplifyTypeName()
}


fun com.squareup.kotlinpoet.TypeName.simplify(): String {
    return this.toString().simplifyTypeName()
}

private fun String.simplifyTypeName(): String {
    fun splitGenericParams(params: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (char in params) {
            when (char) {
                '<' -> {
                    depth++
                    current.append(char)
                }

                '>' -> {
                    depth--
                    current.append(char)
                }

                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString().trim())
                        current.clear()
                    } else {
                        current.append(char)
                    }
                }

                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString().trim())
        }
        return result
    }

    fun stripFullType(typeStr: String): String {
        val trimmed = typeStr.trim()
        val genericStart = trimmed.indexOf('<')
        return if (genericStart == -1) {
            trimmed.substringAfterLast('.')
        } else {
            val base = trimmed.take(genericStart).substringAfterLast('.')
            val genericContent = trimmed.substring(genericStart + 1, trimmed.lastIndexOf('>'))
            val params = splitGenericParams(genericContent).map { stripFullType(it) }
            "$base<${params.joinToString(", ")}>"
        }
    }

    return stripFullType(this)
}