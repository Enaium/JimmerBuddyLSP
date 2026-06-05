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
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import org.babyfish.jimmer.Scalar
import org.babyfish.jimmer.ksp.Context
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * @author Enaium
 */
class KspProcessor(val environment: Environment) {
    val caches = mutableMapOf<String, KSClassDeclaration>()
    private val classCache = mutableMapOf<String, BaseClassNode?>()

    private fun findClass(qualifiedName: String?): BaseClassNode? {
        if (qualifiedName == null) return null
        return classCache.getOrPut(qualifiedName) {
            environment.findClass(qualifiedName)
        }
    }

    fun process(genClass: Set<BaseClassNode>): Ksp {
        val ksFiles = mutableListOf<KSFile>()
        val ksClassDeclarationCaches = mutableMapOf<String, KSClassDeclaration>()
        genClass.forEach {
            ksClassDeclarationCaches[it.qualifiedName] = it.asKSClassDeclaration()
            ksFiles.add(
                createKSFile(
                    fileName = { it.qualifiedName.substringAfterLast(".") },
                    filePath = { it.path.toString() },
                    packageName = { ksClassDeclarationCaches[it.qualifiedName]!!.packageName },
                    declarations = { sequenceOf(ksClassDeclarationCaches[it.qualifiedName]!!) },
                    annotations = { sequenceOf() },
                )
            )
        }
        val sources = mutableListOf<Source>()

        return Ksp(
            resolver = createResolver(
                caches = ksClassDeclarationCaches, newFiles = ksFiles.asSequence()
            ),
            environment = SymbolProcessorEnvironment(
                mapOf("jimmer.buddy.ignoreResourceGeneration" to "true"),
                KotlinVersion.CURRENT,
                object : CodeGenerator {
                    override val generatedFile: Collection<File>
                        get() = TODO("Not yet implemented")

                    override fun associate(
                        sources: List<KSFile>, packageName: String, fileName: String, extensionName: String
                    ) {
                        TODO("Not yet implemented")
                    }

                    override fun associateByPath(
                        sources: List<KSFile>, path: String, extensionName: String
                    ) {
                        TODO("Not yet implemented")
                    }

                    override fun associateWithClasses(
                        classes: List<KSClassDeclaration>, packageName: String, fileName: String, extensionName: String
                    ) {
                        TODO("Not yet implemented")
                    }

                    override fun createNewFile(
                        dependencies: Dependencies, packageName: String, fileName: String, extensionName: String
                    ): OutputStream {
                        return object : ByteArrayOutputStream() {
                            override fun close() {
                                sources.add(
                                    Source(
                                        packageName = packageName,
                                        fileName = fileName,
                                        extensionName = extensionName,
                                        content = toString(Charsets.UTF_8)
                                    )
                                )
                            }
                        }
                    }

                    override fun createNewFileByPath(
                        dependencies: Dependencies, path: String, extensionName: String
                    ): OutputStream {
                        TODO("Not yet implemented")
                    }
                },
                object : KSPLogger {
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
                }),
            sources
        )
    }

    fun toImmutable(classNode: BaseClassNode): ImmutableType {
        val (resolver, kspEnvironment, sources) = KspProcessor(this.environment).process(emptySet())
        val context = Context(resolver, kspEnvironment)
        val classDeclarationByName = resolver.getClassDeclarationByName(classNode.qualifiedName)!!
        return context.typeOf(classDeclarationByName)
    }

