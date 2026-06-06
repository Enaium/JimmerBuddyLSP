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

package cn.enaium.jimmer.buddy.formatter

/**
 * @author Enaium
 */
class SpaceBuilder(val spaceToken: Int, val blockCommentTokens: Set<Int> = emptySet()) {

    private val models = mutableListOf<Model>()

    open class Model

    class Around(
        val token: Int,
        val beforeSpaceCount: Int? = null,
        val afterSpaceCount: Int? = null,
    ) : Model()

    class Between(
        val beforeToken: Int,
        val afterToken: Int,
        val spaceCount: Int
    ) : Model()

    class RuleAround(
        val ruleIndex: Int,
        val beforeSpaceCount: Int? = null,
        val afterSpaceCount: Int? = null,
    ) : Model()

    class RuleBetween(
        val ruleIndex: Int,
        val beforeToken: Int,
        val afterToken: Int,
        val spaceCount: Int,
    ) : Model()

    class TokenAndRuleBetween(
        val token: Int,
        val ruleIndex: Int,
        val spaceCount: Int,
    ) : Model()

    class RuleAndRuleBetween(
        val ruleBefore: Int,
        val ruleAfter: Int,
        val spaceCount: Int,
    ) : Model()

    class Indent(
        val ruleIndex: Int,
    ) : Model()

    // ---- Token-level helpers ----

    fun around(token: Int, space: Int): SpaceBuilder {
        models.add(Around(token, space, space))
        return this
    }

    fun around(token: Int, before: Int, after: Int): SpaceBuilder {
        models.add(Around(token, before, after))
        return this
    }

    fun before(token: Int, space: Int): SpaceBuilder {
        models.add(Around(token, beforeSpaceCount = space))
        return this
    }

    fun after(token: Int, space: Int): SpaceBuilder {
        models.add(Around(token, afterSpaceCount = space))
        return this
    }

    fun between(beforeToken: Int, afterToken: Int, space: Int): SpaceBuilder {
        models.add(Between(beforeToken, afterToken, space))
        return this
    }

    // ---- Rule-boundary helpers ----

    fun ruleAround(ruleIndex: Int, space: Int): SpaceBuilder {
        models.add(RuleAround(ruleIndex, space, space))
        return this
    }

    fun ruleAround(ruleIndex: Int, before: Int, after: Int): SpaceBuilder {
        models.add(RuleAround(ruleIndex, before, after))
        return this
    }

    fun ruleBefore(ruleIndex: Int, space: Int): SpaceBuilder {
        models.add(RuleAround(ruleIndex, beforeSpaceCount = space))
        return this
    }

    fun ruleAfter(ruleIndex: Int, space: Int): SpaceBuilder {
        models.add(RuleAround(ruleIndex, afterSpaceCount = space))
        return this
    }

    fun tokenAndRuleBetween(token: Int, ruleIndex: Int, space: Int): SpaceBuilder {
        models.add(TokenAndRuleBetween(token, ruleIndex, space))
        return this
    }

    fun ruleAndRuleBetween(ruleBefore: Int, ruleAfter: Int, space: Int): SpaceBuilder {
        models.add(RuleAndRuleBetween(ruleBefore, ruleAfter, space))
        return this
    }

    fun indent(ruleIndex: Int): SpaceBuilder {
        models.add(Indent(ruleIndex))
        return this
    }

    fun build(): List<Model> {
        return models
    }
}
