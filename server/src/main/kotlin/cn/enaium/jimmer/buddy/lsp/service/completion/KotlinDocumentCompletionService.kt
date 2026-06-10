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

package cn.enaium.jimmer.buddy.lsp.service.completion

import cn.enaium.jimmer.buddy.dto.lang.ImmutableType
import cn.enaium.jimmer.buddy.lang.parser.utility.*
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.KotlinDocument
import cn.enaium.jimmer.buddy.lsp.utility.dto
import cn.enaium.jimmer.buddy.project.structure.Project
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.sql.*
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.treesitter.TSNode
import org.treesitter.TSPoint
import org.treesitter.TreeSitterKotlin

/**
 * @author Enaium
 */
class KotlinDocumentCompletionService(project: Project, documentManager: DocumentManager) :
    AbstractDocumentCompletionService(project, documentManager) {

    override suspend fun completion(params: CompletionParams): List<CompletionItem> {
        val document = documentManager.getDocument(params.textDocument.uri) as? KotlinDocument ?: return emptyList()
        val cursor = document.root.getNamedDescendantForPointRange(
            TSPoint(params.position.line, params.position.character),
            TSPoint(params.position.line, params.position.character)
        )
        val pkg = document.root.query(TreeSitterKotlin(), "(package_header (identifier) @name)").toMap()["name"]?.text(
            document.content
        )
        val name = cursor?.findParent("class_declaration")
            ?.query(TreeSitterKotlin(), "(class_declaration (type_identifier) @name)")
            ?.toMap()["name"]?.text(document.content) ?: return emptyList()
        val qualifiedName = pkg?.let { "$it.$name" } ?: name
        val annotation = cursor.findParent("annotation")
        val annotationName = annotation?.let {
            cursor.findParent("call_expression")
                ?.namedChild(0)?.text(document.content)
                ?: it.query(TreeSitterKotlin(), "(annotation (constructor_invocation (user_type) @name))")
                    .toMap()["name"]?.text(document.content)?.substringAfterLast(".")
        }

        val propertyName = cursor.findParent("property_declaration")?.let { property ->
            property.query(
                TreeSitterKotlin(),
                "(property_declaration (variable_declaration (simple_identifier) @name))"
            ).toMap()["name"]?.text(document.content)
        }

        if (cursor.type != "string_literal") {
            return emptyList()
        }

        context(document, qualifiedName) {
            when (annotationName) {
                OneToMany::class.simpleName, ManyToMany::class.simpleName, OneToOne::class.simpleName -> {
                    return cursor.mappedBy(propertyName ?: return emptyList())
                }

                IdView::class.simpleName -> {
                    return cursor.idView(propertyName ?: return emptyList())
                }

                OrderedProp::class.simpleName -> {
                    return cursor.orderedProp(propertyName ?: return emptyList())
                }

                Formula::class.simpleName -> {
                    return cursor.formula(propertyName ?: return emptyList())
                }
            }
        }
        return emptyList()
    }

    context(document: KotlinDocument, qualifiedName: String)
    fun TSNode.mappedBy(propertyName: String): List<CompletionItem> {
        val result = mutableListOf<CompletionItem>()
        if (this.parent()?.type == "value_argument") {
            if (this.prevNamedSibling()?.text(document.content) != "mappedBy") {
                return result
            }
        }
        project.dto.ofType(qualifiedName)?.properties[propertyName]?.targetType?.properties?.keys?.forEach {
            result.add(CompletionItem(it))
        }
        return result
    }

    context(document: KotlinDocument, qualifiedName: String)
    fun TSNode.idView(propertyName: String): List<CompletionItem> {
        val result = mutableListOf<CompletionItem>()
        if (this.parent()?.type == "value_argument") {
            if (this.prevNamedSibling().let { it == null || it.text(document.content) == "value" }) {
                project.dto.ofType(qualifiedName)?.properties?.keys?.filter { it != propertyName }?.forEach {
                    result.add(CompletionItem(it))
                }
            }
        }
        return result
    }

    context(document: KotlinDocument, qualifiedName: String)
    fun TSNode.orderedProp(propertyName: String): List<CompletionItem> {
        val result = mutableListOf<CompletionItem>()
        if (this.parent()?.parent()?.type == "value_argument") {
            if (this.parent()?.prevNamedSibling().let { it == null || it.text(document.content) == "value" }) {
                project.dto.ofType(qualifiedName)?.properties[propertyName]?.targetType?.properties?.keys?.forEach {
                    result.add(CompletionItem(it))
                }
            }
        }
        return result
    }

    context(document: KotlinDocument, qualifiedName: String)
    fun TSNode.formula(propertyName: String): List<CompletionItem> {
        val result = mutableListOf<CompletionItem>()
        if (this.parent()?.type == "value_argument") {
            if (this.parent()?.prevNamedSibling()?.text(document.content) != "dependencies") {
                return result
            }
        }

        val text = this.namedChild(0)?.text(document.content) ?: return result
        val trace = text.split(".")

        if (trace.size == 1) {
            project.dto.ofType(qualifiedName)?.properties?.keys?.filter { it != propertyName }?.forEach {
                result.add(CompletionItem(it))
            }
        } else {
            var lastImmutableType: ImmutableType? = null
            trace.forEachIndexed { index, propName ->
                if (index != trace.size - 1) {
                    lastImmutableType = project.dto.ofType(qualifiedName)?.properties[propName]?.targetType
                }
            }
            lastImmutableType?.properties?.keys?.filter { it != propertyName }?.forEach {
                result.add(CompletionItem(it))
            }
        }
        return result
    }
}