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

package cn.enaium.jimmer.buddy.codegen.symbol

import cn.enaium.jimmer.buddy.codegen.utility.*
import cn.enaium.jimmer.buddy.lang.parser.node.*
import cn.enaium.jimmer.buddy.project.structure.Environment
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.implementation.FixedValue
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.Scalar
import org.babyfish.jimmer.apt.createContext
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType
import org.babyfish.jimmer.error.ErrorFamily
import org.babyfish.jimmer.error.ErrorField
import org.babyfish.jimmer.jackson.JsonConverter
import org.babyfish.jimmer.sql.*
import org.jetbrains.annotations.Nullable
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.Writer
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.lang.model.element.*
import javax.lang.model.type.*
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.*
import javax.tools.JavaFileObject.Kind
import kotlin.io.path.Path
import kotlin.io.path.div

/**
 * @author Enaium
 */
class AptProcessor(
    val environment: Environment
) {
    val caches = mutableMapOf<String, TypeElement>()
    private val classCache = mutableMapOf<String, ClassNode?>()

    private fun findClass(qualifiedName: String?): ClassNode? {
        if (qualifiedName == null) return null
        return classCache.getOrPut(qualifiedName) {
            environment.findClass(qualifiedName)
        }
    }

    fun process(genClasses: Set<ClassNode>): Apt {
        val typeElementCaches = mutableMapOf<String, TypeElement>()
        genClasses.forEach { klass ->
            val qualifiedName = klass.qualifiedName
            typeElementCaches[qualifiedName] = klass.asTypeElement()
        }

        val sources = mutableListOf<Source>()

        return Apt(
            createProcessingEnvironment(
                getOptions = { emptyMap() },
                getMessager = {
                    object : Messager {
                        override fun printMessage(kind: Diagnostic.Kind, msg: CharSequence) {

                        }

                        override fun printMessage(
                            kind: Diagnostic.Kind,
                            msg: CharSequence,
                            e: Element
                        ) {

                        }

                        override fun printMessage(
                            kind: Diagnostic.Kind,
                            msg: CharSequence,
                            e: Element,
                            a: AnnotationMirror
                        ) {

                        }

                        override fun printMessage(
                            kind: Diagnostic.Kind,
                            msg: CharSequence,
                            e: Element,
                            a: AnnotationMirror,
                            v: AnnotationValue
                        ) {

                        }
                    }
                },
                getFiler = {
                    object : Filer {
                        override fun createSourceFile(
                            name: CharSequence,
                            vararg originatingElements: Element
                        ): JavaFileObject {
                            return object : SimpleJavaFileObject(
                                Path(System.getProperty("user.dir"), "dummy.java").toUri(),
                                Kind.OTHER
                            ) {
                                override fun openOutputStream(): OutputStream {
                                    return object : ByteArrayOutputStream() {
                                        override fun close() {
                                            sources.add(
                                                Source(
                                                    packageName = name.toString().substringBeforeLast("."),
                                                    fileName = name.toString().substringAfterLast("."),
                                                    extensionName = "java",
                                                    content = toString()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        override fun createClassFile(
                            name: CharSequence?,
                            vararg originatingElements: Element?
                        ): JavaFileObject? {
                            TODO("Not yet implemented")
                        }

                        override fun createResource(
                            location: JavaFileManager.Location?,
                            moduleAndPkg: CharSequence?,
                            relativeName: CharSequence?,
                            vararg originatingElements: Element?
                        ): FileObject? {
                            TODO("Not yet implemented")
                        }

                        override fun getResource(
                            location: JavaFileManager.Location,
                            moduleAndPkg: CharSequence,
                            relativeName: CharSequence
                        ): FileObject {
                            return object : SimpleJavaFileObject(
                                (Path(System.getProperty("user.dir")) / relativeName.toString()).toUri(),
                                Kind.OTHER
                            ) {

                            }
                        }
                    }
                },
                getElementUtils = {
                    object : Elements {
                        override fun getPackageElement(name: CharSequence): PackageElement {
                            TODO("Not yet implemented")
                        }

                        override fun getTypeElement(name: CharSequence): TypeElement {
                            return typeElementCaches[name.toString()] ?: findClass(name.toString())?.asTypeElement()
                            ?: createTypeElement(name.toString())
                        }

                        override fun getElementValuesWithDefaults(a: AnnotationMirror): Map<out ExecutableElement, AnnotationValue> {
                            TODO("Not yet implemented")
                        }

                        override fun getDocComment(e: Element): String? {
                            return null
                        }

                        override fun isDeprecated(e: Element): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun getBinaryName(type: TypeElement): Name {
                            TODO("Not yet implemented")
                        }

                        override fun getPackageOf(e: Element): PackageElement {
                            TODO("Not yet implemented")
                        }

                        override fun getAllMembers(type: TypeElement): List<Element> {
                            TODO("Not yet implemented")
                        }

                        override fun getAllAnnotationMirrors(e: Element): List<AnnotationMirror> {
                            TODO("Not yet implemented")
                        }

                        override fun hides(
                            hider: Element,
                            hidden: Element
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun overrides(
                            overrider: ExecutableElement,
                            overridden: ExecutableElement,
                            type: TypeElement
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun getConstantExpression(value: Any): String {
                            TODO("Not yet implemented")
                        }

                        override fun printElements(w: Writer, vararg elements: Element) {
                            TODO("Not yet implemented")
                        }

                        override fun getName(cs: CharSequence): Name {
                            TODO("Not yet implemented")
                        }

                        override fun isFunctionalInterface(type: TypeElement): Boolean {
                            TODO("Not yet implemented")
                        }
                    }
                },
                getTypeUtils = {
                    object : Types {
                        override fun asElement(t: TypeMirror): Element? {
                            return typeElementCaches[t.toString()] ?: findClass(t.toString())?.asTypeElement()
                        }

                        override fun isSameType(
                            t1: TypeMirror?,
                            t2: TypeMirror?
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun isSubtype(
                            t1: TypeMirror,
                            t2: TypeMirror
                        ): Boolean {
                            return if (t1 is DeclaredType && t2 is DeclaredType) {
                                val t1Element = t1.asElement()?.toString() ?: t1.toString()
                                val t2Element = t2.asElement()?.toString() ?: t2.toString()

                                var eq =
                                    t1Element.contentEquals(t2Element)

                                if (!eq) {
                                    if (t2Element == "java.lang.Enum") {
                                        eq = typeElementCaches[t1Element]?.kind == ElementKind.ENUM
                                    } else {
                                        // TODO
//                                        val classNode = findClass(t2Element) ?: return false
//                                        eq = t1Element in ClassInheritorsSearch.search(
//                                            classNode,
//                                            project.allScope(),
//                                            true
//                                        )
//                                            .map { it.qualifiedName }
                                    }
                                }
                                eq
                            } else {
                                false
                            }
                        }

                        override fun isAssignable(
                            t1: TypeMirror?,
                            t2: TypeMirror?
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun contains(
                            t1: TypeMirror?,
                            t2: TypeMirror?
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun isSubsignature(
                            m1: ExecutableType?,
                            m2: ExecutableType?
                        ): Boolean {
                            TODO("Not yet implemented")
                        }

                        override fun directSupertypes(t: TypeMirror?): List<TypeMirror?>? {
                            TODO("Not yet implemented")
                        }

                        override fun erasure(t: TypeMirror?): TypeMirror? {
                            TODO("Not yet implemented")
                        }

                        override fun boxedClass(p: PrimitiveType?): TypeElement? {
                            TODO("Not yet implemented")
                        }

                        override fun unboxedType(t: TypeMirror?): PrimitiveType? {
                            TODO("Not yet implemented")
                        }

                        override fun capture(t: TypeMirror?): TypeMirror? {
                            TODO("Not yet implemented")
                        }

                        override fun getPrimitiveType(kind: TypeKind?): PrimitiveType? {
                            TODO("Not yet implemented")
                        }

                        override fun getNullType(): NullType? {
                            TODO("Not yet implemented")
                        }

                        override fun getNoType(kind: TypeKind?): NoType? {
                            TODO("Not yet implemented")
                        }

                        override fun getArrayType(componentType: TypeMirror?): ArrayType? {
                            TODO("Not yet implemented")
                        }

                        override fun getWildcardType(
                            extendsBound: TypeMirror?,
                            superBound: TypeMirror?
                        ): WildcardType {
                            return object : WildcardType {
                                override fun getExtendsBound(): TypeMirror? {
                                    TODO("Not yet implemented")
                                }

                                override fun getSuperBound(): TypeMirror? {
                                    TODO("Not yet implemented")
                                }

                                override fun getKind(): TypeKind? {
                                    TODO("Not yet implemented")
                                }

                                override fun getAnnotationMirrors(): List<AnnotationMirror?>? {
                                    TODO("Not yet implemented")
                                }

                                override fun <A : Annotation?> getAnnotation(annotationType: Class<A?>?): A? {
                                    TODO("Not yet implemented")
                                }

                                override fun <A : Annotation?> getAnnotationsByType(annotationType: Class<A?>?): Array<out A?>? {
                                    TODO("Not yet implemented")
                                }

                                override fun <R : Any?, P : Any?> accept(
                                    v: TypeVisitor<R?, P?>?,
                                    p: P?
                                ): R? {
                                    TODO("Not yet implemented")
                                }

                            }
                        }

                        override fun getDeclaredType(
                            typeElem: TypeElement?,
                            vararg typeArgs: TypeMirror
                        ): DeclaredType {
                            return object : DeclaredType {
                                override fun asElement(): Element? {
                                    return typeElementCaches[typeElem?.qualifiedName.toString()]
                                }

                                override fun getEnclosingType(): TypeMirror? {
                                    TODO("Not yet implemented")
                                }

                                override fun getTypeArguments(): List<TypeMirror?>? {
                                    TODO("Not yet implemented")
                                }

                                override fun getKind(): TypeKind? {
                                    TODO("Not yet implemented")
                                }

                                override fun getAnnotationMirrors(): List<AnnotationMirror?>? {
                                    TODO("Not yet implemented")
                                }

                                override fun <A : Annotation?> getAnnotation(annotationType: Class<A?>?): A? {
                                    TODO("Not yet implemented")
                                }

                                override fun <A : Annotation?> getAnnotationsByType(annotationType: Class<A?>?): Array<out A?>? {
                                    TODO("Not yet implemented")
                                }

                                override fun <R : Any?, P : Any?> accept(
                                    v: TypeVisitor<R?, P?>?,
                                    p: P?
                                ): R? {
                                    TODO("Not yet implemented")
                                }

                                override fun toString(): String {
                                    return typeElem?.qualifiedName.toString()
                                }
                            }
                        }

                        override fun getDeclaredType(
                            containing: DeclaredType?,
                            typeElem: TypeElement?,
                            vararg typeArgs: TypeMirror?
                        ): DeclaredType? {
                            TODO("Not yet implemented")
                        }

                        override fun asMemberOf(
                            containing: DeclaredType?,
                            element: Element?
                        ): TypeMirror? {
                            TODO("Not yet implemented")
                        }

                    }
                }
            ),
            typeElementCaches.values.toSet(),
            sources
        )
    }

    fun toImmutable(classNode: ClassNode): ImmutableType {
        val (pe, rootElements, sources) = AptProcessor(environment).process(emptySet())
        val context = createContext(pe.elementUtils, pe.typeUtils, pe.filer)
        return context.getImmutableType(pe.elementUtils.getTypeElement(classNode.qualifiedName))
    }

    fun ClassNode.asTypeElement(): TypeElement {
        return caches[qualifiedName] ?: createTypeElement(
            getEnclosedElements = {
                when (this) {
                    is EnumClassNode -> {
                        this.entries.map {
                            val enumConstant = createTypeElement(
                                getQualifiedName = { createName(it.name) },
                                getSimpleName = { createName(it.name) },
                                getKind = { ElementKind.ENUM_CONSTANT },
                                getEnclosingElement = { createTypeElement() },
                            )
                            createTypeElement(
                                getQualifiedName = { createName(it.name) },
                                getSimpleName = { createName(it.name) },
                                getKind = { ElementKind.ENUM_CONSTANT },
                                getEnclosingElement = { enumConstant },
                            )
                        }
                    }

                    is InterfaceNode -> {
                        this.members.filterIsInstance<MethodNode>()
                            .map { it.asExecutableElement(this) }
                    }

                    else -> {
                        emptyList()
                    }
                }
            },
            getQualifiedName = { createName(this.qualifiedName) },
            getSimpleName = { createName(this.qualifiedName.substringAfterLast(".")) },
            getKind = {
                when (this) {
                    is InterfaceNode -> {
                        ElementKind.INTERFACE
                    }

                    is EnumClassNode -> {
                        ElementKind.ENUM
                    }

                    is AnnotationClassNode -> {
                        ElementKind.ANNOTATION_TYPE
                    }

                    else -> {
                        ElementKind.CLASS
                    }
                }
            },
            getModifiers = { setOf(Modifier.PUBLIC) },
            getAnnotation = { anno ->
                this.annotations.find { it.qualifiedName == anno.name }?.findAnnotation()
            },
            getEnclosingElement = {
                createPackageElement(
                    getQualifiedName = { createName(this.qualifiedName.substringBeforeLast(".")) }
                )
            },
            asType = {
                createDeclaredType(
                    getQualifiedName = { this.qualifiedName },
                    asElement = {
                        this.asTypeElement()
                    },
                    getTypeArguments = {
                        emptyList()
                    }
                )
            },
            getInterfaces = {
                if (this is InterfaceNode) {
                    this.supers.filterIsInstance<ClassTypeNode>().mapNotNull {
                        if (findClass(it.qualifiedName) is InterfaceNode) {
                            it.asTypeMirror()
                        } else {
                            null
                        }
                    }
                } else {
                    emptyList()
                }
            },
            getSuperclass = {
                if (this is ClassClassNode) {
                    this.supers.filterIsInstance<ClassTypeNode>()
                        .find { findClass(it.qualifiedName) !is InterfaceNode }
                        ?.asTypeMirror()
                } else {
                    null
                }
            },
            getTypeParameters = {
                when (this) {
                    is ClassClassNode -> {
                        this.parameters
                    }

                    is InterfaceNode -> {
                        this.parameters
                    }

                    else -> {
                        emptyList()
                    }
                }.map {
                    createTypeParameter(
                        getSimpleName = { createName(it.name) },
                    )
                }
            }
        ).also {
            caches[this.qualifiedName] = it
        }
    }

    fun createTypeElement(qualifiedName: String): TypeElement {
        return createTypeElement(
            getQualifiedName = { createName(qualifiedName) },
            getSimpleName = { createName(qualifiedName.substringAfterLast(".")) },
            getEnclosingElement = {
                createPackageElement(
                    getQualifiedName = {
                        createName(
                            qualifiedName.substringBeforeLast(
                                "."
                            )
                        )
                    }
                )
            },
            getEnclosedElements = {
                emptyList()
            }
        )
    }

    fun MethodNode.asExecutableElement(classNode: ClassNode): ExecutableElement {
        return createExecutableElement(
            getKind = { ElementKind.METHOD },
            getSimpleName = { createName(this.name) },
            getModifiers = { setOf(Modifier.PUBLIC) },
            getEnclosingElement = { classNode.asTypeElement() },
            getParameters = { emptyList() },
            getReturnType = {
                this.type.asTypeMirror()
            },
            getAnnotation = { annotation ->
                this.annotations.find { it.qualifiedName == annotation.name }?.findAnnotation()
            },
            getAnnotationMirrors = {
                this.annotations.mapNotNull { it.findAnnotation()?.asAnnotationMirror() }
            },
            getAnnotationsByType = { annotation ->
                this.annotations.find { it.qualifiedName == annotation.name }?.findAnnotation()?.let { arrayOf(it) }
            },
            isDefault = { this.default }
        )
    }

    fun TypeNode.asTypeMirror(): TypeMirror {
        when (this) {
            is PrimitiveTypeNode -> {
                when (this.name) {
                    "long" -> createPrimitiveType(getKind = { TypeKind.LONG })
                    "int" -> createPrimitiveType(getKind = { TypeKind.INT })
                    "short" -> createPrimitiveType(getKind = { TypeKind.SHORT })
                    "byte" -> createPrimitiveType(getKind = { TypeKind.BYTE })
                    "char" -> createPrimitiveType(getKind = { TypeKind.CHAR })
                    "double" -> createPrimitiveType(getKind = { TypeKind.DOUBLE })
                    "float" -> createPrimitiveType(getKind = { TypeKind.FLOAT })
                    "boolean" -> createPrimitiveType(getKind = { TypeKind.BOOLEAN })
                    "void" -> createPrimitiveType(getKind = { TypeKind.VOID })
                    else -> null
                }?.also {
                    return if (this.array) {
                        createArrayType(
                            getComponentType = {
                                it
                            })
                    } else {
                        it
                    }
                }
            }

            is ClassTypeNode -> {
                return createDeclaredType(
                    getQualifiedName = {
                        this.qualifiedName ?: throw NullPointerException("cannot find class ${this.name}")
                    },
                    asElement = {
                        findClass(this.qualifiedName)?.asTypeElement()
                            ?: this.qualifiedName?.let { createTypeElement(it) }
                            ?: throw IllegalStateException("cannot find class ${this.name}")
                    },
                    getTypeArguments = {
                        this.arguments.mapNotNull { argument ->
                            if (argument is ClassTypeNode) {
                                createDeclaredType(
                                    getQualifiedName = {
                                        argument.qualifiedName
                                            ?: throw NullPointerException("cannot find class ${this.name}")
                                    },
                                    asElement = {
                                        caches[argument.qualifiedName]
                                            ?: findClass(argument.qualifiedName)?.asTypeElement()
                                            ?: this.qualifiedName?.let { createTypeElement(it) }
                                            ?: throw IllegalStateException("Generic element is null")
                                    },
                                    getTypeArguments = { emptyList() },
                                )
                            } else {
                                null
                            }
                        }
                    },
                    getAnnotationMirrors = { emptyList() },
                )
            }
        }

        return createDeclaredType(
            getQualifiedName = { this.name }
        )
    }

    fun AnnotationArgumentNode.toAny(returnType: Class<*>): Any? {
        return this.value?.let {
            when (it) {
                is Int -> {
                    when (returnType.kotlin) {
                        Long::class -> it.toString().toLong()
                        Int::class -> it.toString().toInt()
                        Short::class -> it.toString().toShort()
                        Byte::class -> it.toString().toByte()
                        Boolean::class -> it.toString().toBoolean()
                    }
                }

                is Float -> {
                    when (returnType.kotlin) {
                        Float::class -> it
                        Double::class -> it.toDouble()
                    }
                }

                is Set<*> -> {
                    when (returnType.name) {
                        "[Ljava.lang.String;" -> it.map { it.toString() }.toTypedArray()
                    }
                }

                else -> {
                    it
                }
            }
        }
    }

    fun Any.arrayWrapper(returnType: Class<*>): Any {
        return if (returnType.name.startsWith("[L")) {
            when (this) {
                is Long -> {
                    arrayOf(this)
                }

                is Int -> {
                    arrayOf(this)
                }

                is Short -> {
                    arrayOf(this)
                }

                is Byte -> {
                    arrayOf(this)
                }

                is Boolean -> {
                    arrayOf(this)
                }

                is String -> {
                    arrayOf(this)
                }

                else -> {
                    this
                }
            }
        } else {
            this
        }
    }


    fun AnnotationEntryNode.findAnnotation(): Annotation? = when (this.qualifiedName) {
        Immutable::class.qualifiedName -> AnnotationInstances.immutable()
        Entity::class.qualifiedName -> AnnotationInstances.entity()
        MappedSuperclass::class.qualifiedName -> AnnotationInstances.mappedSuperclass()
        Embeddable::class.qualifiedName -> AnnotationInstances.embeddable()
        ErrorFamily::class.qualifiedName -> AnnotationInstances.errorFamily()
        ErrorField::class.qualifiedName -> AnnotationInstances.errorField()
        Id::class.qualifiedName -> AnnotationInstances.id()
        IdView::class.qualifiedName -> AnnotationInstances.idView()
        Key::class.qualifiedName -> AnnotationInstances.key()
        Version::class.qualifiedName -> AnnotationInstances.version()
        Formula::class.qualifiedName -> AnnotationInstances.formula()
        OneToOne::class.qualifiedName -> AnnotationInstances.oneToOne()
        OneToMany::class.qualifiedName -> AnnotationInstances.oneToMany()
        ManyToOne::class.qualifiedName -> AnnotationInstances.manyToOne()
        ManyToMany::class.qualifiedName -> AnnotationInstances.manyToMany()
        ManyToManyView::class.qualifiedName -> AnnotationInstances.manyToManyView()
        Column::class.qualifiedName -> AnnotationInstances.column()
        GeneratedValue::class.qualifiedName -> AnnotationInstances.generatedValue()
        JoinColumn::class.qualifiedName -> AnnotationInstances.joinColumn()
        JoinTable::class.qualifiedName -> AnnotationInstances.joinTable()
        Transient::class.qualifiedName -> AnnotationInstances._transient()
        Serialized::class.qualifiedName -> AnnotationInstances.serialized()
        LogicalDeleted::class.qualifiedName -> AnnotationInstances.logicalDeleted()
        Scalar::class.qualifiedName -> AnnotationInstances.scalar()
        Nullable::class.qualifiedName -> AnnotationInstances.nullable()
        org.jspecify.annotations.Nullable::class.qualifiedName -> AnnotationInstances.jspecifyNullable()
        TypedTuple::class.qualifiedName -> AnnotationInstances.typedTuple()
        JsonConverter::class.qualifiedName -> AnnotationInstances.jsonConverter()
        else -> null
    }?.let {
        ByteBuddy()
            .redefine(it.javaClass)
            .modifiers(Visibility.PUBLIC)
            .name("${it.javaClass.name}_Proxy")
            .method(ElementMatchers.namedOneOf(*it.javaClass.methods.filter { f ->
                Any::class.java.methods.any { it.name == f.name }.not() && f.name != "annotationType"
            }.map { it.name }.toTypedArray())).intercept(
                InvocationHandlerAdapter.of { proxy, method, args ->
                    arguments.find { it.name == method.name }?.toAny(method.returnType)?.arrayWrapper(method.returnType)
                        ?: it.javaClass.methods.find { it.name == method.name }?.also { it.isAccessible = true }
                            ?.invoke(it)
                }
            ).make().load(it.javaClass.classLoader).loaded.getDeclaredConstructor().also {
                it.isAccessible = true
            }.newInstance() as Annotation
    } ?: run {
        val qualifiedName = qualifiedName?.takeIf { !it.startsWith("java.") } ?: return null
        val map = mutableMapOf<String, ByteArray>()

        class MyClassLoader : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                return map[name]?.let {
                    defineClass(name, it, 0, it.size)
                } ?: super.loadClass(name, resolve)
            }
        }

        val classLoader = MyClassLoader()
        val annotationUnloaded = ByteBuddy()
            .makeAnnotation()
            .modifiers(Visibility.PUBLIC)
            .name(qualifiedName)
            .make()
        map[qualifiedName] = annotationUnloaded.bytes

        val proxyName = qualifiedName + "_Proxy"
        val proxyAnnotationUnloaded = ByteBuddy()
            .subclass(Object::class.java)
            .modifiers(Visibility.PUBLIC)
            .name(proxyName)
            .implement(java.lang.annotation.Annotation::class.java)
            .method(ElementMatchers.named("annotationType"))
            .intercept(FixedValue.value(annotationUnloaded.typeDescription))

        map[proxyName] = proxyAnnotationUnloaded.make().bytes
        val forName = Class.forName(proxyName, true, classLoader)
        return forName.getDeclaredConstructor().also {
            it.isAccessible = true
        }.newInstance() as Annotation
    }

    private fun Annotation.asAnnotationMirror(): AnnotationMirror {
        return object : AnnotationMirror {
            override fun getAnnotationType(): DeclaredType {
                return createDeclaredType(
                    getQualifiedName = { this@asAnnotationMirror.annotationClass.qualifiedName!! },
                    asElement = {
                        findClass(this@asAnnotationMirror.annotationClass.qualifiedName)?.asTypeElement()
                            ?: createTypeElement(this@asAnnotationMirror.annotationClass.qualifiedName!!)
                    })
            }

            override fun getElementValues(): Map<out ExecutableElement, AnnotationValue> {
                return if (listOf(
                        Transient::class.qualifiedName,
                        JsonConverter::class.qualifiedName
                    ).any { it == this@asAnnotationMirror.annotationClass.qualifiedName }
                ) {
                    this@asAnnotationMirror.javaClass.methods.filter { f ->
                        Any::class.java.methods.any { it.name == f.name }.not() && f.name != "annotationType"
                    }.mapNotNull { method ->
                        createExecutableElement(
                            getSimpleName = { createName(method.name) },
                        ) to createAnnotationValue {
                            this@asAnnotationMirror.javaClass.getMethod(method.name).also {
                                it.isAccessible = true
                            }.invoke(this@asAnnotationMirror)
                        }
                    }.toMap()
                } else {
                    emptyMap()
                }
            }
        }
    }
}
