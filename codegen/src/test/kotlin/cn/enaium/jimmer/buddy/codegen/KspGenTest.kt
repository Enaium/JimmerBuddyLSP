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

package cn.enaium.jimmer.buddy.codegen

import cn.enaium.jimmer.buddy.codegen.gen.KspGen
import cn.enaium.jimmer.buddy.lang.parser.processor.KotlinSourceProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.*

/**
 * @author Enaium
 */
class KspGenTest {
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun gen() {
        runBlocking {
            val projectDir = Path(System.getProperty("user.dir")) / "build/resources/test/projects/simple-jimmer-model"
            val process =
                KotlinSourceProcessor(
                    listOf(
                        Path(System.getProperty("java.home")) / "lib/src.zip",
                        Path(System.getProperty("user.home")) / ".gradle/caches/modules-2/files-2.1/org.babyfish.jimmer/jimmer-core/0.10.7/aaa9d8a74d4764e87f0e6b823ad593f014c8c914/jimmer-core-0.10.7-sources.jar",
                        projectDir / "src/main/kotlin"
                    )
                ).process()

            val genDir = createTempDirectory("JimmerBuddyLSP-KspGenTest")
            val sourceDir = genDir / "source"
            val dtoDir = genDir / "dto"
            KspGen(
                projectDir,
                process,
                sourceDir,
                emptyMap(),
            ).sourceProcess(process.values.filter { it.path.parent.isDirectory() }.toSet())
            assertEquals(23, sourceDir.walk().count())
            KspGen(
                projectDir,
                process,
                dtoDir,
                emptyMap(),
            ).dtoProcess((projectDir / "src/main/dto").walk().toSet())
            assertEquals(1, dtoDir.walk().count())
            genDir.deleteRecursively()
        }
    }
}