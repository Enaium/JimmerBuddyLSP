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

import cn.enaium.jimmer.buddy.codegen.symbol.KspProcessor
import cn.enaium.jimmer.buddy.codegen.utility.createKspOption
import cn.enaium.jimmer.buddy.codegen.utility.toDtoFile
import cn.enaium.jimmer.buddy.lang.parser.node.ClassNode
import cn.enaium.jimmer.buddy.project.structure.Environment
import com.google.devtools.ksp.getClassDeclarationByName
import org.babyfish.jimmer.dto.compiler.Anno
import org.babyfish.jimmer.ksp.KspDtoCompiler
import org.babyfish.jimmer.ksp.error.ErrorProcessor
import org.babyfish.jimmer.ksp.immutable.ImmutableProcessor
import org.babyfish.jimmer.ksp.transactional.TxProcessor
import org.babyfish.jimmer.ksp.tuple.TypedTupleProcessor
import java.nio.file.Path

/**
 * @author Enaium
 */
class KspGen(
    projectDir: Path,
    environment: Environment,
    genDir: Path,
    options: Map<String, String>
) : Gen(projectDir, environment, genDir, options) {
    fun sourceProcess(genClasses: Set<ClassNode>) {
        try {
            val (resolver, environment, sources) = KspProcessor(environment).process(genClasses)

            val option = createKspOption(emptyMap(), resolver, environment, environment.codeGenerator)
            ImmutableProcessor(
                option.context,
                option.isModuleRequired,
                option.excludedUserAnnotationPrefixes
            )
                .process()
            ErrorProcessor(
                option.context,
                option.checkedException
            )
                .process()
            TypedTupleProcessor(option.context, emptyList())
                .process()
            TxProcessor(option.context).process()
            sources.forEach {
                it.write()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun dtoProcess(files: Set<Path>, name: String? = null) {
        val (resolver, environment, sources) = KspProcessor(environment).process(emptySet())
        val option = createKspOption(options, resolver, environment, environment.codeGenerator)
        files.forEach { file ->
            try {
                val dtoFile = file.toDtoFile(projectDir)
                val compiler =
                    KspDtoCompiler(
                        dtoFile,
                        option.context.resolver,
                        option.defaultNullableInputModifier
                    )
                val classDeclarationByName =
                    resolver.getClassDeclarationByName(compiler.sourceTypeName)
                        ?: return@forEach
                val compile = compiler.compile(option.context.typeOf(classDeclarationByName))
                compile.forEach { dtoType ->
                    if (name != null && dtoType.name != name) {
                        return@forEach
                    }
                    val mutable = dtoType.annotations.firstOrNull {
                        it.qualifiedName == "org.babyfish.jimmer.kt.dto.KotlinDto"
                    }?.let {
                        val value = it.valueMap["immutability"] as Anno.EnumValue
                        when (value.constant) {
                            "IMMUTABLE" -> false
                            "MUTABLE" -> true
                            else -> null
                        }
                    } ?: option.mutable

                    org.babyfish.jimmer.ksp.dto.DtoGenerator(
                        option.context,
                        org.babyfish.jimmer.ksp.client.DocMetadata(option.context),
                        mutable,
                        dtoType,
                        option.context.environment.codeGenerator
                    ).generate(emptyList())
                }
                sources.forEach {
                    it.write()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}