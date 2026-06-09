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

import cn.enaium.jimmer.buddy.dto.lang.Context
import cn.enaium.jimmer.buddy.dto.lang.ImmutableType
import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import cn.enaium.jimmer.buddy.lang.parser.utility.findParent
import cn.enaium.jimmer.buddy.lang.parser.utility.prevNamedSibling
import cn.enaium.jimmer.buddy.lang.parser.utility.query
import cn.enaium.jimmer.buddy.lang.parser.utility.text
import cn.enaium.jimmer.buddy.lsp.document.DocumentManager
import cn.enaium.jimmer.buddy.lsp.document.KotlinDocument
import cn.enaium.jimmer.buddy.project.structure.Project
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

    private val context = Context(project)

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
        val baseClass = project.environment.getIndex().findClass(pkg?.let { "$it.$name" } ?: name) ?: return emptyList()
        context(document, baseClass) {
            if (cursor.parent?.type == "value_argument") {
                cursor.prevNamedSibling()?.takeIf { it.type == "simple_identifier" }?.also { identifier ->
                    val argName = identifier.text(document.content)
                    when (argName) {
                        "mappedBy" -> return cursor.mappedBy()
                    }
                }
            }
        }
        return emptyList()
    }

    context(document: KotlinDocument, baseClass: BaseClassNode)
    fun TSNode.mappedBy(): List<CompletionItem> {
        val result = mutableListOf<CompletionItem>()
        this.findParent("property_declaration")?.also { property ->
            property.query(
                TreeSitterKotlin(),
                "(property_declaration (variable_declaration (simple_identifier) @name))"
            ).toMap()["name"]?.text(document.content)?.also { name ->
                ImmutableType(
                    context,
                    baseClass
                ).declaredProperties[name]?.targetType?.properties?.keys?.forEach {
                    result.add(CompletionItem(it))
                }
            }
        }
        return result
    }
}