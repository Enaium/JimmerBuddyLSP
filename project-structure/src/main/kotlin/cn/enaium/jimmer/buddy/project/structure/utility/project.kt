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

package cn.enaium.jimmer.buddy.project.structure.utility

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * @author Enaium
 */
fun isProject(path: Path): Boolean {
    return listOf("build.gradle.kts", "build.gradle", "pom.xml", ".git").any { (path / it).exists() }
}

fun findProjectDir(dtoPath: Path, root: Boolean = false): Path? {
    var parent = dtoPath.parent
    var rootPath: Path? = null
    while (parent != null) {
        if (isProject(parent)) {
            if (root) {
                rootPath = parent
            } else {
                return parent
            }
        }
        parent = parent.parent
    }
    return rootPath
}

fun findGitIgnorePath(directory: Path): Path? {
    val file = directory / ".gitignore"
    return if (file.exists()) {
        file
    } else {
        directory.parent?.let { findGitIgnorePath(it) }
    }
}


fun findProjects(rootProject: Path, level: Int = 0): List<Path> {
    val results = mutableListOf<Path>()
    if (isProject(rootProject)) {
        results.add(rootProject)
    }
    rootProject.toFile().listFiles()?.forEach {
        val file = it.toPath()
        if (file.isDirectory() && isProject(file)) {
            results.add(file)
            if (level < 4) {
                return findProjects(file, level + 1)
            }
        }
    }
    return results
}