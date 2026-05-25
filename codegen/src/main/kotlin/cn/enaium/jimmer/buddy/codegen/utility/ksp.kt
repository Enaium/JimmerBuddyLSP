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

package cn.enaium.jimmer.buddy.codegen.utility

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.JimmerProcessor
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * @author Enaium
 */
fun createKSName(name: String): KSName {
    return object : KSName {
        override fun asString(): String {
            return name
        }

        override fun getQualifier(): String {
            return name
        }

        override fun getShortName(): String {
            return name.substringAfterLast(".")
        }

        override fun toString(): String {
            return name
        }
    }
}

fun createKSFile(
    fileName: () -> String = { TODO("No yet implemented") },
    filePath: () -> String = { TODO("${fileName()} No yet implemented") },
    packageName: () -> KSName = { TODO("${fileName()} No yet implemented") },
    declarations: () -> Sequence<KSDeclaration> = { TODO("${fileName()} No yet implemented") },
    location: () -> Location = { TODO("${fileName()} No yet implemented") },
    origin: () -> Origin = { TODO("${fileName()} No yet implemented") },
    parent: () -> KSNode? = { null },
    annotations: () -> Sequence<KSAnnotation> = { TODO("${fileName()} No yet implemented") },
): KSFile {
    return object : KSFile {
        override val fileName: String
            get() = fileName()
        override val filePath: String
            get() = filePath()
        override val packageName: KSName
            get() = packageName()
        override val declarations: Sequence<KSDeclaration>
            get() = declarations()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitFile(this, data)
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
    }
}

fun createKSType(
    qualifiedName: String? = null,
    annotations: () -> Sequence<KSAnnotation> = { emptySequence() },
    arguments: () -> List<KSTypeArgument> = { emptyList() },
    declaration: KSType.() -> KSDeclaration = { TODO("Not yet implemented") },
    isError: () -> Boolean = { false },
    isFunctionType: () -> Boolean = { false },
    isMarkedNullable: () -> Boolean = { false },
    isSuspendFunctionType: () -> Boolean = { false },
    nullability: () -> Nullability = { Nullability.NOT_NULL },
    isAssignableFrom: (KSType) -> Boolean = { false },
    isCovarianceFlexible: () -> Boolean = { false },
    isMutabilityFlexible: () -> Boolean = { false },
    makeNotNullable: () -> KSType = { TODO("Not yet implemented") },
    makeNullable: () -> KSType = { TODO("Not yet implemented") },
    replace: (List<KSTypeArgument>) -> KSType = { TODO("Not yet implemented") },
    starProjection: () -> KSType = { TODO("Not yet implemented") },
): KSType {
    return object : KSType {
        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val arguments: List<KSTypeArgument>
            get() = arguments()
        override val declaration: KSDeclaration
            get() = declaration.invoke(this)
        override val isError: Boolean
            get() = isError()
        override val isFunctionType: Boolean
            get() = isFunctionType()
        override val isMarkedNullable: Boolean
            get() = isMarkedNullable()
        override val isSuspendFunctionType: Boolean
            get() = isSuspendFunctionType()
        override val nullability: Nullability
            get() = nullability()

        override fun isAssignableFrom(that: KSType): Boolean {
            return isAssignableFrom(that)
        }

        override fun isCovarianceFlexible(): Boolean {
            return isCovarianceFlexible()
        }

        override fun isMutabilityFlexible(): Boolean {
            return isMutabilityFlexible()
        }

        override fun makeNotNullable(): KSType {
            return makeNotNullable()
        }

        override fun makeNullable(): KSType {
            return makeNullable()
        }

        override fun replace(arguments: List<KSTypeArgument>): KSType {
            return replace(arguments)
        }

        override fun starProjection(): KSType {
            return starProjection()
        }

        override fun toString(): String {
            return qualifiedName ?: super.toString()
        }
    }
}

