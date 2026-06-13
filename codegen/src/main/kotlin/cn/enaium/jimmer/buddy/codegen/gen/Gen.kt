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

package cn.enaium.jimmer.buddy.codegen.gen

import cn.enaium.jimmer.buddy.lang.parser.index.ClassIndex
import cn.enaium.jimmer.buddy.codegen.symbol.Source
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * @author Enaium
 */
abstract class Gen(
    val projectDir: Path,
    val classIndex: ClassIndex,
    val genDir: Path,
    val options: Map<String, String>
) {
    fun Source.write() {
        val path = genDir / this.packageName.replace(".", "/") / "${this.fileName}.${this.extensionName}"
        path.createParentDirectories()
        path.writeText(this.content)
    }
}