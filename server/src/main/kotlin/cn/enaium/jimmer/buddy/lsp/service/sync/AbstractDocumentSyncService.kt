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

package cn.enaium.jimmer.buddy.lsp.service.sync

import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.utility.DelayedExecutionQueue
import cn.enaium.jimmer.buddy.project.structure.Project
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.relativeTo

/**
 * @author Enaium
 */
abstract class AbstractDocumentSyncService(
    protected val project: Project,
    protected val documentManager: DocumentManager
) {

    val deq = DelayedExecutionQueue()

    abstract suspend fun validate(content: String, uri: String, type: Type)

    enum class Type {
        OPEN, CHANGE, CLOSE, SAVE
    }

    protected fun getGenDirectory(path: Path): Path? {
        val module =
            project.environment.modules.sortedByDescending { it.directory.nameCount }
                .find { path.startsWith(it.directory) }
                ?: return null
        val sourceDirectory =
            module.sourceDirectories.find { path.startsWith(it.parent) }?.relativeTo(module.directory)
                ?: return null
        val buildDirectory = module.buildDirectory
        val main = sourceDirectory.subpath(1, 2).name
        return when {
            project.environment.isKotlinProject -> {
                buildDirectory / "generated/ksp" / main / "kotlin"
            }

            project.environment.isJavaProject -> {
                buildDirectory / "generated/sources/annotationProcessor/java" / main
            }

            else -> null
        }
    }
}