fun createKSClassDeclaration(
    qualifiedName: () -> KSName? = { TODO("Not yet implemented") },
    classKind: () -> ClassKind = { ClassKind.CLASS },
    isCompanionObject: () -> Boolean = { TODO("${qualifiedName()} Not yet implemented") },
    primaryConstructor: () -> KSFunctionDeclaration? = { TODO("${qualifiedName()} Not yet implemented") },
    superTypes: () -> Sequence<KSTypeReference> = { TODO("${qualifiedName()} Not yet implemented") },
    asStarProjectedType: () -> KSType = { TODO("${qualifiedName()} Not yet implemented") },
    asType: KSClassDeclaration.(List<KSTypeArgument>) -> KSType = { TODO("${qualifiedName()} Not yet implemented") },
    getAllFunctions: () -> Sequence<KSFunctionDeclaration> = { TODO("${qualifiedName()} Not yet implemented") },
    getAllProperties: () -> Sequence<KSPropertyDeclaration> = { TODO("${qualifiedName()} Not yet implemented") },
    getSealedSubclasses: () -> Sequence<KSClassDeclaration> = { TODO("${qualifiedName()} Not yet implemented") },
    containingFile: () -> KSFile? = { TODO("${qualifiedName()} Not yet implemented") },
    docString: () -> String? = { null },
    packageName: () -> KSName = { TODO("${qualifiedName()} Not yet implemented") },
    parentDeclaration: () -> KSDeclaration? = { null },
    simpleName: () -> KSName = { TODO("${qualifiedName()} Not yet implemented") },
    typeParameters: () -> List<KSTypeParameter> = { emptyList() },
    modifiers: () -> Set<Modifier> = { setOf(Modifier.PUBLIC) },
    location: () -> Location = { TODO("${qualifiedName()} Not yet implemented") },
    origin: () -> Origin = { Origin.KOTLIN },
    parent: () -> KSNode? = { TODO("${qualifiedName()} Not yet implemented") },
    annotations: () -> Sequence<KSAnnotation> = { emptySequence() },
    isActual: () -> Boolean = { false },
    isExpect: () -> Boolean = { false },
    findActuals: () -> Sequence<KSDeclaration> = { emptySequence() },
    findExpects: () -> Sequence<KSDeclaration> = { emptySequence() },
    declarations: () -> Sequence<KSDeclaration> = { emptySequence() },
): KSClassDeclaration {
    return object : KSClassDeclaration {
        override val classKind: ClassKind
            get() = classKind()
        override val isCompanionObject: Boolean
            get() = isCompanionObject()
        override val primaryConstructor: KSFunctionDeclaration?
            get() = primaryConstructor()
        override val superTypes: Sequence<KSTypeReference>
            get() = superTypes()

        override fun asStarProjectedType(): KSType {
            return asStarProjectedType()
        }

        override fun asType(typeArguments: List<KSTypeArgument>): KSType {
            return asType.invoke(this, typeArguments)
        }

        override fun getAllFunctions(): Sequence<KSFunctionDeclaration> {
            return getAllFunctions()
        }

        override fun getAllProperties(): Sequence<KSPropertyDeclaration> {
            return getAllProperties()
        }

        override fun getSealedSubclasses(): Sequence<KSClassDeclaration> {
            return getSealedSubclasses()
        }

        override val containingFile: KSFile?
            get() = containingFile()
        override val docString: String?
            get() = docString()
        override val packageName: KSName
            get() = packageName()
        override val parentDeclaration: KSDeclaration?
            get() = parentDeclaration()
        override val qualifiedName: KSName?
            get() = qualifiedName()
        override val simpleName: KSName
            get() = simpleName()
        override val typeParameters: List<KSTypeParameter>
            get() = typeParameters()
        override val modifiers: Set<Modifier>
            get() = modifiers()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitClassDeclaration(this, data)
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val isActual: Boolean
            get() = isActual()
        override val isExpect: Boolean
            get() = isExpect()

        override fun findActuals(): Sequence<KSDeclaration> {
            return findActuals()
        }

        override fun findExpects(): Sequence<KSDeclaration> {
            return findExpects()
        }

        override val declarations: Sequence<KSDeclaration>
            get() = declarations()

        override fun toString(): String {
            return qualifiedName()?.asString() ?: simpleName().asString()
        }
    }
}

