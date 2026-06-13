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

package cn.enaium.jimmer.buddy.dto.lang

import cn.enaium.jimmer.buddy.lang.parser.index.InMemoryClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.ClassTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.InterfaceNode
import cn.enaium.jimmer.buddy.lang.parser.node.MethodNode
import cn.enaium.jimmer.buddy.lang.parser.node.PrimitiveTypeNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * @author Enaium
 */
class DtoProcessorTest {
    @Test
    fun findCursor() {
        val dtoProcessor = DtoProcessor(Path("build/resources/test/Simple.dto").readText())
        assertTrue(dtoProcessor.findCursor(14, 0) is DtoParser.DtoBodyContext)
        assertTrue(dtoProcessor.findCursor(16, 0) is DtoParser.DtoBodyContext)
        assertTrue(dtoProcessor.findCursor(18, 0) is DtoParser.DtoBodyContext)
        assertTrue(dtoProcessor.findCursor(20, 0) is DtoParser.DtoBodyContext)
    }

    @Test
    fun findTrace() {
        val dtoProcessor = DtoProcessor(Path("build/resources/test/Simple.dto").readText())
        assertEquals("object1.object2.object3", dtoProcessor.findTrace(13, 0)?.joinToString("."))
    }

    @Test
    fun findProps() {
        val dtoProcessor = DtoProcessor(Path("build/resources/test/Simple.dto").readText())
        // Not have super types
        assertIterableEquals(
            listOf("prop1", "prop2", "prop3"),
            dtoProcessor.findProps(
                InMemoryClassIndex(
                    mapOf(
                        "cn.enaium.model.Simple" to InterfaceNode(
                            "cn.enaium.model.Simple",
                            Path(""),
                            members = setOf(
                                MethodNode("cn.enaium.model.Simple", "prop1", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.Simple", "prop2", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.Simple", "prop3", type = PrimitiveTypeNode("int")),
                            )
                        )
                    )
                ), "cn.enaium.model.Simple", emptyList()
            ).map { it.name }
        )

        // Have super types
        assertIterableEquals(
            listOf(
                "baseProp1", "baseProp2", "baseProp3",
                "prop1", "prop2", "prop3"
            ),
            dtoProcessor.findProps(
                InMemoryClassIndex(
                    mapOf(
                        "cn.enaium.model.BaseEntity" to InterfaceNode(
                            "cn.enaium.model.BaseEntity",
                            Path(""),
                            members = setOf(
                                MethodNode("cn.enaium.model.BaseEntity", "baseProp1", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.BaseEntity", "baseProp2", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.BaseEntity", "baseProp3", type = PrimitiveTypeNode("int")),
                            )
                        ),
                        "cn.enaium.model.Simple" to InterfaceNode(
                            "cn.enaium.model.Simple",
                            Path(""),
                            supers = setOf(
                                ClassTypeNode("BaseEntity", "cn.enaium.model.BaseEntity"),
                            ),
                            members = setOf(
                                MethodNode("cn.enaium.model.Simple", "prop1", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.Simple", "prop2", type = PrimitiveTypeNode("int")),
                                MethodNode("cn.enaium.model.Simple", "prop3", type = PrimitiveTypeNode("int")),
                            )
                        )
                    )
                ), "cn.enaium.model.Simple", emptyList()
            ).map { it.name }
        )

        // Use trace
        val threeLayerNesting = InMemoryClassIndex(
            mapOf(
                "cn.enaium.model.Object3" to InterfaceNode(
                    "cn.enaium.model.Object3", Path(""), members = setOf(
                        MethodNode("cn.enaium.model.Object3", "prop1", type = PrimitiveTypeNode("int")),
                        MethodNode("cn.enaium.model.Object3", "prop2", type = PrimitiveTypeNode("int")),
                        MethodNode("cn.enaium.model.Object3", "prop3", type = PrimitiveTypeNode("int")),
                    )
                ),
                "cn.enaium.model.Object2" to InterfaceNode(
                    "cn.enaium.model.Object2", Path(""), members = setOf(
                        MethodNode(
                            "cn.enaium.model.Object2",
                            "object3",
                            type = ClassTypeNode("Object3", "cn.enaium.model.Object3")
                        ),
                    )
                ),
                "cn.enaium.model.Object1" to InterfaceNode(
                    "cn.enaium.model.Object1", Path(""), members = setOf(
                        MethodNode(
                            "cn.enaium.model.Object1",
                            "object2",
                            type = ClassTypeNode("Object2", "cn.enaium.model.Object2")
                        ),
                    )
                ),
                "cn.enaium.model.Simple" to InterfaceNode(
                    "cn.enaium.model.Simple", Path(""), members = setOf(
                        MethodNode(
                            "cn.enaium.model.Simple",
                            "object1",
                            type = ClassTypeNode("Object1", "cn.enaium.model.Object1")
                        ),
                    )
                )
            )
        )
        assertIterableEquals(
            listOf("prop1", "prop2", "prop3"),
            dtoProcessor.findProps(
                threeLayerNesting, "cn.enaium.model.Simple", listOf("object1", "object2", "object3")
            ).map { it.name }
        )

        assertIterableEquals(
            listOf("prop1", "prop2", "prop3"),
            dtoProcessor.findProps(
                threeLayerNesting, "cn.enaium.model.Simple", dtoProcessor.findTrace(14, 0)
            ).map { it.name }
        )
    }
}