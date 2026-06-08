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

import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.ClassTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.InterfaceNode
import org.babyfish.jimmer.dto.compiler.spi.BaseType
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.MappedSuperclass

/**
 * @author Enaium
 */
data class ImmutableType(
    val context: Context,
    val classNode: BaseClassNode
) : BaseType {
    val superTypes: List<ImmutableType> = if (classNode is InterfaceNode) {
        classNode.supers.filterIsInstance<ClassTypeNode>().mapNotNull { context.ofType(it.qualifiedName ?: it.name) }
    } else {
        emptyList()
    }

    private val primarySuperType: ImmutableType? =
        superTypes.firstOrNull { !it.isMappedSuperclass }

    val declaredProperties: Map<String, ImmutableProp>
        get() = if (classNode is InterfaceNode) {
            classNode.members
                .filter { member -> member.annotations.any { it.name.endsWith(Id::class.simpleName!!) } }
                .associateBy({ it.name }) {
                    ImmutableProp(context, this, it)
                } + classNode.members
                .filter { member -> member.annotations.any { !it.name.endsWith(Id::class.simpleName!!) } || member.annotations.isEmpty() }
                .associateBy({ it.name }) {
                    ImmutableProp(context, this, it)
                }
        } else {
            emptyMap()
        }

    private val superPropMap: Map<String, ImmutableProp> = superTypes
        .flatMap { it.properties.values }
        .groupBy { it.name }
        .toList()
        .associateBy({ it.first }) {
            it.second.first()
        }

    private val redefinedProps = superPropMap.filterKeys {
        primarySuperType == null || !primarySuperType.properties.contains(it)
    }.mapValues {
        ImmutableProp(context, this, it.value.prop)
    }

    val properties: Map<String, ImmutableProp> =
        if (superTypes.isEmpty()) {
            declaredProperties
        } else {
            val map = mutableMapOf<String, ImmutableProp>()
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (!prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            map
        }

    val idProp: ImmutableProp? by lazy {
        val idProps = declaredProperties.values.filter { it.isId }
        val superIdProp = superTypes.firstOrNull { it.idProp !== null }?.idProp
        val prop = idProps.firstOrNull() ?: superIdProp
        prop
    }

    override val isEntity: Boolean =
        if (classNode is InterfaceNode) {
            classNode.annotations.any { it.name.endsWith(Entity::class.simpleName!!) }
        } else {
            false
        }
    val isMappedSuperclass: Boolean =
        if (classNode is InterfaceNode) {
            classNode.annotations.any { it.name.endsWith(MappedSuperclass::class.simpleName!!) }
        } else {
            false
        }
    val isEmbeddable: Boolean =
        if (classNode is InterfaceNode) {
            classNode.annotations.any { it.name.endsWith(Embeddable::class.simpleName!!) }
        } else {
            false
        }

    val isImmutable: Boolean = isEntity || isMappedSuperclass || isEmbeddable

    override val name: String = classNode.qualifiedName.substringAfterLast('.')
    override val packageName: String = classNode.qualifiedName.substringBeforeLast(".")
    override val qualifiedName: String = classNode.qualifiedName

    override fun toString(): String {
        return name
    }
}