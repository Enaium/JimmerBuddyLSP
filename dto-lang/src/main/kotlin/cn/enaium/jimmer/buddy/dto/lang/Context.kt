/*
 * Copyright 2024 Enaium
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

import cn.enaium.jimmer.buddy.lang.parser.node.ClassNode
import cn.enaium.jimmer.buddy.project.structure.Project

/**
 * @author Enaium
 */
class Context(val project: Project) {
    private val sourceCache = mutableMapOf<String, ClassNode>()

    fun ofSource(qualifiedName: String): ClassNode? {
        return sourceCache[qualifiedName] ?: project.environment.classes[qualifiedName]?.also {
            sourceCache[qualifiedName] = it
        }
    }

    private val typeCache = mutableMapOf<String, ImmutableType>()

    fun ofType(qualifiedName: String): ImmutableType? {
        return typeCache[qualifiedName] ?: project.environment.classes[qualifiedName]?.let {
            ImmutableType(this, it).also {
                typeCache[qualifiedName] = it
            }
        }
    }
}