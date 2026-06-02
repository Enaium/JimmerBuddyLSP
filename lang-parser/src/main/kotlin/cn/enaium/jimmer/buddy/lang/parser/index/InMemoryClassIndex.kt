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

package cn.enaium.jimmer.buddy.lang.parser.index

import cn.enaium.jimmer.buddy.lang.parser.node.ClassNode
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString

/**
 * @author Enaium
 */
class InMemoryClassIndex : ClassIndex {

    private val classes = ConcurrentHashMap<String, ClassNode>()

    constructor()

    constructor(classes: Map<String, ClassNode>) {
        this.classes.putAll(classes)
    }

    override fun findClass(qualifiedName: String): ClassNode? {
        return classes[qualifiedName]
    }

    override fun findClasses(directory: Path): List<ClassNode> {
        val prefix = directory.absolutePathString()
        return classes.values.filter { it.path.absolutePathString().startsWith(prefix) }
    }

    override fun upsertClass(qualifiedName: String, classNode: ClassNode) {
        classes[qualifiedName] = classNode
    }
}
