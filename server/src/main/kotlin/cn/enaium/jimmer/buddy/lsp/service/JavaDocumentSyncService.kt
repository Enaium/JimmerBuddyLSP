package cn.enaium.jimmer.buddy.lsp.service

import cn.enaium.jimmer.buddy.codegen.gen.AptGen
import cn.enaium.jimmer.buddy.lang.parser.processor.JavaSourceProcessor
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.project.structure.Project
import java.net.URI
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class JavaDocumentSyncService(project: Project, documentManager: DocumentManager) :
    DocumentSyncService(project, documentManager) {
    override fun validate(
        content: String,
        uri: String,
        type: Type
    ) {
        val path = URI.create(uri).toPath()
        path.extension != "java" && return

        when (type) {
            Type.CHANGE -> {
                deq.schedule("genSource", 2000) {
                    val index = project.environment.getIndex()
                    JavaSourceProcessor(setOf(path), index).process()
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