fun createKSPropertyGetter(
    returnType: () -> KSTypeReference? = { null },
    receiver: () -> KSPropertyDeclaration = { TODO("Not yet implemented") },
    declarations: () -> Sequence<KSDeclaration> = { emptySequence() },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { Origin.KOTLIN },
    parent: () -> KSDeclaration? = { null },
    annotations: () -> Sequence<KSAnnotation> = { emptySequence() },
    modifiers: () -> Set<Modifier> = { emptySet() },
): KSPropertyGetter {
    return object : KSPropertyGetter {
        override val returnType: KSTypeReference?
            get() = returnType()
        override val receiver: KSPropertyDeclaration
            get() = receiver()
        override val declarations: Sequence<KSDeclaration>
            get() = declarations()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitPropertyGetter(this, data)
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val modifiers: Set<Modifier>
            get() = modifiers()
    }
}

fun createKSPropertyDeclaration(
    qualifiedName: () -> KSName? = { TODO("Not yet implemented") },
    extensionReceiver: () -> KSTypeReference? = { TODO("${qualifiedName()} Not yet implemented") },
    getter: () -> KSPropertyGetter? = { null },
    hasBackingField: () -> Boolean = { false },
    isMutable: () -> Boolean = { false },
    setter: () -> KSPropertySetter? = { null },
    type: () -> KSTypeReference = { TODO("${qualifiedName()} Not yet implemented") },
    asMemberOf: (KSType) -> KSType = { TODO("${qualifiedName()} Not yet implemented") },
    findOverride: () -> KSPropertyDeclaration? = { TODO("${qualifiedName()} Not yet implemented") },
    isDelegated: () -> Boolean = { TODO("${qualifiedName()} Not yet implemented") },
    containingFile: () -> KSFile? = { TODO("${qualifiedName()} Not yet implemented") },
    docString: () -> String? = { null },
    packageName: () -> KSName = { TODO("${qualifiedName()} Not yet implemented") },
    parentDeclaration: () -> KSDeclaration? = { null },
    simpleName: () -> KSName = { TODO("${qualifiedName()} Not yet implemented") },
    typeParameters: () -> List<KSTypeParameter> = { TODO("${qualifiedName()} Not yet implemented") },
    modifiers: () -> Set<Modifier> = { TODO("${qualifiedName()} Not yet implemented") },
    location: () -> Location = { TODO("${qualifiedName()} Not yet implemented") },
    origin: () -> Origin = { TODO("${qualifiedName()} Not yet implemented") },
    parent: () -> KSNode? = { null },
    annotations: () -> Sequence<KSAnnotation> = { TODO("${qualifiedName()} Not yet implemented") },
    isActual: () -> Boolean = { false },
    isExpect: () -> Boolean = { false },
    findActuals: () -> Sequence<KSDeclaration> = { emptySequence() },
    findExpects: () -> Sequence<KSDeclaration> = { emptySequence() },
): KSPropertyDeclaration {
    return object : KSPropertyDeclaration {
        override val extensionReceiver: KSTypeReference?
            get() = extensionReceiver()
        override val getter: KSPropertyGetter?
            get() = getter()
        override val hasBackingField: Boolean
            get() = hasBackingField()
        override val isMutable: Boolean
            get() = isMutable()
        override val setter: KSPropertySetter?
            get() = setter()
        override val type: KSTypeReference
            get() = type()

        override fun asMemberOf(containing: KSType): KSType {
            return asMemberOf(containing)
        }

        override fun findOverridee(): KSPropertyDeclaration? {
            return findOverride()
        }

        override fun isDelegated(): Boolean {
            return isDelegated()
        }

        override val containingFile: KSFile?
            get() = containingFile()
        override val docString: String?
            get() = docString()
        override val packageName: KSName
            get() = packageName()
        override val parentDeclaration: KSDeclaration?
            get() = parentDeclaration()
        override val qualifiedName: KSName?
            get() = qualifiedName()
        override val simpleName: KSName
            get() = simpleName()
        override val typeParameters: List<KSTypeParameter>
            get() = typeParameters()
        override val modifiers: Set<Modifier>
            get() = modifiers()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitPropertyDeclaration(this, data)
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val isActual: Boolean
            get() = isActual()
        override val isExpect: Boolean
            get() = isExpect()

        override fun findActuals(): Sequence<KSDeclaration> {
            return findActuals()
        }

        override fun findExpects(): Sequence<KSDeclaration> {
            return findExpects()
        }
    }
}

