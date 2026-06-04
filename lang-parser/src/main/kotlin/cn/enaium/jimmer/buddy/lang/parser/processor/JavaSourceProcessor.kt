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

import JavaLexer
import JavaParser
import cn.enaium.jimmer.buddy.lang.parser.index.ClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
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

    // ======================== Scan Phase (regex-based, lightweight) ========================

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

    // ======================== Parse Phase (full ANTLR4, immediate indexing) ========================

    private suspend fun parseFiles() = coroutineScope {
        fileEntries.map { entry ->
            launch(Dispatchers.Default) {
                parseAndIndex(entry.contentProvider(), entry.path)
            }
        }.joinAll()
    }

    private fun parseAndIndex(content: String, path: Path) {
        val javaLexer = JavaLexer(CharStreams.fromString(content))
        val javaParser = JavaParser(CommonTokenStream(javaLexer))
        javaParser.errorListeners.clear()
        val compilationUnit = javaParser.compilationUnit()

        val pkg = compilationUnit.packageDeclaration()?.qualifiedName()?.text ?: ""
        val imports = compilationUnit.importDeclaration().map { importDeclaration ->
            importDeclaration.qualifiedName().text.let { qualifiedName ->
                importDeclaration.MUL()?.let { "$qualifiedName.*" } ?: qualifiedName
            }
        }.toSet()

        val typeDeclarations = compilationUnit.typeDeclaration()
        compilationUnit.children?.clear()

        typeDeclarations.forEach { typeDeclaration ->
            val annotations = typeDeclaration.classOrInterfaceModifier()
                ?.mapNotNull { it.annotation()?.asAnnotationEntryNode(pkg, imports) }?.toSet() ?: emptySet()

            typeDeclaration.classDeclaration()?.also { classDeclarationContext ->
                val name = classDeclarationContext.identifier().text ?: return@forEach
                classIndex.upsertClass(
                    "$pkg.$name", ClassClassNode("$pkg.$name", path, annotations = annotations)
                )
            }

            typeDeclaration.interfaceDeclaration()?.also { interfaceDeclarationContext ->
                val name = interfaceDeclarationContext.identifier().text ?: return@forEach
                val supers = interfaceDeclarationContext.typeList()
                    ?.flatMap { it.typeType().map { it.asTypeNode(pkg, imports) } }
                    ?.toSet() ?: emptySet()

                val methods = interfaceDeclarationContext.interfaceBody()?.interfaceBodyDeclaration()
                    ?.mapNotNull { bodyDeclarationContext ->
                        return@mapNotNull bodyDeclarationContext.asMethodNode("$pkg.$name", pkg, imports)
                    }?.toSet() ?: emptySet()
                classIndex.upsertClass(
                    "$pkg.$name",
                    InterfaceNode("$pkg.$name", path, annotations, supers = supers, members = methods)
                )
            }

            typeDeclaration.enumDeclaration()?.also { enumDeclarationContext ->
                val name = enumDeclarationContext.identifier().text ?: return@forEach
                classIndex.upsertClass(
                    "$pkg.$name",
                    EnumClassNode(
                        "$pkg.$name",
                        path,
                        annotations,
                        entries = enumDeclarationContext.enumConstants()?.enumConstant()
                            ?.map { EnumEntryNode(it.identifier().text) }?.toSet() ?: emptySet()
                    )
                )
            }

            typeDeclaration.annotationTypeDeclaration()?.also { annotationTypeDeclarationContext ->
                val name = annotationTypeDeclarationContext.identifier().text ?: return@forEach
                classIndex.upsertClass("$pkg.$name", AnnotationClassNode("$pkg.$name", path, annotations))
            }

            typeDeclaration.children?.clear()
        }
    }

    // ======================== Helper methods (unchanged semantics) ========================

    private fun JavaParser.InterfaceBodyDeclarationContext.asMethodNode(
        className: String,
        pkg: String,
        imports: Set<String>
    ): MethodNode? {
        val interfaceMethodDeclaration =
            this.interfaceMemberDeclaration()?.interfaceMethodDeclaration()
                ?: return null
        val interfaceCommonBodyDeclaration =
            interfaceMethodDeclaration.interfaceCommonBodyDeclaration()
                ?: return null

        val name = interfaceCommonBodyDeclaration.identifier().text ?: return null
        val asTypeNode = interfaceCommonBodyDeclaration.typeTypeOrVoid()
            .asTypeNode(pkg, imports)

        val annotations = this.modifier()?.mapNotNull {
            it.classOrInterfaceModifier()?.annotation()?.asAnnotationEntryNode(pkg, imports)
        }?.toSet() ?: emptySet()

        return MethodNode(
            className,
            name,
            annotations,
            asTypeNode,
            interfaceMethodDeclaration.interfaceMethodModifier().any { it.DEFAULT() != null })
    }

    private fun JavaParser.TypeTypeOrVoidContext.asTypeNode(pkg: String, imports: Set<String>): TypeNode {
        this.typeType()?.asTypeNode(pkg, imports)?.also {
            return it
        }
        return TypeNode(this.text, false)
    }

    private fun JavaParser.TypeTypeContext.asTypeNode(pkg: String, imports: Set<String>): TypeNode {
        val array = this.LBRACK().isNotEmpty() && this.RBRACK().isNotEmpty()
        this.primitiveType()?.also { primitive ->
            return PrimitiveTypeNode(primitive.text, array)
        } ?: this.classOrInterfaceType()?.classType()?.also { classType ->
            val name = classType.typeIdentifier().firstOrNull()
                ?.let { id ->
                    classType.packageName()?.firstOrNull()?.let { pkg -> "${id.text}.${pkg.text}" } ?: id.text
                } ?: return@also
            val qualifiedName =
                findQualifiedName(pkg, imports, name)

            val arguments = classType.typeArguments().firstOrNull()?.typeArgument()
                ?.mapNotNull { it.typeType()?.asTypeNode(pkg, imports) ?: it.QUESTION()?.let { WildcardTypeNode() } }
                ?.toSet() ?: emptySet()

            return ClassTypeNode(classType.text, qualifiedName, array, false, arguments)
        }
        return TypeNode(this.text, array)
    }

    private fun JavaParser.AnnotationContext.asAnnotationEntryNode(
        pkg: String,
        imports: Set<String>
    ): AnnotationEntryNode {
        val name = this.qualifiedName().text
        val qualifiedName = findQualifiedName(pkg, imports, name)

        val arguments =
            this.annotationFieldValues()?.annotationFieldValue()
                ?.mapNotNull { it.asAnnotationArgumentNode(pkg, imports) }
                ?.toSet() ?: emptySet()

        return AnnotationEntryNode(name, qualifiedName, arguments)
    }

    private fun JavaParser.AnnotationFieldValueContext.asAnnotationArgumentNode(
        pkg: String,
        imports: Set<String>
    ): AnnotationArgumentNode? {
        this.annotationValue()?.also { annotationValue ->
            annotationValue.expression()?.also { expression ->
                when (expression) {
                    // named argument
                    is JavaParser.BinaryOperatorExpressionContext -> {
                        val name =
                            (expression.expression().getOrNull(0) as? JavaParser.PrimaryExpressionContext)?.primary()
                                ?.identifier()?.text ?: return@also
                        val valueExpression =
                            (expression.expression().getOrNull(1) as? JavaParser.PrimaryExpressionContext)?.primary()
                                ?: return@also
                        return AnnotationArgumentNode(name, valueExpression)
                    }

                    // unnamed argument
                    is JavaParser.PrimaryExpressionContext -> {
                        val valueExpression = expression.primary() ?: return@also
                        return AnnotationArgumentNode("value", valueExpression.asAny(pkg, imports))
                    }
                }
            }

            // array
            val values = annotationValue.annotationValue()
                .mapNotNull {
                    (it.expression() as? JavaParser.PrimaryExpressionContext)?.primary()?.asAny(pkg, imports)
                }

            return AnnotationArgumentNode(this.identifier()?.text ?: "value", values)
        }
        return null
    }

    private fun JavaParser.PrimaryContext.asAny(
        pkg: String,
        imports: Set<String>
    ): Any? {
        this.literal()?.also { literal ->
            literal.CHAR_LITERAL()?.also {
                return it.text.substringAfter("'").substringBeforeLast("'")
            }
            literal.STRING_LITERAL()?.also {
                return it.text.substringAfter('"').substringBeforeLast('"')
            }
            literal.BOOL_LITERAL()?.also {
                return it.text.toBoolean()
            }
            literal.floatLiteral()?.also {
                return it.text.toFloat()
            }
            literal.integerLiteral()?.also {
                return it.text.toInt()
            }
        }
        this.typeTypeOrVoid()?.asTypeNode(pkg, imports)?.also {
            return it
        }
        return null
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
