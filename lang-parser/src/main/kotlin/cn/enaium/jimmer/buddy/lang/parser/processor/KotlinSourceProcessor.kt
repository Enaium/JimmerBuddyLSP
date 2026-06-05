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

package cn.enaium.jimmer.buddy.lang.parser.processor

import cn.enaium.jimmer.buddy.lang.parser.index.ClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.*
import cn.enaium.jimmer.buddy.lang.parser.utility.text
import cn.enaium.jimmer.buddy.lang.parser.utility.types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.treesitter.TSNode
import org.treesitter.TSParser
import org.treesitter.TSQuery
import org.treesitter.TSQueryCursor
import org.treesitter.TSQueryMatch
import org.treesitter.TreeSitterKotlin
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * @author Enaium
 */
class KotlinSourceProcessor(val sourceDirOrJar: Set<Path>, private val classIndex: ClassIndex) {

    // All qualified names discovered during the scan phase, used for type resolution
    private val allQualifiedNames = ConcurrentHashMap.newKeySet<String>()

    // Files to fully parse in the second phase
    private val fileEntries = Collections.synchronizedList(mutableListOf<FileEntry>())

    suspend fun process(): ClassIndex = coroutineScope {
        JavaSourceProcessor(sourceDirOrJar, classIndex).process()
        scanSource()
        parseFiles()
        return@coroutineScope classIndex
    }

    private suspend fun scanSource() = coroutineScope {
        sourceDirOrJar.mapNotNull { entry ->
            if (!entry.exists()) {
                return@mapNotNull null
            }
            launch(Dispatchers.IO) {
                when {
                    entry.isDirectory() -> scanDir(entry)
                    entry.extension == "jar" -> scanJar(entry)
                    entry.extension == "kt" -> scanFile(entry)
                }
            }
        }.joinAll()
    }

    private fun scanDir(sourceDir: Path) {
        sourceDir.walk().forEach { file ->
            if (file.extension == "kt") {
                scanFile(file)
            }
        }
    }