fun createKSTypeReference(
    element: () -> KSReferenceElement? = { null },
    resolve: () -> KSType = { TODO("Not yet implemented") },
    annotations: () -> Sequence<KSAnnotation> = { emptySequence() },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { TODO("Not yet implemented") },
    parent: () -> KSNode? = { TODO("Not yet implemented") },
    modifiers: () -> Set<Modifier> = { TODO("Not yet implemented") },
): KSTypeReference {
    return object : KSTypeReference {
        override val element: KSReferenceElement?
            get() = element()

        override fun resolve(): KSType {
            return resolve()
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitTypeReference(this, data)
        }

        override val modifiers: Set<Modifier>
            get() = modifiers()
    }
}

fun createKSValueArgument(
    isSpread: () -> Boolean = { false },
    name: () -> KSName? = { null },
    value: () -> Any? = { null },
    annotations: () -> Sequence<KSAnnotation> = { emptySequence() },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { TODO("Not yet implemented") },
    parent: () -> KSNode? = { null },
): KSValueArgument {
    return object : KSValueArgument {
        override val isSpread: Boolean
            get() = isSpread()
        override val name: KSName?
            get() = name()
        override val value: Any?
            get() = value()
        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitValueArgument(this, data)
        }
    }
}

fun createKSAnnotation(
    annotationType: KSTypeReference,
    arguments: () -> List<KSValueArgument> = { emptyList() },
    defaultArguments: () -> List<KSValueArgument> = { emptyList() },
    shortName: () -> KSName = { TODO("Not yet implemented") },
    useSiteTarget: () -> AnnotationUseSiteTarget? = { TODO("Not yet implemented") },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { TODO("Not yet implemented") },
    parent: () -> KSNode? = { TODO("Not yet implemented") },
): KSAnnotation {
    return object : KSAnnotation {
        override val annotationType: KSTypeReference
            get() = annotationType
        override val arguments: List<KSValueArgument>
            get() = arguments()
        override val defaultArguments: List<KSValueArgument>
            get() = defaultArguments()
        override val shortName: KSName
            get() = shortName()
        override val useSiteTarget: AnnotationUseSiteTarget?
            get() = useSiteTarget()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitAnnotation(this, data)
        }
    }
}

fun createKSTypeArgument(
    type: () -> KSTypeReference? = { TODO("Not yet implemented") },
    variance: () -> Variance = { TODO("Not yet implemented") },
    annotations: () -> Sequence<KSAnnotation> = { TODO("Not yet implemented") },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { TODO("Not yet implemented") },
    parent: () -> KSNode? = { TODO("Not yet implemented") },
): KSTypeArgument {
    return object : KSTypeArgument {
        override val type: KSTypeReference?
            get() = type()
        override val variance: Variance
            get() = variance()
        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitTypeArgument(this, data)
        }
    }
}

