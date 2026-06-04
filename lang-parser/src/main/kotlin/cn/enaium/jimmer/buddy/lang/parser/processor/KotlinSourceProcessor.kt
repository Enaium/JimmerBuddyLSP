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

import KotlinLexer
import KotlinParser
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

    // ======================== Parse Phase (full ANTLR4, immediate indexing) ========================

    private suspend fun parseFiles() = coroutineScope {
        fileEntries.map { entry ->
            launch(Dispatchers.Default) {
                parseAndIndex(entry.contentProvider(), entry.path)
            }
        }.joinAll()
    }

    private fun parseAndIndex(content: String, path: Path) {
        val kotlinLexer = KotlinLexer(CharStreams.fromString(content))
        val kotlinParser = KotlinParser(CommonTokenStream(kotlinLexer))
        kotlinLexer.removeErrorListeners()
        kotlinParser.removeErrorListeners()
        val kotlinFile = kotlinParser.kotlinFile()

        val pkg = kotlinFile.preamble()?.packageHeader()?.identifier()?.text ?: return
        val imports = kotlinFile.preamble()?.importList()?.importHeader()?.map { importHeader ->
            importHeader.identifier().text.let { identifier ->
                importHeader.MULT()?.let { "$identifier.*" } ?: identifier
            }
        }?.toSet() ?: emptySet()

        val topLevelObjects = kotlinFile.topLevelObject()
        kotlinFile.children?.clear()

        topLevelObjects.forEach { topLevelObject ->
            topLevelObject.classDeclaration()?.also { classDeclarationContext ->
                val name = classDeclarationContext.simpleIdentifier()?.text ?: return@forEach
                val annotations =
                    classDeclarationContext.modifierList()?.annotations()
                        ?.map { it.annotation().asAnnotationEntryNode(pkg, imports) }?.toSet()
                        ?: emptySet()

                // class
                classDeclarationContext.CLASS()?.also {
                    // enum
                    classDeclarationContext.modifierList()?.modifier()
                        ?.any { it.classModifier()?.ENUM() != null }
                        ?.also {
                            val entries = classDeclarationContext.enumClassBody()?.enumEntries()?.enumEntry()
                                ?.mapNotNull { EnumEntryNode(it.simpleIdentifier().text) }?.toSet() ?: emptySet()
                            classIndex.upsertClass(
                                "$pkg.$name",
                                EnumClassNode("$pkg.$name", path, annotations, entries)
                            )
                        } ?: run {
                        classIndex.upsertClass("$pkg.$name", ClassClassNode("$pkg.$name", path))
                    }
                    classDeclarationContext.modifierList()?.modifier()
                        ?.any { it.classModifier()?.ANNOTATION() != null }?.also {
                            classIndex.upsertClass("$pkg.$name", AnnotationClassNode("$pkg.$name", path, annotations))
                        }
                    classDeclarationContext.modifierList()?.modifier()
                        ?.any { it.classModifier()?.DATA() != null }
                        ?.also {
                            val parameters =
                                classDeclarationContext.primaryConstructor()?.classParameters()?.classParameter()
                                    ?.mapNotNull {
                                        it.simpleIdentifier().text to (it.type().asTypeNode(pkg, imports)
                                            ?: return@mapNotNull null)
                                    }?.toMap() ?: emptyMap()
                            classIndex.upsertClass(
                                "$pkg.$name",
                                DataClassNode("$pkg.$name", path, annotations, parameters)
                            )
                        }
                }

                // interface
                classDeclarationContext.INTERFACE()?.also {
                    val supers = classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()?.mapNotNull {
                        it.asTypeNode(pkg, imports)
                    }?.toSet() ?: emptySet()

                    val properties = classDeclarationContext.classBody()?.classMemberDeclaration()
                        ?.mapNotNull { it.propertyDeclaration()?.asPropertyNode("$pkg.$name", pkg, imports) }?.toSet()
                        ?: classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()?.lastOrNull()
                            ?.constructorInvocation()?.callSuffix()?.annotatedLambda()?.firstOrNull()
                            ?.functionLiteral()?.statements()?.statement()
                            ?.mapNotNull {
                                it.declaration()?.propertyDeclaration()?.asPropertyNode("$pkg.$name", pkg, imports)
                            }
                            ?.toSet() ?: emptySet()

                    classIndex.upsertClass(
                        "$pkg.$name",
                        InterfaceNode("$pkg.$name", path, annotations, supers = supers, members = properties)
                    )
                }
            }

            topLevelObject.children?.clear()
        }
    }

    // ======================== Helper methods ========================

    private fun KotlinParser.PropertyDeclarationContext.asPropertyNode(
        className: String,
        pkg: String,
        imports: Set<String>
    ): PropertyNode? {
        val name = this.variableDeclaration()?.simpleIdentifier()?.text ?: return null
        val asTypeNode = this.variableDeclaration().type()?.asTypeNode(pkg, imports) ?: return null
        val annotations =
            this.modifierList()?.annotations()?.map { it.annotation().asAnnotationEntryNode(pkg, imports) }?.toSet()
                ?: emptySet()
        return PropertyNode(className, name, asTypeNode, annotations, this.getter() != null, this.setter() != null)
    }

    private fun KotlinParser.TypeContext.asTypeNode(pkg: String, imports: Set<String>): TypeNode? {
        val simpleUserType =
            (this.typeReference() ?: this.nullableType()?.typeReference())?.userType()?.simpleUserType() ?: return null
        val nullable = this.nullableType() != null
        val name = simpleUserType.map { it.simpleIdentifier() }.joinToString(".") { it.text }
        val qualifiedName =
            findQualifiedName(pkg, imports, name)
        val arguments =
            simpleUserType.lastOrNull()?.typeArguments()?.typeProjection()
                ?.mapNotNull { it.type()?.asTypeNode(pkg, imports) }?.toSet() ?: emptySet()
        return ClassTypeNode(name, qualifiedName, nullable, name.endsWith("Array"), arguments)
    }

    private fun KotlinParser.AnnotationContext.asAnnotationEntryNode(
        pkg: String,
        imports: Set<String>
    ): AnnotationEntryNode {
        val name =
            this.LabelReference()?.text?.substringAfter("@") + this.simpleIdentifier().joinToString(".") { it.text }
        val qualifiedName = findQualifiedName(pkg, imports, name)
        val arguments =
            this.valueArguments()?.valueArgument()?.map { it.asAnnotationArgumentNode(pkg, imports) }?.toSet()
                ?: emptySet()

        return AnnotationEntryNode(name, qualifiedName, arguments)
    }

    private fun KotlinParser.ValueArgumentContext.asAnnotationArgumentNode(
        pkg: String,
        imports: Set<String>
    ): AnnotationArgumentNode {
        val name = this.simpleIdentifier()?.text ?: "value"
        val value = this.expression()?.asAny(pkg, imports)
        return AnnotationArgumentNode(name, value)
    }

    private fun KotlinParser.ExpressionContext.asAny(pkg: String, imports: Set<String>): Any? {
        val postfixUnaryExpression =
            this.disjunction()?.firstOrNull()?.conjunction()?.firstOrNull()?.equalityComparison()
                ?.firstOrNull()?.comparison()?.firstOrNull()?.namedInfix()?.firstOrNull()?.elvisExpression()
                ?.firstOrNull()
                ?.infixFunctionCall()?.firstOrNull()?.rangeExpression()?.firstOrNull()?.additiveExpression()
                ?.firstOrNull()
                ?.multiplicativeExpression()?.firstOrNull()?.typeRHS()?.firstOrNull()?.prefixUnaryExpression()
                ?.firstOrNull()?.postfixUnaryExpression()

        postfixUnaryExpression?.atomicExpression()?.also { atomicExpression ->
            atomicExpression.literalConstant()?.let {
                return it.stringLiteral()
                    ?.let { stringLiteral ->
                        stringLiteral.lineStringLiteral()?.lineStringContent()
                            ?.joinToString { lineStringContentContext -> lineStringContentContext.text }
                            ?: stringLiteral.multiLineStringLiteral()?.lineStringLiteral()
                                ?.joinToString { content -> content.text }
                    }
                    ?: it.IntegerLiteral()?.text?.toInt()
                    ?: it.RealLiteral()?.text?.toDouble()
                    ?: it.BooleanLiteral()?.text?.toBoolean()
                    ?: it.LongLiteral()?.text?.toLong()
                    ?: it.HexLiteral()?.text?.toInt()
                    ?: it.BinLiteral()?.text?.toInt()
                    ?: it.CharacterLiteral()?.text?.toInt()?.toChar()
            }
            atomicExpression.collectionLiteral()?.let { collection ->
                return collection.expression()?.map { it.asAny(pkg, imports) }
            }
        }

        if (postfixUnaryExpression?.let {
                it.atomicExpression() != null && it.postfixUnaryOperation().isNotEmpty()
            } == true) {
            postfixUnaryExpression.asTypeNode(pkg, imports)
        }
        return null
    }

    private fun KotlinParser.DelegationSpecifierContext.asTypeNode(pkg: String, imports: Set<String>): TypeNode? {
        val name = (this.userType() ?: this.constructorInvocation()?.userType())?.simpleUserType()
            ?.map { it.simpleIdentifier() }?.joinToString(".") { it.text } ?: return null
        val qualifiedName = findQualifiedName(pkg, imports, name)
        return ClassTypeNode(name, qualifiedName)
    }

    private fun KotlinParser.PostfixUnaryExpressionContext.asTypeNode(
        pkg: String,
        imports: Set<String>,
        name: String = ""
    ): TypeNode? {
        val n = this.atomicExpression()?.simpleIdentifier()?.text?.let { identifier ->
            name.takeIf { it.isNotBlank() }?.let { "$it.$identifier" } ?: identifier
        } ?: return null

        this.postfixUnaryOperation()?.firstOrNull()?.postfixUnaryExpression()?.also { postfixUnaryExpression ->
            return postfixUnaryExpression.asTypeNode(pkg, imports, n)
        }

        this.callableReference()?.userType()?.simpleUserType()?.firstOrNull()
            ?.simpleIdentifier()?.text?.also { identifier ->
                val qualifiedName =
                    findQualifiedName(pkg, imports, n)
                return ClassTypeNode(n, qualifiedName)
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
        "$pkg.$name".takeIf {
            it in allQualifiedNames || classIndex.findClass(it) != null
        }?.also { return it }
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
