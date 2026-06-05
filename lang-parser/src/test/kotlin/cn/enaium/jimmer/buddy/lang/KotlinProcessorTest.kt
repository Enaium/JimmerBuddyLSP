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

package cn.enaium.jimmer.buddy.lang

import cn.enaium.jimmer.buddy.lang.parser.index.InMemoryClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.ClassTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.InterfaceNode
import cn.enaium.jimmer.buddy.lang.parser.node.MethodNode
import cn.enaium.jimmer.buddy.lang.parser.processor.KotlinSourceProcessor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * @author Enaium
 */
class KotlinProcessorTest {
    @Test
    fun javaProcessorTest() {
        val immutableProp = "org.babyfish.jimmer.meta.ImmutableProp"
        runBlocking {
            val index = InMemoryClassIndex()
            KotlinSourceProcessor(
                setOf(
                    Path(System.getProperty("java.home")) / "lib/src.zip",
                    Path(System.getProperty("user.home")) / ".gradle/caches/modules-2/files-2.1/org.babyfish.jimmer/jimmer-core/0.10.7/aaa9d8a74d4764e87f0e6b823ad593f014c8c914/jimmer-core-0.10.7-sources.jar"
                ), index
            )
                .process()
            assertTrue(index.findClass(immutableProp) != null)
            index.findClass(immutableProp)?.also { classNode ->
                assertTrue(classNode is InterfaceNode)
                assertTrue((classNode as InterfaceNode).members.find { it.name == "getId" }
                    ?.let { ((it as? MethodNode)?.type as? ClassTypeNode)?.qualifiedName == "org.babyfish.jimmer.meta.PropId" } == true)
            }
        }
    }

    @Test
    fun sourceDirTest() {
        val srcDir =
            Path(System.getProperty("user.dir")) / "build/resources/test/projects/simple-jimmer-model/src/main/kotlin"
        runBlocking {
            val index = InMemoryClassIndex()
            KotlinSourceProcessor(
                setOf(
                    Path(System.getProperty("java.home")) / "lib/src.zip",
                    Path(System.getProperty("user.home")) / ".gradle/caches/modules-2/files-2.1/org.babyfish.jimmer/jimmer-core/0.10.7/aaa9d8a74d4764e87f0e6b823ad593f014c8c914/jimmer-core-0.10.7-sources.jar",
                    srcDir
                ), index
            ).process()
            val entities = listOf(
                "cn.enaium.Answer",
                "cn.enaium.BaseEntity",
                "cn.enaium.Comment",
                "cn.enaium.People",
                "cn.enaium.Profile",
                "cn.enaium.Post",
                "cn.enaium.Question",
                "cn.enaium.Topic"
            )
            assertTrue(entities.any { index.findClass(it) != null })
            assertNotNull(index.findClass("cn.enaium.BaseEntity")?.annotations?.find { it.qualifiedName == "org.babyfish.jimmer.sql.MappedSuperclass" })

            entities.filter { it != "cn.enaium.BaseEntity" }.forEach { entity ->
                assertNotNull((index.findClass(entity) as? InterfaceNode)?.supers?.find { (it as? ClassTypeNode)?.qualifiedName == "cn.enaium.BaseEntity" })
            }

            assertNotNull(((index.findClass("cn.enaium.People") as? InterfaceNode)?.members?.find { it.name == "profile" }?.type as? ClassTypeNode)?.also {
                assertEquals("cn.enaium.Profile", it.qualifiedName)
                assertTrue(it.nullable)
            })
            assertNotNull((index.findClass("cn.enaium.People") as? InterfaceNode)?.members?.find { it.name == "posts" }?.annotations?.find { it.qualifiedName == "org.babyfish.jimmer.sql.OneToMany" })
            assertNotNull((index.findClass("cn.enaium.Post") as? InterfaceNode)?.members?.find { it.name == "people" }?.annotations?.find { it.qualifiedName == "org.babyfish.jimmer.sql.ManyToOne" })
            assertNotNull((index.findClass("cn.enaium.Post") as? InterfaceNode)?.members?.find { it.name == "topics" }?.annotations?.find { it.qualifiedName == "org.babyfish.jimmer.sql.ManyToMany" })
            assertNotNull(((index.findClass("cn.enaium.Post") as? InterfaceNode)?.members?.find { it.name == "topics" }?.type as? ClassTypeNode)?.also {
                assertEquals("kotlin.collections.List", it.qualifiedName)
                assertEquals("cn.enaium.Topic", (it.arguments.first() as? ClassTypeNode)?.qualifiedName)
            })
            val changeFile = srcDir / "cn/enaium/Answer.kt"
            changeFile.also {
                val origin = it.readText()
                it.writeText(origin.replaceRange(origin.length - 1, origin.length - 1, "val insertField: String"))
            }
            KotlinSourceProcessor(setOf(changeFile), index).process()
            assertNotNull(index.findClass("cn.enaium.Answer")?.annotations?.find { it.qualifiedName == "org.babyfish.jimmer.sql.Entity" })
            assertNotNull((index.findClass("cn.enaium.Answer") as? InterfaceNode)?.members?.find { it.name == "insertField" })
        }
    }
}