fun createKSTypeParameter(
    bounds: () -> Sequence<KSTypeReference> = { TODO("Not yet implemented") },
    isReified: () -> Boolean = { TODO("Not yet implemented") },
    name: () -> KSName,
    variance: () -> Variance = { TODO("Not yet implemented") },
    containingFile: () -> KSFile? = { TODO("Not yet implemented") },
    docString: () -> String? = { TODO("Not yet implemented") },
    packageName: () -> KSName = { TODO("Not yet implemented") },
    parentDeclaration: () -> KSDeclaration? = { TODO("Not yet implemented") },
    qualifiedName: () -> KSName? = { TODO("Not yet implemented") },
    simpleName: () -> KSName = { TODO("Not yet implemented") },
    typeParameters: () -> List<KSTypeParameter> = { TODO("Not yet implemented") },
    modifiers: () -> Set<Modifier> = { TODO("Not yet implemented") },
    location: () -> Location = { TODO("Not yet implemented") },
    origin: () -> Origin = { TODO("Not yet implemented") },
    parent: () -> KSNode? = { TODO("Not yet implemented") },
    annotations: () -> Sequence<KSAnnotation> = { TODO("Not yet implemented") },
    isActual: () -> Boolean = { TODO("Not yet implemented") },
    isExpect: () -> Boolean = { TODO("Not yet implemented") },
    findActuals: () -> Sequence<KSDeclaration> = { emptySequence() },
    findExpects: () -> Sequence<KSDeclaration> = { emptySequence() },
): KSTypeParameter {
    return object : KSTypeParameter {
        override val bounds: Sequence<KSTypeReference>
            get() = bounds()
        override val isReified: Boolean
            get() = isReified()
        override val name: KSName
            get() = name()
        override val variance: Variance
            get() = variance()
        override val containingFile: KSFile?
            get() = containingFile()
        override val docString: String?
            get() = docString()
        override val packageName: KSName
            get() = packageName()
        override val parentDeclaration: KSDeclaration?
            get() = parentDeclaration()
        override val qualifiedName: KSName?
            get() = qualifiedName()
        override val simpleName: KSName
            get() = simpleName()
        override val typeParameters: List<KSTypeParameter>
            get() = typeParameters()
        override val modifiers: Set<Modifier>
            get() = modifiers()
        override val location: Location
            get() = location()
        override val origin: Origin
            get() = origin()
        override val parent: KSNode?
            get() = parent()

        override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
            return visitor.visitTypeParameter(this, data)
        }

        override val annotations: Sequence<KSAnnotation>
            get() = annotations()
        override val isActual: Boolean
            get() = isActual()
        override val isExpect: Boolean
            get() = isExpect()

        override fun findActuals(): Sequence<KSDeclaration> {
            return findActuals()
        }

        override fun findExpects(): Sequence<KSDeclaration> {
            return findExpects()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun createKspOption(
    options: Map<String, String>,
    resolver: Resolver,
    environment: SymbolProcessorEnvironment,
    codeGenerator: CodeGenerator
): KspOption {
    val jimmerProcessor =
        JimmerProcessor(SymbolProcessorEnvironment(options, KotlinVersion.CURRENT, codeGenerator, object : KSPLogger {
            override fun error(message: String, symbol: KSNode?) {
            }

            override fun exception(e: Throwable) {
            }

            override fun info(message: String, symbol: KSNode?) {
            }

            override fun logging(message: String, symbol: KSNode?) {
            }

            override fun warn(message: String, symbol: KSNode?) {
            }
        }))

    val jimmerProcessorClass = JimmerProcessor::class
    return KspOption(
        Context(resolver, environment),
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "isModuleRequired" }!!
            .also { it.isAccessible = true }.get(jimmerProcessor) as Boolean,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "dtoDirs" }!!.also { it.isAccessible = true }
            .get(jimmerProcessor) as Collection<String>,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "dtoTestDirs" }!!
            .also { it.isAccessible = true }.get(jimmerProcessor) as Collection<String>,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "defaultNullableInputModifier" }!!
            .also { it.isAccessible = true }.get(jimmerProcessor) as DtoModifier,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "checkedException" }!!
            .also { it.isAccessible = true }.get(jimmerProcessor) as Boolean,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "dtoMutable" }!!.also { it.isAccessible = true }
            .get(jimmerProcessor) as Boolean,
        jimmerProcessorClass.declaredMemberProperties.find { it.name == "excludedUserAnnotationPrefixes" }!!
            .also { it.isAccessible = true }.get(jimmerProcessor) as List<String>
    )
}

data class KspOption(
    val context: Context,
    val isModuleRequired: Boolean,
    val dtoDirs: Collection<String>,
    val dtoTestDirs: Collection<String>,
    val defaultNullableInputModifier: DtoModifier,
    val checkedException: Boolean,
    val mutable: Boolean,
    val excludedUserAnnotationPrefixes: List<String>
)