    fun BaseClassNode.asKSClassDeclaration(): KSClassDeclaration {
        return caches[this.qualifiedName] ?: createKSClassDeclaration(
            qualifiedName = { createKSName(this.qualifiedName) },
            classKind = {
                when (this) {
                    is InterfaceNode -> ClassKind.INTERFACE
                    is EnumClassNode -> ClassKind.ENUM_CLASS
                    is AnnotationClassNode -> ClassKind.ANNOTATION_CLASS
                    else -> ClassKind.CLASS
                }
            },
            modifiers = {
                setOf(Modifier.PUBLIC) + if (this is DataClassNode) {
                    setOf(Modifier.DATA)
                } else {
                    emptySet()
                }
            },
            simpleName = { createKSName(this.qualifiedName.substringAfterLast(".")) },
            superTypes = {
                when (this) {
                    is InterfaceNode -> this.supers
                    is ClassNode -> this.supers
                    else -> emptySet()
                }.mapNotNull {
                    if (it is ClassTypeNode) {
                        val superClass = findClass(it.qualifiedName()) ?: return@mapNotNull null
                        createKSTypeReference(resolve = {
                            createKSType(declaration = {
                                superClass.asKSClassDeclaration()
                            })
                        })
                    } else {
                        null
                    }
                }.asSequence()
            },
            packageName = { createKSName(this.qualifiedName.substringBeforeLast(".")) },
            parentDeclaration = { null },
            annotations = { this.annotations.mapNotNull { annotation -> annotation.asKSAnnotation() }.asSequence() },
            declarations = {
                when (this) {
                    is InterfaceNode -> {
                        this.members.filterIsInstance<PropertyNode>().mapNotNull { property ->
                            val classType = property.type as? ClassTypeNode ?: return@mapNotNull null
                            val fqName = classType.qualifiedName() ?: return@mapNotNull null
                            createKSPropertyDeclaration(
                                qualifiedName = {
                                    createKSName(property.name)
                                },
                                simpleName = {
                                    createKSName(property.name)
                                },
                                annotations = {
                                    property.annotations.mapNotNull { annotation -> annotation.asKSAnnotation() }
                                        .asSequence()
                                },
                                modifiers = {
                                    if (!property.getter && !property.setter) {
                                        setOf(Modifier.ABSTRACT)
                                    } else {
                                        setOf()
                                    }
                                },
                                type = {
                                    classType.asKSTypeReference(fqName)
                                },
                                getter = {
                                    if (property.getter) {
                                        createKSPropertyGetter(modifiers = { setOf(Modifier.FUN) })
                                    } else {
                                        null
                                    }
                                },
                                parentDeclaration = { caches[this.qualifiedName] }
                            )
                        }.asSequence()
                    }

                    is EnumClassNode -> {
                        this.entries.map {
                            createKSClassDeclaration(
                                qualifiedName = { createKSName(it.name) },
                                classKind = { ClassKind.ENUM_ENTRY },
                                simpleName = { createKSName(it.name) },
                                parentDeclaration = { caches[this.qualifiedName] },
                            )
                        }.asSequence()
                    }

                    else -> emptySequence()
                }
            },
            asStarProjectedType = {
                createKSType(
                    this.qualifiedName,
                    declaration = {
                        this@asKSClassDeclaration.asKSClassDeclaration()
                    },
                    isAssignableFrom = {
                        it.toString() == this.qualifiedName
                    }
                )
            },
            asType = {
                this.asStarProjectedType()
            },
            typeParameters = {
                when (this) {
                    is InterfaceNode -> {
                        this.parameters
                    }

                    is ClassNode -> {
                        this.parameters
                    }

                    else -> null
                }?.map {
                    createKSTypeParameter(
                        name = {
                            createKSName(it.name)
                        }
                    )
                } ?: emptyList()
            }
        ).also {
            caches[this.qualifiedName] = it
        }
    }

    private fun ClassTypeNode.asKSTypeReference(fqName: String): KSTypeReference {
        return createKSTypeReference(
            resolve = {
                createKSType(
                    arguments = {
                        this.arguments.mapNotNull { argument ->
                            val qualifiedName = (argument as? ClassTypeNode)?.qualifiedName() ?: return@mapNotNull null
                            val klass = findClass(qualifiedName)
                            createKSTypeArgument(
                                type = {
                                    createKSTypeReference(
                                        resolve = {
                                            createKSType(
                                                declaration = {
                                                    klass?.asKSClassDeclaration() ?: createKSClassDeclaration(
                                                        classKind = { ClassKind.CLASS },
                                                        qualifiedName = {
                                                            createKSName(qualifiedName)
                                                        },
                                                        simpleName = {
                                                            createKSName(
                                                                qualifiedName.substringAfterLast(".")
                                                            )
                                                        },
                                                        packageName = {
                                                            createKSName(
                                                                qualifiedName.substringBeforeLast(".")
                                                            )
                                                        },
                                                        asStarProjectedType = {
                                                            this@createKSType
                                                        })
                                                }
                                            )
                                        }
                                    )
                                },
                                variance = { Variance.INVARIANT },
                            )
                        }
                    },
                    declaration = {
                        findClass(this@asKSTypeReference.qualifiedName())?.asKSClassDeclaration()
                            ?: createKSClassDeclaration(
                                classKind = { ClassKind.CLASS },
                                qualifiedName = { createKSName(fqName) },
                                simpleName = {
                                    createKSName(
                                        fqName.substringAfterLast(".")
                                    )
                                },
                                packageName = {
                                    createKSName(
                                        fqName.substringBeforeLast(".")
                                    )
                                },
                                asStarProjectedType = {
                                    this@createKSType
                                },
                                annotations = { sequenceOf() })
                    },
                    isMarkedNullable = { this.nullable }
                )
            },
        )
    }

