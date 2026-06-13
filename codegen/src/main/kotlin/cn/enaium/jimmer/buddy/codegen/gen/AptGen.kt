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

package cn.enaium.jimmer.buddy.codegen.gen

import cn.enaium.jimmer.buddy.codegen.symbol.AptProcessor
import cn.enaium.jimmer.buddy.codegen.utility.createRoundEnvironment
import cn.enaium.jimmer.buddy.codegen.utility.toDtoFile
import cn.enaium.jimmer.buddy.lang.parser.index.ClassIndex
import cn.enaium.jimmer.buddy.lang.parser.node.BaseClassNode
import org.babyfish.jimmer.apt.client.DocMetadata
import org.babyfish.jimmer.apt.createAptOption
import org.babyfish.jimmer.apt.dto.AptDtoCompiler
import org.babyfish.jimmer.apt.dto.DtoGenerator
import org.babyfish.jimmer.apt.entry.EntryProcessor
import org.babyfish.jimmer.apt.immutable.ImmutableProcessor
import org.babyfish.jimmer.apt.immutable.meta.ImmutableType
import org.babyfish.jimmer.apt.transactional.TxProcessor
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.slf4j.LoggerFactory
import java.nio.file.Path
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

/**
 * @author Enaium
 */
class AptGen(
    projectDir: Path,
    classIndex: ClassIndex,
    genDir: Path,
    options: Map<String, String>
) : Gen(projectDir, classIndex, genDir, options) {

    private val logger = LoggerFactory.getLogger(AptGen::class.java)

    fun sourceProcess(genClasses: Set<BaseClassNode>) {
        try {
            val (pe, rootElements, sources) = AptProcessor(classIndex).process(genClasses)
            val option = createAptOption(emptyMap(), pe.elementUtils, pe.typeUtils, pe.filer)
            val roundEnv = createRoundEnvironment(rootElements)

            val immutableProcessor = ImmutableProcessor(option.context, pe.messager)
            val immutableTypeElements = try {
                ImmutableProcessor::class.java.getDeclaredMethod(
                    "parseImmutableTypes",
                    RoundEnvironment::class.java
                )
            } catch (_: Throwable) {
                null
            }?.let {
                it.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                it.invoke(
                    immutableProcessor,
                    roundEnv
                ) as Map<TypeElement, ImmutableType>
            } ?: emptyMap()
            ImmutableProcessor::class.java.declaredMethods.find { it.name == "generateJimmerTypes" }
                ?.also {
                    it.isAccessible = true
                    it.invoke(
                        immutableProcessor,
                        immutableTypeElements
                    )
                }
            EntryProcessor(option.context, immutableTypeElements.keys).process()
            TxProcessor(option.context).process(roundEnv)
            sources.forEach {
                it.write()
            }
        } catch (e: Throwable) {
            logger.error("Unable to gen sources", e)
        }
        System.gc()
    }

    fun dtoFileProcess(files: Set<DtoFile>, name: String? = null) {
        val (pe, rootElements, sources) = AptProcessor(classIndex).process(emptySet())
        val option = createAptOption(
            options,
            pe.elementUtils,
            pe.typeUtils,
            pe.filer
        )
        val elements = pe.elementUtils
        files.forEach { file ->
            try {
                val compiler =
                    AptDtoCompiler(file, elements, option.defaultNullableInputModifier)
                val typeElement: TypeElement =
                    elements.getTypeElement(compiler.sourceTypeName) ?: return@forEach
                val compile = compiler.compile(option.context.getImmutableType(typeElement))
                compile.forEach { dtoType ->
                    if (name != null && dtoType.name != name) {
                        return@forEach
                    }
                    DtoGenerator(
                        option.context,
                        DocMetadata(option.context),
                        dtoType
                    ).generate()
                }
                sources.forEach {
                    it.write()
                }
            } catch (e: Throwable) {
                logger.error("Unable to generate dto", e)
            }
        }
        System.gc()
    }

    fun dtoPathProcess(files: Set<Path>, name: String? = null) {
        dtoFileProcess(files.map { it.toDtoFile(projectDir) }.toSet(), name)
    }
}