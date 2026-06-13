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

package cn.enaium.jimmer.buddy.project.structure

import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.MemberNode
import cn.enaium.jimmer.buddy.lang.parser.node.TypeNode
import cn.enaium.jimmer.buddy.lang.parser.processor.JavaSourceProcessor
import cn.enaium.jimmer.buddy.lang.parser.processor.KotlinSourceProcessor
import cn.enaium.jimmer.buddy.project.structure.index.ClassIndexImpl
import cn.enaium.jimmer.buddy.project.structure.jackson.ClassNodeMixin
import cn.enaium.jimmer.buddy.project.structure.jackson.MemberNodeMixin
import cn.enaium.jimmer.buddy.project.structure.jackson.TypeNodeMixin
import cn.enaium.jimmer.buddy.project.structure.utility.findGitIgnorePath
import org.babyfish.jimmer.sql.exception.DatabaseValidationException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import tools.jackson.databind.node.ArrayNode
import tools.jackson.dataformat.smile.SmileMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.io.path.*


/**
 * @author Enaium
 */
class Environment {
    val directories = CopyOnWriteArraySet<Path>()
    val modules = CopyOnWriteArraySet<Module>()
    val dependencies = CopyOnWriteArraySet<Path>()

    var isJimmerProject = false
    var isJavaProject = false
    var isKotlinProject = false

    private val mapper: SmileMapper = SmileMapper.builder().addModule(kotlinModule())
        .addMixIn(BaseClassNode::class.java, ClassNodeMixin::class.java)
        .addMixIn(MemberNode::class.java, MemberNodeMixin::class.java)
        .addMixIn(TypeNode::class.java, TypeNodeMixin::class.java)
        .build()

    private var classIndex: ClassIndexImpl? = null

    fun getIndex(): ClassIndexImpl = classIndex ?: error("ClassIndex not initialized")

    private val onExits = mutableListOf<() -> Unit>()

    @OptIn(ExperimentalPathApi::class)
    suspend fun process(project: Project) {
        directories.forEach { directory ->
            suspend fun init() {
                val cacheDirectory = directory / ".jblsp"
                if (!cacheDirectory.exists()) {
                    cacheDirectory.createDirectory()
                }

                findGitIgnorePath(directory)?.also {
                    if (!it.readText().contains(cacheDirectory.name)) {
                        it.appendLines(listOf("${cacheDirectory.name}/"))
                    }
                }

                val modulesCache = cacheDirectory / "modules.smile"
                val classesCache = cacheDirectory / "classes.smile"
                val dependenciesCache = cacheDirectory / "dependencies.smile"


                try {
                    classIndex = ClassIndexImpl(classesCache)
                } catch (e: DatabaseValidationException) {
                    cacheDirectory.deleteRecursively()
                    init()
                    return
                }

                val cached = listOf(cacheDirectory, modulesCache, classesCache, dependenciesCache).all { it.exists() }
                if (cached) {
                    modules.addAll(mapper.readValue<ArrayNode>(modulesCache.readBytes()).toList().map { module ->
                        Module(
                            project = project,
                            directory = URI.create(module.get("directory").asString()).toPath(),
                            buildDirectory = URI.create(module.get("buildDirectory").asString()).toPath(),
                            sourceDirectories = module.get("sourceDirectories").toList()
                                .map { URI.create(it.asString()).toPath() }
                                .toList(),
                        )
                    }.toList())
                    dependencies.addAll(mapper.readValue<List<Path>>(dependenciesCache.readBytes()))
                } else {
                    val connection = GradleConnector.newConnector()
                        .forProjectDirectory(directory.toFile())
                        .connect()

                    val ideaProject = connection.model(IdeaProject::class.java).get()

                    for (module in ideaProject.modules) {
                        val moduleDependencies = mutableListOf<Path>()
                        for (dep in module.dependencies) {
                            if (dep is IdeaSingleEntryLibraryDependency) {
                                val element = dep.source?.toPath() ?: continue
                                moduleDependencies.add(element)
                            }
                        }

                        dependencies.addAll(moduleDependencies)

                        modules.add(
                            Module(
                                project,
                                module.gradleProject.projectDirectory.toPath(),
                                module.gradleProject.buildDirectory.toPath(),
                                module.contentRoots.flatMap { roots -> roots.sourceDirectories.map { it.directory.toPath() } },
                            )
                        )
                    }

                    connection.close()
                }

                isJimmerProject = dependencies.any { it.name.startsWith("jimmer-core") }
                isKotlinProject = dependencies.any { it.name.startsWith("jimmer-core-kotlin") } &&
                        modules.any { module -> module.sourceDirectories.any { it.name == "kotlin" } }

                if (!isKotlinProject) {
                    isJavaProject = isJimmerProject
                }


                val saveCache = {
                    modulesCache.writeBytes(mapper.writeValueAsBytes(modules))
                    dependenciesCache.writeBytes(mapper.writeValueAsBytes(dependencies))
                }

                if (!cached) {
                    val jdkSrc = Path(System.getProperty("java.home")) / "lib/src.zip"
                    val sourceDirOrJar = dependencies + modules.flatMap { it.sourceDirectories } + listOf(jdkSrc)
                    if (isKotlinProject) {
                        KotlinSourceProcessor(sourceDirOrJar, classIndex!!).process()
                    } else if (isJavaProject) {
                        JavaSourceProcessor(sourceDirOrJar, classIndex!!).process()
                    }

                    saveCache()
                }

                onExits.add(saveCache)
            }

            init()
        }
    }

    fun exit() {
        onExits.forEach { it() }
    }
}