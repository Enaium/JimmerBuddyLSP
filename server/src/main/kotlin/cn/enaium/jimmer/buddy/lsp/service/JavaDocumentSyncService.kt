package cn.enaium.jimmer.buddy.lsp.service

import cn.enaium.jimmer.buddy.codegen.gen.KspGen
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
                    val process = JavaSourceProcessor(setOf(path), project.environment.classes).process()
                    project.environment.classes.putAll(process)
                    val module =
                        project.environment.modules.find { path.startsWith(it.directory) } ?: return@schedule
                    val genDir = getGenDirectory(path) ?: return@schedule
                    KspGen(
                        module.directory,
                        process,
                        genDir,
                        emptyMap()
                    ).sourceProcess(process.values.filter { it.path == path }
                        .toSet())
                }
            }

            else -> {}
        }
    }
}