    private fun AnnotationEntryNode.asKSAnnotation(): KSAnnotation? {
        val fqName =
            this@asKSAnnotation.qualifiedName?.takeIf { it.startsWith(Scalar::class.java.packageName) } ?: return null

        return createKSAnnotation(
            annotationType = createKSTypeReference(
                resolve = {
                    createKSType(
                        declaration = {
                            findClass(fqName)?.asKSClassDeclaration()
                                ?: createKSClassDeclaration(
                                    classKind = { ClassKind.ANNOTATION_CLASS },
                                    qualifiedName = { createKSName(fqName) },
                                    simpleName = { createKSName(fqName.substringAfterLast(".")) },
                                    packageName = { createKSName(fqName.substringBeforeLast(".")) },
                                    annotations = { emptySequence() })
                        }
                    )
                }
            ),
            shortName = { createKSName(fqName.substringAfterLast(".")) },
            arguments = {
                this.arguments.map { argument ->
                    createKSValueArgument(name = { createKSName(argument.name) }, value = { argument.value })
                }
            }
        )
    }

    private fun ClassTypeNode.qualifiedName() = qualifiedName ?: listOf(
        List::class, Set::class, Collection::class,
        Long::class, Int::class, Short::class,
        Byte::class, Float::class, Double::class, String::class,
    ).find { it.simpleName == name }?.qualifiedName

