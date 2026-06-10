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

package cn.enaium.jimmer.buddy.dto.lang.utility

import cn.enaium.jimmer.buddy.dto.lang.DtoParser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RuleContext

/**
 * @author Enaium
 */

fun ParserRuleContext.findPropTrace(): List<String> {
    val trace = mutableListOf<String>()
    var parent: RuleContext? = this.parent
    while (parent != null) {
        when (parent) {
            is DtoParser.PositivePropContext -> {
                parent.prop?.text?.also {
                    trace.add(it)
                }
            }

            is DtoParser.DtoBodyContext -> {
                (parent.parent?.getChild(0) as? DtoParser.FuncContext)?.also { ifuncContext ->
                    if (ifuncContext.name.text in listOf("flat")) {
                        ifuncContext.props?.firstOrNull()?.text?.also {
                            trace.add(it)
                        }
                    }
                }
            }
        }
        parent = parent.parent
    }
    return trace.reversed()
}