    private fun scanJar(path: Path) {
        JarFile(path.toFile()).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".kt")) continue
                val entryName = entry.name
                val content = jar.getInputStream(entry).readAllBytes().decodeToString()
                addFile(content, path / entryName) {
                    JarFile(path.toFile()).use { j ->
                        j.getInputStream(j.getEntry(entryName)).readAllBytes().decodeToString()
                    }
                }
            }
        }
    }

    private fun scanFile(filePath: Path) {
        val content = filePath.readText()
        addFile(content, filePath) { filePath.readText() }
    }

    private fun addFile(content: String, path: Path, contentProvider: () -> String) {
        val qualifiedNames = extractQualifiedNames(content)
        if (qualifiedNames.isEmpty()) return
        allQualifiedNames.addAll(qualifiedNames)
        fileEntries.add(FileEntry(path, contentProvider))
    }

    /**
     * Extract qualified names from Kotlin source content using regex (no ANTLR).
     */
    private fun extractQualifiedNames(content: String): List<String> {
        val clean = stripComments(content)
        val pkg = KOTLIN_PACKAGE_REGEX.find(clean)?.groupValues?.get(1)
        val typeNames = KOTLIN_TYPE_REGEX.findAll(clean).map { it.groupValues[1] }.toList()
        return typeNames.map { name ->
            if (pkg != null) "$pkg.$name" else name
        }
    }

    private suspend fun parseFiles() = coroutineScope {
        fileEntries.map { entry ->
            launch(Dispatchers.Default) {
                parseAndIndex(entry.contentProvider(), entry.path)
            }
        }.joinAll()
    }

    private fun parseAndIndex(content: String, path: Path) {
        val parser = TSParser()
        val language = TreeSitterKotlin()
        parser.setLanguage(language)
        val parse = parser.parseString(null, content)
        val query = TSQuery(
            language, """(package_header) @package
            |(import_header) @import
            |(class_declaration) @class
            """.trimMargin()
        )
        val cursor = TSQueryCursor()
        cursor.exec(query, parse.rootNode)
        val match = TSQueryMatch()

        var pkg = ""
        val imports = mutableSetOf<String>()

        while (cursor.nextMatch(match)) {
            match.captures.forEach { capture ->
                val captureName = query.getCaptureNameForId(capture.index)
                when (captureName) {
                    "package" -> {
                        pkg = capture.node.types("identifier").firstOrNull()
                            ?.let { node ->
                                node.types("simple_identifier").mapNotNull { it.text(content) }
                                    .joinToString(".")
                            } ?: ""
                    }

                    "import" -> {
                        capture.node.types("identifier").firstOrNull()?.let { id ->
                            val name = id.types("simple_identifier").mapNotNull { it.text(content) }
                                .joinToString(".")
                            imports.add(
                                if (capture.node.types("wildcard_import").isNotEmpty()) "$name.*"
                                else name
                            )
                        }
                    }

                    "class" -> {
                        val name = capture.node.types("type_identifier").firstOrNull()?.text(content)
                            ?: return@forEach
                        val qualifiedName = "$pkg.$name"
                        val annotations = capture.node.types("modifiers").firstOrNull()
                            ?.types("annotation")
                            ?.mapNotNull { it.asAnnotationEntryNode(content, pkg, imports) }
                            ?.toSet() ?: emptySet()

                        val keyword = (0 until capture.node.childCount)
                            .map { capture.node.getChild(it) }
                            .firstOrNull { it.type in setOf("interface", "enum", "class") }
                            ?.type

                        when (keyword) {
                            "interface" -> {
                                val supers = capture.node.types("delegation_specifier")
                                    .mapNotNull { it.asTypeNode(content, pkg, imports) }
                                    .toSet()

                                val properties = capture.node.types("class_body").firstOrNull()
                                    ?.types("property_declaration")
                                    .orEmpty()
                                    .mapNotNull { it.asPropertyNode(content, qualifiedName, pkg, imports) }
                                    .toSet()

                                classIndex.upsertClass(
                                    qualifiedName,
                                    InterfaceNode(
                                        qualifiedName, path, annotations,
                                        supers = supers, members = properties
                                    )
                                )
                            }

                            "enum" -> {
                                val entries = capture.node.types("enum_entry")
                                    .mapNotNull {
                                        it.types("type_identifier").firstOrNull()?.text(content)
                                            ?.let { n -> EnumEntryNode(n) }
                                    }.toSet()

                                classIndex.upsertClass(
                                    qualifiedName,
                                    EnumClassNode(qualifiedName, path, annotations, entries)
                                )
                            }

                            "class" -> {
                                val modifierNodes = capture.node.types("modifiers").firstOrNull()
                                val modifierKeywords = if (modifierNodes != null) {
                                    (0 until modifierNodes.childCount)
                                        .map { modifierNodes.getChild(it).type }.toSet()
                                } else emptySet()

                                when {
                                    "annotation" in modifierKeywords -> {
                                        classIndex.upsertClass(
                                            qualifiedName,
                                            AnnotationClassNode(qualifiedName, path, annotations)
                                        )
                                    }

                                    "data" in modifierKeywords -> {
                                        val parameters = capture.node.types("class_parameter")
                                            .mapNotNull { param ->
                                                val paramName = param.types("simple_identifier").firstOrNull()
                                                    ?.text(content) ?: return@mapNotNull null
                                                val paramType = param.types("type").firstOrNull()
                                                    ?.asTypeNode(content, pkg, imports)
                                                    ?: return@mapNotNull null
                                                paramName to paramType
                                            }.toMap()
                                        classIndex.upsertClass(
                                            qualifiedName,
                                            DataClassNode(qualifiedName, path, annotations, parameters)
                                        )
                                    }

                                    else -> {
                                        classIndex.upsertClass(
                                            qualifiedName,
                                            ClassNode(qualifiedName, path, annotations)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun TSNode.asPropertyNode(
        content: String,
        className: String,
        pkg: String,
        imports: Set<String>
    ): PropertyNode? {
        val bindingPatternKind = this.types("binding_pattern_kind").firstOrNull() ?: return null
        val variableDecl = this.types("variable_declaration").firstOrNull() ?: return null
        val name = variableDecl.types("simple_identifier").firstOrNull()?.text(content) ?: return null
        val type = (variableDecl.types("user_type").firstOrNull()
            ?: variableDecl.types("nullable_type").firstOrNull())
            ?.asTypeNode(content, pkg, imports) ?: return null
        val annotations = this.types("modifiers").firstOrNull()
            ?.types("annotation")
            ?.mapNotNull { it.asAnnotationEntryNode(content, pkg, imports) }
            ?.toSet() ?: emptySet()
        return PropertyNode(className, name, type, annotations)
    }

    private fun TSNode.asTypeNode(content: String, pkg: String, imports: Set<String>): TypeNode? {
        return when (this.type) {
            "user_type" -> {
                val identifiers = this.types("type_identifier").mapNotNull { it.text(content) }
                if (identifiers.isEmpty()) return null
                val name = identifiers.joinToString(".")
                val qualifiedName = findQualifiedName(pkg, imports, name)
                val arguments = mutableSetOf<TypeNode>()
                this.types("type_arguments").firstOrNull()?.let { typeArgs ->
                    for (i in 0 until typeArgs.childCount) {
                        val child = typeArgs.getChild(i)
                        child.asTypeNode(content, pkg, imports)?.let { arguments.add(it) }
                    }
                }
                ClassTypeNode(name, qualifiedName, arguments = arguments)
            }

            "nullable_type" -> {
                val inner = this.types("user_type").firstOrNull()?.asTypeNode(content, pkg, imports) ?: return null
                if (inner is ClassTypeNode) {
                    ClassTypeNode(inner.name, inner.qualifiedName, nullable = true, array = inner.array, arguments = inner.arguments)
                } else {
                    inner
                }
            }

            "type_projection" -> {
                this.types("user_type").firstOrNull()?.asTypeNode(content, pkg, imports)
            }

            "type" -> {
                this.types("user_type").firstOrNull()?.asTypeNode(content, pkg, imports)
                    ?: this.types("type_identifier").firstOrNull()?.let {
                        val name = it.text(content) ?: return@let null
                        ClassTypeNode(name, findQualifiedName(pkg, imports, name))
                    }
            }

            "type_identifier" -> {
                val name = this.text(content) ?: return null
                ClassTypeNode(name, findQualifiedName(pkg, imports, name))
            }

            "delegation_specifier" -> {
                this.types("user_type").firstOrNull()?.asTypeNode(content, pkg, imports)
                    ?: this.types("constructor_invocation").firstOrNull()
                        ?.types("user_type")?.firstOrNull()?.asTypeNode(content, pkg, imports)
            }

            else -> null
        }
    }

    private fun TSNode.asAnnotationEntryNode(
        content: String,
        pkg: String,
        imports: Set<String>
    ): AnnotationEntryNode? {
        // Annotations with arguments: annotation wraps @ + constructor_invocation
        // Annotations without arguments: annotation wraps @ + user_type
        val invocation = this.types("constructor_invocation").firstOrNull()
        val userType = invocation?.types("user_type")?.firstOrNull()
            ?: this.types("user_type").firstOrNull() ?: return null
        val identifiers = userType.types("type_identifier").mapNotNull { it.text(content) }
        if (identifiers.isEmpty()) return null
        val name = identifiers.joinToString(".")
        val qualifiedName = findQualifiedName(pkg, imports, name)
        val arguments = if (invocation != null) {
            invocation.types("value_arguments").firstOrNull()
                ?.types("value_argument")
                ?.mapNotNull { it.asAnnotationArgumentNode(content, pkg, imports) }
                ?.toSet() ?: emptySet()
        } else {
            emptySet()
        }
        return AnnotationEntryNode(name, qualifiedName, arguments)
    }

    private fun TSNode.asAnnotationArgumentNode(
        content: String,
        pkg: String,
        imports: Set<String>
    ): AnnotationArgumentNode? {
        val key = this.types("simple_identifier").firstOrNull()?.text(content) ?: "value"
        val value = this.asAnnotationValue(content)
        return AnnotationArgumentNode(key, value)
    }

    private fun TSNode.asAnnotationValue(content: String): Any? {
        return when (this.type) {
            "string_literal" -> this.text(content)?.substringAfter("\"")?.substringBeforeLast("\"")
            "integer_literal" -> this.text(content)?.toIntOrNull()
            "boolean_literal" -> this.text(content)?.toBoolean()
            "null_literal" -> null
            "simple_identifier" -> this.text(content)
            "scoped_identifier" -> this.text(content)
            "real_literal" -> this.text(content)?.toFloatOrNull()
            else -> {
                // Check children for value types
                for (i in 0 until this.childCount) {
                    val child = this.getChild(i)
                    child.asAnnotationValue(content)?.let { return it }
                }
                null
            }
        }
    }

    /**
     * [imports] Current imports
     * [name] Simple name
     */
    private fun findQualifiedName(pkg: String, imports: Set<String>, name: String): String? {
        // The name is a qualified name if it has dot
        name.takeIf { it.contains(".") }?.also { return it }
        // The package name and the name are a qualified name if it is known.
        "$pkg.$name".takeIf {
            it in allQualifiedNames || classIndex.findClass(it) != null
        }?.also { return it }
        // The import is qualified name if its suffix is same the name
        imports.find { it.endsWith(name) }?.also { return it }
        // Wildcard imports: expand each and check against known qualified names
        imports.filter { it.endsWith("*") }.forEach { import ->
            val qualifiedName = "${import.substringBeforeLast(".*")}.$name"
            if (qualifiedName in allQualifiedNames || classIndex.findClass(qualifiedName) != null) {
                return qualifiedName
            }
        }
        return null
    }

    private class FileEntry(
        val path: Path,
        val contentProvider: () -> String
    )

    companion object {
        private val KOTLIN_PACKAGE_REGEX = Regex(
            """^\s*package\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)""",
            RegexOption.MULTILINE
        )
        private val KOTLIN_TYPE_REGEX = Regex(
            """^\s*(?:(?:public|private|internal|protected|abstract|open|data|sealed|inner|actual|expect|enum|annotation)\s+)*(?:class|interface|object)\s+([A-Za-z_]\w*)""",
            RegexOption.MULTILINE
        )

        private fun stripComments(content: String): String {
            val noBlock = content.replace(BLOCK_COMMENT_REGEX, " ")
            return noBlock.replace(LINE_COMMENT_REGEX, " ")
        }

        private val BLOCK_COMMENT_REGEX = Regex("/\\*[\\s\\S]*?\\*/")
        private val LINE_COMMENT_REGEX = Regex("//[^\n]*")
    }
}
