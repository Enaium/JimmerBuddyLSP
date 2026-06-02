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
import kotlinx.coroutines.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.*

/**
 * @author Enaium
 */
class JavaSourceProcessor(val sourceDirOrJar: Set<Path>, private val classIndex: ClassIndex) {

    // Qualified name to cst
    private val sourceCompilation = ConcurrentHashMap<String, PreParse>()

    suspend fun process(): ClassIndex = coroutineScope {
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
                    if (entry.name.endsWith(".java")) {
                        val javaLexer = JavaLexer(CharStreams.fromStream(jar.getInputStream(entry)))
                        val javaParser = JavaParser(CommonTokenStream(javaLexer))
                        javaLexer.removeErrorListeners()
                        javaParser.removeErrorListeners()
                        val compilationUnit = javaParser.compilationUnit()
                        val pkg = compilationUnit.packageDeclaration()?.qualifiedName()?.text
                        val name = entry.name.substringAfterLast("/").removeSuffix(".java")
                        val qualifiedName = pkg?.let { "$it.$name" } ?: name
                        result[qualifiedName] = PreParse(compilationUnit, path / entry.name)
                    }
                }
            }
            return@withContext result
        }

    private suspend fun parseSourceFile(path: Path): Map<String, PreParse> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, PreParse>()
        val javaLexer = JavaLexer(CharStreams.fromPath(path))
        val javaParser = JavaParser(CommonTokenStream(javaLexer))
        javaParser.errorListeners.clear()
        val compilationUnit = javaParser.compilationUnit()
        val qualifiedName =
            compilationUnit.packageDeclaration()
                ?.qualifiedName()?.text?.let { "$it.${path.nameWithoutExtension}" } ?: return@withContext result
        result[qualifiedName] = PreParse(compilationUnit, path)
        return@withContext result
    }

    private suspend fun parseSourceDir(sourceDir: Path): Map<String, PreParse> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, PreParse>()
            sourceDir.walk().forEach { file ->
                if (file.extension == "java") {
                    result.putAll(parseSourceFile(file))
                }
            }
            return@withContext result
        }

    private suspend fun parseJdkSource(path: Path): Map<String, PreParse> = coroutineScope {
        val jobs = mutableListOf<Job>()
        val result = ConcurrentHashMap<String, PreParse>()
        ZipFile(path.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (listOf(
                        "java.base/java/util",
                        "java.base/java/lang",
                        "java.base/java/time"
                    ).any { entry.name.startsWith(it) } && entry.name.endsWith(".java")
                ) {
                    jobs.add(launch(Dispatchers.IO) {
                        val javaLexer = JavaLexer(CharStreams.fromStream(zip.getInputStream(entry)))
                        val javaParser = JavaParser(CommonTokenStream(javaLexer))
                        javaParser.errorListeners.clear()
                        val compilationUnit = javaParser.compilationUnit()
                        val qualifiedName = compilationUnit.packageDeclaration()?.qualifiedName()?.text
                            ?.let { "$it.${entry.name.substringAfterLast("/").removeSuffix(".java")}" }
                            ?: return@launch
                        result[qualifiedName] = PreParse(compilationUnit, path / entry.name)
                    })
                }
            }
            jobs.joinAll()
        }
        return@coroutineScope result
    }

    private suspend fun parseSource() = coroutineScope {
        // parse cst
        sourceDirOrJar.mapNotNull { entry ->
            if (!entry.exists()) {
                return@mapNotNull null
            }
            launch(Dispatchers.IO) {
                if (entry.isDirectory()) {
                    sourceCompilation.putAll(parseSourceDir(entry))
                } else if (entry.extension == "jar") {
                    sourceCompilation.putAll(parseSourceJar(entry))
                } else if (entry.extension == "zip") {
                    sourceCompilation.putAll(parseJdkSource(entry))
                } else if (entry.extension == "java") {
                    sourceCompilation.putAll(parseSourceFile(entry))
                }
            }
        }.joinAll()
    }

    private suspend fun parseCst() = coroutineScope {
        // parse node from cst
        sourceCompilation.map { (fqName, preParse) ->
            launch(Dispatchers.Default) {
                val (compilationUnit, path) = preParse
                val pkg = compilationUnit.packageDeclaration()?.qualifiedName()?.text ?: ""
                val imports =
                    compilationUnit.importDeclaration().map { importDeclaration ->
                        importDeclaration.qualifiedName().text.let { qualifiedName ->
                            importDeclaration?.MUL()?.let { "$qualifiedName.$it" } ?: qualifiedName
                        }
                    }.toSet()

                // All type declaration such as class, interface, enum etc.
                compilationUnit.typeDeclaration().forEach { typeDeclaration ->

                    val annotations = typeDeclaration.classOrInterfaceModifier()
                        ?.mapNotNull { it.annotation()?.asAnnotationEntryNode(pkg, imports) }?.toSet() ?: emptySet()

                    // parse class cst
                    typeDeclaration.classDeclaration()?.also { classDeclarationContext ->
                        val name = classDeclarationContext.identifier().text ?: return@forEach
                        val qualifiedName = "${fqName.substringBeforeLast(".")}.${name}"
                        classIndex.upsertClass(
                            qualifiedName, ClassClassNode(qualifiedName, path, annotations = annotations)
                        )
                    }

                    // parse interface cst
                    typeDeclaration.interfaceDeclaration()?.also { interfaceDeclarationContext ->
                        val name = interfaceDeclarationContext.identifier().text ?: return@forEach
                        val qualifiedName = "${fqName.substringBeforeLast(".")}.${name}"
                        val supers = interfaceDeclarationContext.typeList()
                            ?.flatMap { it.typeType().map { it.asTypeNode(pkg, imports) } }
                            ?.toSet() ?: emptySet()

                        val methods = interfaceDeclarationContext.interfaceBody()?.interfaceBodyDeclaration()
                            ?.mapNotNull { bodyDeclarationContext ->
                                return@mapNotNull bodyDeclarationContext.asMethodNode(fqName, pkg, imports)
                            }?.toSet() ?: emptySet()
                        classIndex.upsertClass(
                            qualifiedName,
                            InterfaceNode(qualifiedName, path, annotations, supers = supers, members = methods)
                        )
                    }

                    // parse enum cst
                    typeDeclaration.enumDeclaration()?.also { enumDeclarationContext ->
                        val name = enumDeclarationContext.identifier().text ?: return@forEach
                        val qualifiedName = "${fqName.substringBeforeLast(".")}${name}"
                        classIndex.upsertClass(
                            qualifiedName,
                            EnumClassNode(
                                qualifiedName,
                                path,
                                annotations,
                                entries = enumDeclarationContext.enumConstants()?.enumConstant()
                                    ?.map { EnumEntryNode(it.identifier().text) }?.toSet() ?: emptySet()
                            )
                        )
                    }

                    // parse annotation cst
                    typeDeclaration.annotationTypeDeclaration()?.also { annotationTypeDeclarationContext ->
                        val name = annotationTypeDeclarationContext.identifier().text ?: return@forEach
                        val qualifiedName = "${fqName.substringBeforeLast(".")}.${name}"
                        classIndex.upsertClass(qualifiedName, AnnotationClassNode(qualifiedName, path, annotations))
                    }
                }
            }
        }.joinAll()
    }

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
        // The package name and the name are a qualified name if it has in cst cache.
        "$pkg.$name".takeIf { sourceCompilation.containsKey(it) || classIndex.findClass(it) != null }?.also { return it }
        // The import is qualified name if its suffix is same the name
        imports.find { it.endsWith(name) }?.also { return it }
        // The import package name and the name are a qualified name if it has in cst cache.
        imports.filter { it.endsWith("*") }.forEach { import ->
            val qualifiedName = "${import.substringBeforeLast(".*")}.${name}"
            if (sourceCompilation.containsKey(qualifiedName) || classIndex.findClass(qualifiedName) != null) {
                return qualifiedName
            } else {
                sourceCompilation.forEach { (fqName, preParse) ->
                    val (compilationUnit, path) = preParse
                    compilationUnit.typeDeclaration().forEach { declarationContext ->
                        (declarationContext.classDeclaration()?.identifier()
                            ?: declarationContext.interfaceDeclaration()?.identifier()
                            ?: declarationContext.enumDeclaration()?.identifier()
                            ?: declarationContext.annotationTypeDeclaration()?.identifier())
                            ?.let {
                                "${fqName.substringBeforeLast(".")}.${name}"
                            }?.also {
                                return it
                            }
                    }
                }
            }
        }
        return null
    }

    private data class PreParse(
        val cst: JavaParser.CompilationUnitContext,
        val path: Path
    )
}