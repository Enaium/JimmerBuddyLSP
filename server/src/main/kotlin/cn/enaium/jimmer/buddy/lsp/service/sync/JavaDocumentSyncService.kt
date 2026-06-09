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

import cn.enaium.jimmer.buddy.codegen.gen.AptGen
import cn.enaium.jimmer.buddy.lang.parser.processor.JavaSourceProcessor
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.JavaDocument
import cn.enaium.jimmer.buddy.project.structure.Project
import org.treesitter.TSParser
import org.treesitter.TreeSitterJava
import java.net.URI
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class JavaDocumentSyncService(project: Project, documentManager: DocumentManager) :
    AbstractDocumentSyncService(project, documentManager) {
    override suspend fun validate(
        content: String,
        uri: String,
        type: Type
    ) {
        !uri.startsWith("file") && return
        val path = URI.create(uri).toPath()
        path.extension != "java" && return
        val parser = TSParser()
        val language = TreeSitterJava()
        val parse = parser.parseString(null, content)
        documentManager.openOrUpdateDocument(uri, JavaDocument(content, parse.rootNode))
        when (type) {
            Type.CHANGE -> {
                deq.schedule("genSource", 2000) {
                    val index = project.environment.getIndex()
                    JavaSourceProcessor(emptySet(), index).apply {
                        addFile(content, path) {
                            content
                        }
                    }.process()
                    val module =
                        project.environment.modules.sortedByDescending { it.directory.nameCount }
                            .find { path.startsWith(it.directory) } ?: return@schedule
                    val genDir = getGenDirectory(path) ?: return@schedule
                    val genClasses = index.findClasses(path.parent).filter { it.path == path }.toSet()
                    AptGen(
                        module.directory,
                        project.environment,
                        genDir,
                        emptyMap()
                    ).sourceProcess(genClasses)
                }
            }

            else -> {}
        }
    }
}
