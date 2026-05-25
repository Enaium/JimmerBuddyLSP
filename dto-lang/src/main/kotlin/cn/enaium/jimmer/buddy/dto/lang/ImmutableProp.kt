/*
 * Copyright 2024 Enaium
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

import cn.enaium.jimmer.buddy.lang.parser.node.*
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import org.babyfish.jimmer.sql.*

/**
 * @author Enaium
 */
data class ImmutableProp(
    private val context: Context,
    val declaringType: ImmutableType,
    val prop: MemberNode
) : BaseProp {
    private val targetSource = (prop.type as? ClassTypeNode)?.let { classTypeNode ->
        if (isList) {
            (classTypeNode.arguments.first() as? ClassTypeNode)?.let { classTypeNode ->
                context.ofSource(
                    classTypeNode.qualifiedName ?: classTypeNode.name
                )
            }
        } else {
            context.ofSource(classTypeNode.qualifiedName ?: classTypeNode.name)
        }
    }

    private val isAssociation: Boolean =
        targetSource is InterfaceNode && targetSource.annotations.any {
            it.qualifiedName in listOfNotNull(
                Entity::class.qualifiedName,
                MappedSuperclass::class.qualifiedName, Embeddable::class.qualifiedName
            )
        }

    val targetType: ImmutableType? by lazy {
        targetSource
            ?.takeIf { isAssociation }
            ?.let {
                context.ofType(it.qualifiedName)
            }
    }

    val isGeneratedValue: Boolean =
        prop.annotations.any { it.name.endsWith(GeneratedValue::class.simpleName!!) }

    val enumConstants: List<String> =
        if (targetSource is EnumClassNode) {
            targetSource.entries.map { it.name }
        } else {
            emptyList()
        }

    override val idViewBaseProp: BaseProp? = null

    val isIdView: Boolean = prop.annotations.any { it.name.endsWith(IdView::class.simpleName!!) }

    override val isEmbedded: Boolean
        get() = targetType?.isEmbeddable == true

    override val isExcludedFromAllScalars: Boolean
        get() = prop.annotations.any { it.name.endsWith(ExcludeFromAllScalars::class.simpleName!!) }

    override val isFormula: Boolean =
        prop.annotations.any { it.name.endsWith(Formula::class.simpleName!!) }

    override val isId: Boolean =
        prop.annotations.any { it.name.endsWith(Id::class.simpleName!!) }

    override val isKey: Boolean =
        prop.annotations.any { it.name.endsWith(Key::class.simpleName!!) }

    override val isList: Boolean
        get() = listOf(
            "List",
            "Set",
            "Collection",
            "MutableList",
            "MutableSet",
            "MutableCollection"
        ).any { collection ->
            (prop.type as? ClassTypeNode)?.let {
                it.qualifiedName?.substringAfterLast('.') ?: it.name
            } == collection
        }

    override val isLogicalDeleted: Boolean =
        prop.annotations.any { it.name.endsWith(LogicalDeleted::class.simpleName!!) }

    override val isNullable: Boolean
        get() = (prop.type as? ClassTypeNode)?.nullable == true

    override val isRecursive: Boolean by lazy {
        declaringType.isEntity && manyToManyViewBaseProp == null && targetSource != null && isAssignableFrom(
            declaringType.classNode,
            targetSource
        )
    }

    private fun isAssignableFrom(
        classNode1: ClassNode,
        classNode2: ClassNode
    ): Boolean {
        if (classNode1 == classNode2) {
            return true
        }
        if (classNode1 is InterfaceNode && classNode1.supers.filterIsInstance<ClassTypeNode>().any {
                val ofSource = context.ofSource(it.qualifiedName ?: it.name)
                ofSource != null && ofSource is InterfaceNode && isAssignableFrom(ofSource, classNode2)
            }) {
            return true
        }
        return false
    }

    override val isTransient: Boolean =
        prop.annotations.any { it.name.endsWith(Transient::class.simpleName!!) }

    override val manyToManyViewBaseProp: BaseProp? = null

    val isManyToManyView: Boolean =
        prop.annotations.any { it.name.endsWith(ManyToManyView::class.simpleName!!) }

    override val name: String
        get() = prop.name

    override fun hasTransientResolver(): Boolean {
        return isTransient
    }

    override fun isAssociation(entityLevel: Boolean): Boolean {
        return isAssociation && (!entityLevel || targetType?.isEntity == true)
    }

    override val isReference: Boolean
        get() = !isList && isAssociation

    override fun toString(): String {
        return name
    }
}