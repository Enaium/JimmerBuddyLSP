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

import java.util.*
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.*
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

/**
 * @author Enaium
 */
fun createName(name: String): Name {
    return object : Name {
        override fun contentEquals(cs: CharSequence): Boolean {
            return name.contentEquals(cs)
        }

        override val length: Int
            get() = name.length

        override fun get(index: Int): Char {
            return name[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return name.subSequence(startIndex, endIndex)
        }

        override fun toString(): String {
            return name
        }
    }
}

fun createPackageElement(
    getQualifiedName: () -> Name = { TODO("Not yet implemented") },
    asType: () -> TypeMirror = { TODO("${getQualifiedName()} Not yet implemented") },
    getSimpleName: () -> Name = { TODO("${getQualifiedName()} Not yet implemented") },
    getEnclosedElements: () -> List<Element> = { TODO("${getQualifiedName()} Not yet implemented") },
    isUnnamed: () -> Boolean = { false },
    getEnclosingElement: () -> Element = { TODO("${getQualifiedName()} Not yet implemented") },
    getKind: () -> ElementKind = { ElementKind.PACKAGE },
    getModifiers: () -> Set<Modifier> = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotation: (Class<Annotation>) -> Annotation? = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { TODO("${getQualifiedName()} Not yet implemented") },
): PackageElement {
    return object : PackageElement {
        override fun asType(): TypeMirror {
            return asType()
        }

        override fun getQualifiedName(): Name {
            return getQualifiedName()
        }

        override fun getSimpleName(): Name {
            return getSimpleName()
        }

        override fun getEnclosedElements(): List<Element> {
            return getEnclosedElements()
        }

        override fun isUnnamed(): Boolean {
            return isUnnamed()
        }

        override fun getEnclosingElement(): Element {
            return getEnclosingElement()
        }

        override fun getKind(): ElementKind {
            return getKind()
        }

        override fun getModifiers(): Set<Modifier> {
            return getModifiers()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any, P : Any> accept(
            v: ElementVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitPackage(this, p)
        }
    }
}

fun createArrayType(
    getComponentType: () -> TypeMirror,
    getAnnotationMirrors: () -> List<AnnotationMirror> = { emptyList() },
    getAnnotation: (Class<Annotation>) -> Annotation? = { null },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { emptyArray() },
): ArrayType {
    return object : ArrayType {
        override fun getComponentType(): TypeMirror {
            return getComponentType()
        }

        override fun getKind(): TypeKind {
            return TypeKind.ARRAY
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror?>? {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation?> getAnnotation(annotationType: Class<A?>?): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation?> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any?, P : Any?> accept(
            v: TypeVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitArray(this, p)
        }
    }
}

fun createPrimitiveType(
    getKind: () -> TypeKind,
    getAnnotationMirrors: () -> List<AnnotationMirror> = { emptyList() },
    getAnnotation: (Class<Annotation>) -> Annotation? = { null },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { emptyArray() },
): PrimitiveType {
    return object : PrimitiveType {
        override fun getKind(): TypeKind {
            return getKind()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any, P : Any> accept(
            v: TypeVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitPrimitive(this, p)
        }

        override fun equals(other: Any?): Boolean {
            if (other is PrimitiveType) {
                return getKind() == other.kind
            }
            return super.equals(other)
        }
    }
}

fun createTypeVariable(
    asElement: () -> Element? = { TODO("Not yet implemented") },
    getUpperBound: () -> TypeMirror? = { TODO("Not yet implemented") },
    getLowerBound: () -> TypeMirror? = { TODO("Not yet implemented") },
    getKind: () -> TypeKind? = { TODO("Not yet implemented") },
): TypeVariable {
    return object : TypeVariable {
        override fun asElement(): Element? {
            return asElement()
        }

        override fun getUpperBound(): TypeMirror? {
            return getUpperBound()
        }

        override fun getLowerBound(): TypeMirror? {
            return getLowerBound()
        }

        override fun getKind(): TypeKind? {
            return getKind()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror?> {
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
            return v?.visitTypeVariable(this, p)
        }
    }
}

fun createDeclaredType(
    getQualifiedName: () -> String,
    getKind: () -> TypeKind = { TypeKind.DECLARED },
    asElement: () -> Element = { TODO("${getQualifiedName()} Not yet implemented") },
    getEnclosingType: () -> TypeMirror? = {
        object : TypeMirror {
            override fun getKind(): TypeKind {
                return TypeKind.NONE
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
    },
    getTypeArguments: () -> List<TypeMirror> = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotation: (Class<Annotation>) -> Annotation? = { TODO("${getQualifiedName()} Not yet implemented") },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { TODO("${getQualifiedName()} Not yet implemented") },
): DeclaredType {
    return object : DeclaredType {
        override fun asElement(): Element {
            return asElement()
        }

        override fun getEnclosingType(): TypeMirror? {
            return getEnclosingType()
        }

        override fun getTypeArguments(): List<TypeMirror> {
            return getTypeArguments()
        }

        override fun getKind(): TypeKind {
            return getKind()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<A>
        }

        override fun <R : Any, P : Any> accept(
            v: TypeVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitDeclared(this, p)
        }

        override fun toString(): String {
            return getQualifiedName()
        }

        override fun equals(other: Any?): Boolean {
            if (other is DeclaredType) {
                return this.toString() == other.toString()
            }
            return super.equals(other)
        }
    }
}

fun createVariableElement(
    getSimpleName: () -> Name = { TODO("Not yet implemented") },
    asType: () -> TypeMirror = { TODO("${getSimpleName()} Not yet implemented") },
    getConstantValue: () -> Any? = { TODO("${getSimpleName()} Not yet implemented") },
    getEnclosingElement: () -> Element = { TODO("${getSimpleName()} Not yet implemented") },
    getKind: () -> ElementKind = { TODO("${getSimpleName()} Not yet implemented") },
    getModifiers: () -> Set<Modifier> = { TODO("${getSimpleName()} Not yet implemented") },
    getEnclosedElements: () -> List<Element> = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotation: (Class<Annotation>) -> Annotation? = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { TODO("${getSimpleName()} Not yet implemented") },
): VariableElement {
    return object : VariableElement {
        override fun asType(): TypeMirror? {
            return asType()
        }

        override fun getConstantValue(): Any? {
            return getConstantValue()
        }

        override fun getSimpleName(): Name? {
            return getSimpleName()
        }

        override fun getEnclosingElement(): Element? {
            return getEnclosingElement()
        }

        override fun getKind(): ElementKind? {
            return getKind()
        }

        override fun getModifiers(): Set<Modifier?>? {
            return getModifiers()
        }

        override fun getEnclosedElements(): List<Element?>? {
            return getEnclosedElements()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror?>? {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation?> getAnnotation(annotationType: Class<A?>?): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation?> getAnnotationsByType(annotationType: Class<A?>?): Array<out A?>? {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<A?>?
        }

        override fun <R : Any?, P : Any?> accept(
            v: ElementVisitor<R?, P?>?,
            p: P?
        ): R? {
            return v?.visitVariable(this, p)
        }
    }
}

fun createExecutableElement(
    getSimpleName: () -> Name = { TODO("Not yet implemented") },
    asType: () -> TypeMirror = { TODO("${getSimpleName()} Not yet implemented") },
    getTypeParameters: () -> List<TypeParameterElement> = { TODO("${getSimpleName()} Not yet implemented") },
    getReturnType: () -> TypeMirror = { TODO("${getSimpleName()} Not yet implemented") },
    getParameters: () -> List<VariableElement> = { TODO("${getSimpleName()} Not yet implemented") },
    getReceiverType: () -> TypeMirror = { TODO("${getSimpleName()} Not yet implemented") },
    isVarArgs: () -> Boolean = { TODO("${getSimpleName()} Not yet implemented") },
    isDefault: () -> Boolean = { TODO("${getSimpleName()} Not yet implemented") },
    getThrownTypes: () -> List<TypeMirror> = { TODO("${getSimpleName()} Not yet implemented") },
    getDefaultValue: () -> AnnotationValue = { TODO("${getSimpleName()} Not yet implemented") },
    getEnclosingElement: () -> Element = { TODO("${getSimpleName()} Not yet implemented") },
    getKind: () -> ElementKind = { TODO("${getSimpleName()} Not yet implemented") },
    getModifiers: () -> Set<Modifier> = { TODO("${getSimpleName()} Not yet implemented") },
    getEnclosedElements: () -> List<Element> = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotation: (Class<Annotation>) -> Annotation? = { TODO("${getSimpleName()} Not yet implemented") },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation>? = { TODO("${getSimpleName()} Not yet implemented") },
): ExecutableElement {
    return object : ExecutableElement {
        override fun asType(): TypeMirror {
            return asType()
        }

        override fun getTypeParameters(): List<TypeParameterElement> {
            return getTypeParameters()
        }

        override fun getReturnType(): TypeMirror {
            return getReturnType()
        }

        override fun getParameters(): List<VariableElement> {
            return getParameters()
        }

        override fun getReceiverType(): TypeMirror {
            return getReceiverType()
        }

        override fun isVarArgs(): Boolean {
            return isVarArgs()
        }

        override fun isDefault(): Boolean {
            return isDefault()
        }

        override fun getThrownTypes(): List<TypeMirror> {
            return getThrownTypes()
        }

        override fun getDefaultValue(): AnnotationValue {
            return getDefaultValue()
        }

        override fun getEnclosingElement(): Element {
            return getEnclosingElement()
        }

        override fun getSimpleName(): Name {
            return getSimpleName()
        }

        override fun getKind(): ElementKind {
            return getKind()
        }

        override fun getModifiers(): Set<Modifier> {
            return getModifiers()
        }

        override fun getEnclosedElements(): List<Element> {
            return getEnclosedElements()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any, P : Any> accept(
            v: ElementVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitExecutable(this, p)
        }
    }
}

fun createTypeParameter(
    getSimpleName: () -> Name = { TODO("Not yet implemented") },
    asType: () -> TypeMirror = { TODO("Not yet implemented") },
    getGenericElement: () -> Element = { TODO("Not yet implemented") },
    getBounds: () -> List<TypeMirror> = { TODO("Not yet implemented") },
    getEnclosingElement: () -> Element = { TODO("Not yet implemented") },
    getKind: () -> ElementKind = { ElementKind.TYPE_PARAMETER },
    getModifiers: () -> Set<Modifier> = { TODO("Not yet implemented") },
    getEnclosedElements: () -> List<Element> = { TODO("Not yet implemented") },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { TODO("Not yet implemented") },
    getAnnotation: (Class<Annotation>) -> Annotation? = { TODO("Not yet implemented") },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = { TODO("Not yet implemented") },
): TypeParameterElement {
    return object : TypeParameterElement {
        override fun asType(): TypeMirror {
            return asType()
        }

        override fun getGenericElement(): Element {
            return getGenericElement()
        }

        override fun getBounds(): List<TypeMirror> {
            return getBounds()
        }

        override fun getEnclosingElement(): Element {
            return getEnclosingElement()
        }

        override fun getKind(): ElementKind {
            return getKind()
        }

        override fun getModifiers(): Set<Modifier> {
            return getModifiers()
        }

        override fun getSimpleName(): Name {
            return getSimpleName()
        }

        override fun getEnclosedElements(): List<Element> {
            return getEnclosedElements()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any, P : Any> accept(
            v: ElementVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visitTypeParameter(this, p)
        }
    }
}

fun createTypeElement(
    getQualifiedName: () -> Name = { TODO("Not yet implemented") },
    asType: () -> TypeMirror = { TODO("${getQualifiedName()} Not yet implemented") },
    getEnclosedElements: () -> List<Element> = { TODO("${getQualifiedName()} Not yet implemented") },
    getNestingKind: () -> NestingKind = { TODO("${getQualifiedName()} Not yet implemented") },
    getSimpleName: () -> Name = { TODO("${getQualifiedName()} Not yet implemented") },
    getSuperclass: () -> TypeMirror? = { null },
    getInterfaces: () -> List<TypeMirror> = { emptyList() },
    getTypeParameters: () -> List<TypeParameterElement> = { TODO("${getQualifiedName()} Not yet implemented") },
    getEnclosingElement: () -> Element = { TODO("${getQualifiedName()} Not yet implemented") },
    getKind: () -> ElementKind = { TODO("${getQualifiedName()} Not yet implemented") },
    getModifiers: () -> Set<Modifier> = { setOf(Modifier.PUBLIC) },
    getAnnotationMirrors: () -> List<AnnotationMirror> = { emptyList() },
    getAnnotation: (Class<Annotation>) -> Annotation? = { null },
    getAnnotationsByType: (Class<Annotation>) -> Array<Annotation> = {
        getAnnotation(it)?.let { arrayOf(it) } ?: emptyArray()
    },
): TypeElement {
    return object : TypeElement {
        override fun asType(): TypeMirror {
            return asType()
        }

        override fun getEnclosedElements(): List<Element> {
            return getEnclosedElements()
        }

        override fun getNestingKind(): NestingKind {
            return getNestingKind()
        }

        override fun getQualifiedName(): Name {
            return getQualifiedName()
        }

        override fun getSimpleName(): Name {
            return getSimpleName()
        }

        override fun getSuperclass(): TypeMirror? {
            return getSuperclass()
        }

        override fun getInterfaces(): List<TypeMirror> {
            return getInterfaces()
        }

        override fun getTypeParameters(): List<TypeParameterElement> {
            return getTypeParameters()
        }

        override fun getEnclosingElement(): Element {
            return getEnclosingElement()
        }

        override fun getKind(): ElementKind {
            return getKind()
        }

        override fun getModifiers(): Set<Modifier> {
            return getModifiers()
        }

        override fun getAnnotationMirrors(): List<AnnotationMirror> {
            return getAnnotationMirrors()
        }

        override fun <A : Annotation> getAnnotation(annotationType: Class<A>): A? {
            return getAnnotation.invoke(annotationType as Class<Annotation>) as A?
        }

        override fun <A : Annotation> getAnnotationsByType(annotationType: Class<A>): Array<out A> {
            return getAnnotationsByType.invoke(annotationType as Class<Annotation>) as Array<out A>
        }

        override fun <R : Any, P : Any> accept(
            v: ElementVisitor<R, P>,
            p: P?
        ): R {
            return v.visitType(this, p)
        }

        override fun toString(): String {
            return getQualifiedName().toString()
        }
    }
}

fun createProcessingEnvironment(
    getOptions: () -> Map<String, String> = { TODO("Not yet implemented") },
    getMessager: () -> Messager = { TODO("Not yet implemented") },
    getFiler: () -> Filer? = { null },
    getElementUtils: () -> Elements = { TODO("Not yet implemented") },
    getTypeUtils: () -> Types = { TODO("Not yet implemented") },
    getSourceVersion: () -> SourceVersion = { TODO("Not yet implemented") },
    getLocale: () -> Locale = { TODO("Not yet implemented") },
): ProcessingEnvironment {
    return object : ProcessingEnvironment {
        override fun getOptions(): Map<String, String> {
            return getOptions()
        }

        override fun getMessager(): Messager {
            return getMessager()
        }

        override fun getFiler(): Filer? {
            return getFiler()
        }

        override fun getElementUtils(): Elements {
            return getElementUtils()
        }

        override fun getTypeUtils(): Types {
            return getTypeUtils()
        }

        override fun getSourceVersion(): SourceVersion {
            return getSourceVersion()
        }

        override fun getLocale(): Locale {
            return getLocale()
        }
    }
}

fun createRoundEnvironment(
    rootElements: Set<Element>,
): RoundEnvironment {
    return object : RoundEnvironment {
        override fun processingOver(): Boolean {
            TODO("Not yet implemented")
        }

        override fun errorRaised(): Boolean {
            TODO("Not yet implemented")
        }

        override fun getRootElements(): Set<Element> {
            return rootElements
        }

        override fun getElementsAnnotatedWith(a: TypeElement): Set<Element> {
            TODO("Not yet implemented")
        }

        override fun getElementsAnnotatedWith(a: Class<out Annotation>): Set<Element> {
            return rootElements.filter { it.getAnnotation(a as Class<Annotation>) != null }
                .toSet()
        }
    }
}

fun createAnnotationValue(
    value: () -> Any? = { TODO("Not yet implemented") },
): AnnotationValue {
    return object : AnnotationValue {
        override fun getValue(): Any? {
            return try {
                value()?.let {
                    if (it is Class<*>) {
                        if (it == Void::class.java) {
                            "void"
                        } else {
                            it.name
                        }
                    } else {
                        it
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }

        override fun <R : Any?, P : Any?> accept(
            v: AnnotationValueVisitor<R, P>?,
            p: P?
        ): R? {
            return v?.visit(this, p)
        }

        override fun toString(): String {
            return getValue().toString()
        }
    }
}
