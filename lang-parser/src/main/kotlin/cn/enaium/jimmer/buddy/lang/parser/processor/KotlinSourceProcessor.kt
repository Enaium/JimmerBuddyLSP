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
import kotlinx.coroutines.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * @author Enaium
 */
class KotlinSourceProcessor(val sourceDirOrJar: Set<Path>, private val classIndex: ClassIndex) {

    // Qualified name to cst
    private val sourceTopLevelObjects =
        ConcurrentHashMap<String, PreParse>()

    suspend fun process(): ClassIndex = coroutineScope {
        JavaSourceProcessor(sourceDirOrJar, classIndex).process()
        parseSource()
        parseCst()
        return@coroutineScope classIndex
    }

    private suspend fun parseSourceJar(path: Path): Map<String, PreParse> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, PreParse>()
            JarFile(path.toFile()).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".kt")) {
                        val kotlinLexer = KotlinLexer(CharStreams.fromStream(jar.getInputStream(entry)))
                        val kotlinParser = KotlinParser(CommonTokenStream(kotlinLexer))
                        kotlinLexer.removeErrorListeners()
                        kotlinParser.removeErrorListeners()
                        val kotlinFile = kotlinParser.kotlinFile()

                        val pkg = kotlinFile.preamble()?.packageHeader()?.identifier()?.text
                        kotlinFile.topLevelObject().forEach { topLevelObject ->
                            val name = topLevelObject.classDeclaration()?.simpleIdentifier()?.text
                            val qualifiedName = pkg?.let { pkg -> "$pkg.$name" } ?: name ?: return@forEach
                            result[qualifiedName] = PreParse(kotlinFile.preamble(), topLevelObject, path / entry.name)
                        }
                    }
                }
            }
            return@withContext result
        }

    private suspend fun parseSourceFile(path: Path): Map<String, PreParse> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, PreParse>()
        val kotlinLexer = KotlinLexer(CharStreams.fromPath(path))
        val kotlinParser = KotlinParser(CommonTokenStream(kotlinLexer))
        kotlinLexer.removeErrorListeners()
        kotlinParser.removeErrorListeners()
        val kotlinFile = kotlinParser.kotlinFile()

        val pkg = kotlinFile.preamble()?.packageHeader()?.identifier()?.text
        kotlinFile.topLevelObject().forEach { topLevelObject ->
            val name = topLevelObject.classDeclaration()?.simpleIdentifier()?.text
            val qualifiedName = pkg?.let { pkg -> "$pkg.$name" } ?: name ?: return@forEach
            result[qualifiedName] = PreParse(kotlinFile.preamble(), topLevelObject, path)
        }
        return@withContext result
    }

    private suspend fun parseSourceDir(sourceDir: Path): Map<String, PreParse> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, PreParse>()
            sourceDir.walk().forEach { file ->
                if (file.extension == "kt") {
                    result.putAll(parseSourceFile(file))
                }
            }
            return@withContext result
        }

    private suspend fun parseSource() = coroutineScope {
        // parse cst
        sourceDirOrJar.mapNotNull { entry ->
            if (!entry.exists()) {
                return@mapNotNull null
            }
            launch(Dispatchers.IO) {
                if (entry.isDirectory()) {
                    sourceTopLevelObjects.putAll(parseSourceDir(entry))
                } else if (entry.extension == "jar") {
                    sourceTopLevelObjects.putAll(parseSourceJar(entry))
                } else if (entry.extension == "kt") {
                    sourceTopLevelObjects.putAll(parseSourceFile(entry))
                }
            }
        }.joinAll()
    }

    private suspend fun parseCst() = coroutineScope {
        // parse node from cst
        sourceTopLevelObjects.map { (fqName, preParse) ->
            val (preamble, topLevelObject, path) = preParse
            launch(Dispatchers.Default) {
                val pkg = preamble.packageHeader()?.identifier()?.text ?: return@launch
                val imports = preamble.importList()?.importHeader()?.map { importHeader ->
                    importHeader.identifier().text.let { identifier ->
                        importHeader.MULT()?.let { "$identifier.$it" } ?: identifier
                    }
                }?.toSet() ?: emptySet()

                topLevelObject.classDeclaration()?.also { classDeclarationContext ->
                    val annotations =
                        classDeclarationContext.modifierList()?.annotations()
                            ?.map { it.annotation().asAnnotationEntryNode(pkg, imports) }?.toSet()
                            ?: emptySet()

                    // parse class cst
                    classDeclarationContext.CLASS()?.also {
                        // parse enum cst
                        classDeclarationContext.modifierList()?.modifier()?.any { it.classModifier()?.ENUM() != null }
                            ?.also {
                                val entries = classDeclarationContext.enumClassBody()?.enumEntries()?.enumEntry()
                                    ?.mapNotNull { EnumEntryNode(it.simpleIdentifier().text) }?.toSet() ?: emptySet()
                                classIndex.upsertClass(fqName, EnumClassNode(fqName, path, annotations, entries))
                            } ?: run {
                            classIndex.upsertClass(fqName, ClassClassNode(fqName, path))
                        }
                        classDeclarationContext.modifierList()?.modifier()
                            ?.any { it.classModifier()?.ANNOTATION() != null }?.also {
                                classIndex.upsertClass(fqName, AnnotationClassNode(fqName, path, annotations))
                            }
                        classDeclarationContext.modifierList()?.modifier()?.any { it.classModifier()?.DATA() != null }
                            ?.also {
                                val parameters =
                                    classDeclarationContext.primaryConstructor()?.classParameters()?.classParameter()
                                        ?.mapNotNull {
                                            it.simpleIdentifier().text to (it.type().asTypeNode(pkg, imports)
                                                ?: return@mapNotNull null)
                                        }?.toMap() ?: emptyMap()
                                classIndex.upsertClass(fqName, DataClassNode(fqName, path, annotations, parameters))
                            }
                    }

                    // parse interface cst
                    classDeclarationContext.INTERFACE()?.also {

                        val supers = classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()?.mapNotNull {
                            it.asTypeNode(pkg, imports)
                        }?.toSet() ?: emptySet()

                        val properties = classDeclarationContext.classBody()?.classMemberDeclaration()
                            ?.mapNotNull { it.propertyDeclaration()?.asPropertyNode(fqName, pkg, imports) }?.toSet()
                            ?: classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()?.lastOrNull()
                                ?.constructorInvocation()?.callSuffix()?.annotatedLambda()?.firstOrNull()
                                ?.functionLiteral()?.statements()?.statement()
                                ?.mapNotNull {
                                    it.declaration()?.propertyDeclaration()?.asPropertyNode(fqName, pkg, imports)
                                }
                                ?.toSet() ?: emptySet()


                        classIndex.upsertClass(
                            fqName,
                            InterfaceNode(fqName, path, annotations, supers = supers, members = properties)
                        )
                    }
                }
            }
        }.joinAll()
    }

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
        val qualifiedName = findQualifiedName(
            pkg,
            imports,
            name
        )
        return ClassTypeNode(name, qualifiedName)
    }

    private fun KotlinParser.PostfixUnaryExpressionContext.asTypeNode(
        pkg: String,
        imports: Set<String>,
        name: String = ""
    ): TypeNode? {
        val name = this.atomicExpression()?.simpleIdentifier()?.text?.let { identifier ->
            name.takeIf { it.isNotBlank() }?.let { "$it.$identifier" } ?: identifier
        } ?: return null

        this.postfixUnaryOperation()?.firstOrNull()?.postfixUnaryExpression()?.also { postfixUnaryExpression ->
            return postfixUnaryExpression.asTypeNode(pkg, imports, name)
        }

        this.callableReference()?.userType()?.simpleUserType()?.firstOrNull()
            ?.simpleIdentifier()?.text?.also { identifier ->
                val qualifiedName =
                    findQualifiedName(
                        pkg,
                        imports,
                        name.takeIf { it.isNotBlank() }?.let { "$it.$identifier" } ?: identifier)
                return ClassTypeNode(name, qualifiedName)
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
        // The package name and the name are a qualified name if it has in cst cache.
        "$pkg.$name".takeIf {
            sourceTopLevelObjects.containsKey(it) || classIndex.findClass(it) != null
        }?.also { return it }
        // The import is qualified name if its suffix is same the name
        imports.find { it.endsWith(name) }?.also { return it }
        // The import package name and the name are a qualified name if it has in cst cache.
        imports.filter { it.endsWith("*") }.forEach { import ->
            val qualifiedName = "${import.substringBeforeLast(".*")}.${name}"
            if (sourceTopLevelObjects.containsKey(qualifiedName) || classIndex.findClass(qualifiedName) != null) {
                return qualifiedName
            }
        }
        return null
    }

    private data class PreParse(
        val preamble: KotlinParser.PreambleContext,
        val topLevelObject: KotlinParser.TopLevelObjectContext,
        val path: Path
    )
}