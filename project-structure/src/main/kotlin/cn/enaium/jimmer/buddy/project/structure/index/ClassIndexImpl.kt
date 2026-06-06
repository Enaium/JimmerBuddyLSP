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

package cn.enaium.jimmer.buddy.project.structure.index

import cn.enaium.jimmer.buddy.lang.parser.entity.ClassEntity
import cn.enaium.jimmer.buddy.lang.parser.entity.classNode
import cn.enaium.jimmer.buddy.lang.parser.entity.path
import cn.enaium.jimmer.buddy.lang.parser.entity.qualifiedName
import cn.enaium.jimmer.buddy.lang.parser.entity.type
import cn.enaium.jimmer.buddy.lang.parser.entity.type.ClassType
import cn.enaium.jimmer.buddy.lang.parser.index.ClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.AnnotationClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.ClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.DataClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.EnumClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.InterfaceNode
import cn.enaium.jimmer.buddy.project.structure.db.sql
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.expression.ilike
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * @author Enaium
 */
class ClassIndexImpl(val path: Path) : ClassIndex {
    private val sql = sql(path)

    override fun findClass(qualifiedName: String): BaseClassNode? {
        return sql.createQuery(ClassEntity::class) {
            where(table.qualifiedName eq qualifiedName)
            select(table.classNode)
        }.fetchOneOrNull()
    }

    override fun findClasses(directory: Path): List<BaseClassNode> {
        return sql.createQuery(ClassEntity::class) {
            where(table.path.ilike("${directory.absolutePathString()}%"))
            select(table.classNode)
        }.execute()
    }

    override fun findClasses(type: ClassType): List<BaseClassNode> {
        return sql.createQuery(ClassEntity::class) {
            where(table.type eq type)
            select(table.classNode)
        }.execute()
    }

    override fun upsertClass(qualifiedName: String, classNode: BaseClassNode) {
        sql.save(ClassEntity {
            this.qualifiedName = qualifiedName
            this.type = when (classNode) {
                is ClassNode -> {
                    ClassType.CLASS
                }
                is InterfaceNode -> {
                    ClassType.INTERFACE
                }
                is EnumClassNode -> {
                    ClassType.ENUM
                }
                is DataClassNode -> {
                    ClassType.DATA
                }
                is AnnotationClassNode -> {
                    ClassType.ANNOTATION
                }
                else -> {
                    throw IllegalArgumentException("Class type ${classNode.qualifiedName} is not supported")
                }
            }
            this.classNode = classNode
            this.path = classNode.path.absolutePathString()
        })
    }
}