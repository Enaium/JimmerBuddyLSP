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
import cn.enaium.jimmer.buddy.lang.parser.node.AnnotationArgumentNode
import cn.enaium.jimmer.buddy.lang.parser.node.AnnotationClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.AnnotationEntryNode
import cn.enaium.jimmer.buddy.lang.parser.node.ClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.ClassTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.EnumClassNode
import cn.enaium.jimmer.buddy.lang.parser.node.EnumEntryNode
import cn.enaium.jimmer.buddy.lang.parser.node.InterfaceNode
import cn.enaium.jimmer.buddy.lang.parser.node.MethodNode
import cn.enaium.jimmer.buddy.lang.parser.node.PrimitiveTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.TypeNode
import cn.enaium.jimmer.buddy.lang.parser.utility.field
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
import org.treesitter.TreeSitterJava
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.*


/**
 * @author Enaium
 */
class JavaSourceProcessor(val sourceDirOrJar: Set<Path>, private val classIndex: ClassIndex) {

    // All qualified names discovered during the scan phase, used for type resolution
    private val allQualifiedNames = ConcurrentHashMap.newKeySet<String>()

    // Files to fully parse in the second phase
    private val fileEntries = Collections.synchronizedList(mutableListOf<FileEntry>())

    suspend fun process(): ClassIndex = coroutineScope {
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
                    entry.extension == "zip" -> scanJdkZip(entry)
                    entry.extension == "java" -> scanFile(entry)
                }
            }
        }.joinAll()
    }

    private fun scanDir(sourceDir: Path) {
        sourceDir.walk().forEach { file ->
            if (file.extension == "java") {
                scanFile(file)
            }
        }
    }

    private fun scanJar(path: Path) {
        JarFile(path.toFile()).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".java")) continue
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

    private fun scanJdkZip(path: Path) {
        val relevantPrefixes = listOf("java.base/java/util", "java.base/java/lang", "java.base/java/time")
        ZipFile(path.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (relevantPrefixes.none { entry.name.startsWith(it) } || !entry.name.endsWith(".java")) continue
                val entryName = entry.name
                val content = zip.getInputStream(entry).readAllBytes().decodeToString()
                addFile(content, path / entryName) {
                    ZipFile(path.toFile()).use { z ->
                        z.getInputStream(z.getEntry(entryName)).readAllBytes().decodeToString()
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
     * Extract qualified names from Java source content using regex (no ANTLR).
     */
    private fun extractQualifiedNames(content: String): List<String> {
        val clean = stripComments(content)
        val pkg = PACKAGE_REGEX.find(clean)?.groupValues?.get(1) ?: return emptyList()
        val typeNames = TYPE_REGEX.findAll(clean).map { it.groupValues[1] }.toList()
        return typeNames.map { "$pkg.$it" }
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
        val language = TreeSitterJava()
        parser.setLanguage(language)
        val parse = parser.parseString(null, content)
        val query = TSQuery(
            language, """(package_declaration) @package
            |(import_declaration)  @import
            |(class_declaration) @class
            |(interface_declaration) @interface
            |(enum_declaration) @enum
            |(annotation_type_declaration) @annotation
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
                        pkg =
                            capture.node.types("identifier", "scoped_identifier").mapNotNull { it.text(content) }.firstOrNull()
                                ?: ""
                    }

                    "import" -> {
                        capture.node.types("identifier", "scoped_identifier").mapNotNull { it.text(content) }.firstOrNull()
                            ?.let { name ->
                                imports.add(
                                    if (capture.node.types("asterisk").isNotEmpty()) {
                                        "$name.*"
                                    } else {
                                        name
                                    }
                                )
                            }
                    }

                    "class", "interface", "enum", "annotation" -> {
                        val name = capture.node.field("name")?.text(content) ?: return@forEach
                        val qualifiedName = "$pkg.$name"
                        val annotations = capture.node.types("modifiers").firstOrNull()
                            ?.types("marker_annotation", "annotation")
                            ?.mapNotNull { it.asAnnotationEntryNode(content, pkg, imports) }
                            ?.toSet() ?: emptySet()

                        when (captureName) {
                            "class" -> {
                                classIndex.upsertClass(
                                    qualifiedName,
                                    ClassNode(qualifiedName, path, annotations)
                                )
                            }

                            "interface" -> {
                                val supers = capture.node.types("extends_interfaces")
                                    .flatMap { it.types("type_list") }
                                    .flatMap { tl ->
                                        (0 until tl.childCount).mapNotNull { i ->
                                            tl.getChild(i).asTypeNode(content, pkg, imports)
                                        }
                                    }.toSet()

                                val methods = capture.node.field("body")?.types("method_declaration")
                                    ?.mapNotNull { it.asMethodNode(content, pkg, imports, qualifiedName) }
                                    ?.toSet() ?: emptySet()

                                classIndex.upsertClass(
                                    qualifiedName,
                                    InterfaceNode(
                                        qualifiedName, path, annotations,
                                        supers = supers, members = methods
                                    )
                                )
                            }

                            "enum" -> {
                                val entries = capture.node.field("body")?.types("enum_constant")
                                    ?.mapNotNull {
                                        it.field("name")?.text(content)?.let { name -> EnumEntryNode(name) }
                                    }?.toSet() ?: emptySet()

                                classIndex.upsertClass(
                                    qualifiedName,
                                    EnumClassNode(qualifiedName, path, annotations, entries)
                                )
                            }

                            "annotation" -> {
                                classIndex.upsertClass(
                                    qualifiedName,
                                    AnnotationClassNode(qualifiedName, path, annotations)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun TSNode.asMethodNode(
        content: String,
        pkg: String,
        imports: Set<String>,
        classQualifiedName: String
    ): MethodNode? {
        val name = this.field("name")?.text(content) ?: return null
        val type = this.field("type")?.asTypeNode(content, pkg, imports) ?: return null
        val annotations = this.types("modifiers").firstOrNull()
            ?.types("marker_annotation", "annotation")
            ?.mapNotNull { it.asAnnotationEntryNode(content, pkg, imports) }
            ?.toSet() ?: emptySet()
        val nullable = annotations.any { it.name == "Nullable" || (it.qualifiedName?.endsWith(".Nullable") == true) }
        val effectiveType = if (nullable && type is ClassTypeNode) {
            ClassTypeNode(type.name, type.qualifiedName, nullable = true, array = type.array, arguments = type.arguments)
        } else type
        val isDefault = this.types("modifiers").firstOrNull()
            ?.types("default")?.isNotEmpty() == true
        return MethodNode(
            className = classQualifiedName, name, type = effectiveType,
            annotations = annotations, default = isDefault
        )
    }

    fun TSNode.asTypeNode(content: String, pkg: String, imports: Set<String>, array: Boolean = false): TypeNode? {
        when (this.type) {
            "array_type" -> {
                return this.field("element")?.asTypeNode(content, pkg, imports, array = true)
            }

            "integral_type" -> {
                val t = this.text(content) ?: return null
                return PrimitiveTypeNode(t, array = array)
            }

            "floating_point_type" -> {
                val t = this.text(content) ?: return null
                return PrimitiveTypeNode(t, array = array)
            }

            "boolean_type" -> {
                return PrimitiveTypeNode("boolean", array = array)
            }

            "void_type" -> {
                return TypeNode("void", false)
            }

            "identifier", "scoped_type_identifier", "type_identifier" -> {
                val name = this.text(content) ?: return null
                return ClassTypeNode(name, findQualifiedName(pkg, imports, name))
            }

            "generic_type" -> {
                val name = this.types("type_identifier").firstOrNull()?.text(content) ?: return null
                val arguments = mutableSetOf<TypeNode>()
                this.types("type_arguments").firstOrNull()?.let { typeArgs ->
                    for (i in 0 until typeArgs.childCount) {
                        val child = typeArgs.getChild(i)
                        child.asTypeNode(content, pkg, imports)?.let { arguments.add(it) }
                    }
                }
                return ClassTypeNode(
                    name, findQualifiedName(pkg, imports, name), array = array, arguments = arguments
                )
            }
        }
        return null
    }

    fun TSNode.asAnnotationEntryNode(content: String, pkg: String, imports: Set<String>): AnnotationEntryNode? {
        val name = this.field("name")?.text(content) ?: return null
        val arguments = this.field("arguments")?.types("element_value_pair")
            ?.mapNotNull { it.asAnnotationArgumentNode(content, pkg, imports) }?.toSet() ?: emptySet()
        return AnnotationEntryNode(name, findQualifiedName(pkg, imports, name), arguments)
    }

    fun TSNode.asAnnotationArgumentNode(content: String, pkg: String, imports: Set<String>): AnnotationArgumentNode? {
        val key = this.field("key")?.text(content) ?: return null
        val value = this.field("value")?.asAnnotationValue(content)
        return AnnotationArgumentNode(key, value)
    }

    private fun TSNode.asAnnotationValue(content: String): Any? {
        return when (this.type) {
            "string_literal" -> this.text(content)?.substringAfter('"')?.substringBeforeLast('"')
            "char_literal" -> this.text(content)?.substringAfter('\'')?.substringBeforeLast('\'')
            "decimal_integer_literal" -> this.text(content)?.toIntOrNull()
            "decimal_floating_point_literal" -> this.text(content)?.toFloatOrNull()
            "hex_integer_literal" -> this.text(content)?.removePrefix("0x")?.removePrefix("0X")?.removeSuffix("L")
                ?.toIntOrNull(16)

            "octal_integer_literal" -> this.text(content)?.removePrefix("0")?.toIntOrNull(8)
            "binary_integer_literal" -> this.text(content)?.removePrefix("0b")?.removePrefix("0B")?.toIntOrNull(2)
            "true" -> true
            "false" -> false
            "null_literal" -> null
            "identifier" -> this.text(content)
            "scoped_identifier" -> this.text(content)
            "element_value_array_initializer" -> {
                (0 until this.childCount).mapNotNull { i ->
                    this.getChild(i).asAnnotationValue(content)
                }
            }

            else -> this.text(content)
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
        "$pkg.$name".takeIf { it in allQualifiedNames || classIndex.findClass(it) != null }?.also { return it }
        // The import is qualified name if its suffix is same the name
        imports.find { it.endsWith(name) }?.also { return it }
        // Wildcard imports: expand each and check against known qualified names
        imports.filter { it.endsWith("*") }.forEach { import ->
            val qualifiedName = "${import.substringBeforeLast(".*")}.${name}"
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
        private val PACKAGE_REGEX =
            Regex("""^\s*package\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*;""", RegexOption.MULTILINE)
        private val TYPE_REGEX = Regex(
            """^\s*(?:public\s+)?(?:abstract\s+|final\s+)?(?:class|interface|enum|@interface)\s+([a-zA-Z_]\w*)""",
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