    private fun createResolver(
        caches: Map<String, KSClassDeclaration>,
        newFiles: Sequence<KSFile> = emptySequence(),
    ): Resolver {

        val collection = createKSClassDeclaration(asStarProjectedType = {
            createKSType(isAssignableFrom = {
                when (it.declaration.qualifiedName?.asString()?.substringBefore("<")) {
                    Collection::class.qualifiedName -> true
                    List::class.qualifiedName -> true
                    Set::class.qualifiedName -> true
                    MutableList::class.qualifiedName -> true
                    MutableSet::class.qualifiedName -> true
                    MutableCollection::class.qualifiedName -> true
                    else -> false
                }
            })
        })
        val list = createKSClassDeclaration(asStarProjectedType = {
            createKSType(isAssignableFrom = {
                when (it.declaration.qualifiedName?.asString()?.substringBefore("<")) {
                    List::class.qualifiedName -> true
                    MutableList::class.qualifiedName -> true
                    else -> false
                }
            })
        })
        val map = createKSClassDeclaration(asStarProjectedType = {
            createKSType(isAssignableFrom = {
                try {
                    when (it.declaration.qualifiedName?.asString()?.substringBefore("<")) {
                        Map::class.qualifiedName -> true
                        MutableMap::class.qualifiedName -> true
                        else -> false
                    }
                } catch (_: Throwable) {
                    false
                }
            })
        })

        return object : Resolver {
            override val builtIns: KSBuiltIns
                get() = object : KSBuiltIns {
                    override val annotationType: KSType
                        get() = TODO("Not yet implemented")
                    override val anyType: KSType
                        get() = TODO("Not yet implemented")
                    override val arrayType: KSType
                        get() = TODO("Not yet implemented")
                    override val booleanType: KSType
                        get() = TODO("Not yet implemented")
                    override val byteType: KSType
                        get() = TODO("Not yet implemented")
                    override val charType: KSType
                        get() = TODO("Not yet implemented")
                    override val doubleType: KSType
                        get() = TODO("Not yet implemented")
                    override val floatType: KSType
                        get() = TODO("Not yet implemented")
                    override val intType: KSType
                        get() = createKSType()
                    override val iterableType: KSType
                        get() = TODO("Not yet implemented")
                    override val longType: KSType
                        get() = TODO("Not yet implemented")
                    override val nothingType: KSType
                        get() = TODO("Not yet implemented")
                    override val numberType: KSType
                        get() = TODO("Not yet implemented")
                    override val shortType: KSType
                        get() = TODO("Not yet implemented")
                    override val stringType: KSType
                        get() = TODO("Not yet implemented")
                    override val unitType: KSType
                        get() = TODO("Not yet implemented")

                }

            override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun effectiveJavaModifiers(declaration: KSDeclaration): Set<Modifier> {
                TODO("Not yet implemented")
            }

            override fun getAllFiles(): Sequence<KSFile> {
                return newFiles
            }

            override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
                return when (name.asString()) {
                    "kotlin.collections.Collection" -> collection
                    "kotlin.collections.List" -> list
                    "kotlin.collections.Map" -> map
                    else -> caches[name.asString()]
                } ?: run {
                    var cd =
                        (findClass(name.asString()))?.asKSClassDeclaration()

                    if (cd == null) {
                        cd = findClass(name.asString())?.asKSClassDeclaration()
                    }

                    cd
                }
            }

            @KspExperimental
            override fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration> {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getDeclarationsInSourceOrder(container: KSDeclarationContainer): Sequence<KSDeclaration> {
                TODO("Not yet implemented")
            }

            override fun getFunctionDeclarationsByName(
                name: KSName, includeTopLevel: Boolean
            ): Sequence<KSFunctionDeclaration> {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getJavaWildcard(reference: KSTypeReference): KSTypeReference {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getJvmCheckedException(function: KSFunctionDeclaration): Sequence<KSType> {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getJvmCheckedException(accessor: KSPropertyAccessor): Sequence<KSType> {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getJvmName(declaration: KSFunctionDeclaration): String? {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getJvmName(accessor: KSPropertyAccessor): String? {
                TODO("Not yet implemented")
            }

            override fun getKSNameFromString(name: String): KSName {
                return createKSName(name)
            }

            @KspExperimental
            override fun getModuleName(): KSName {
                TODO("Not yet implemented")
            }

            override fun getNewFiles(): Sequence<KSFile> {
                return newFiles
            }

            @KspExperimental
            override fun getOwnerJvmClassName(declaration: KSFunctionDeclaration): String? {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getOwnerJvmClassName(declaration: KSPropertyDeclaration): String? {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getPackageAnnotations(packageName: String): Sequence<KSAnnotation> {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun getPackagesWithAnnotation(annotationName: String): Sequence<String> {
                TODO("Not yet implemented")
            }

            override fun getPropertyDeclarationByName(
                name: KSName, includeTopLevel: Boolean
            ): KSPropertyDeclaration? {
                TODO("Not yet implemented")
            }

            override fun getSymbolsWithAnnotation(
                annotationName: String, inDepth: Boolean
            ): Sequence<KSAnnotated> {
                TODO("Not yet implemented")
            }

            override fun getTypeArgument(
                typeRef: KSTypeReference, variance: Variance
            ): KSTypeArgument {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun isJavaRawType(type: KSType): Boolean {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun mapJavaNameToKotlin(javaName: KSName): KSName? {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun mapKotlinNameToJava(kotlinName: KSName): KSName? {
                TODO("Not yet implemented")
            }

            @KspExperimental
            override fun mapToJvmSignature(declaration: KSDeclaration): String? {
                TODO("Not yet implemented")
            }

            override fun overrides(
                overrider: KSDeclaration, overridee: KSDeclaration
            ): Boolean {
                TODO("Not yet implemented")
            }

            override fun overrides(
                overrider: KSDeclaration, overridee: KSDeclaration, containingClass: KSClassDeclaration
            ): Boolean {
                TODO("Not yet implemented")
            }
        }
    }
}