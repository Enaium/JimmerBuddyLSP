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

import cn.enaium.jimmer.buddy.dto.lang.DtoLexer
import cn.enaium.jimmer.buddy.dto.lang.DtoParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

/**
 * @author Enaium
 */
class FormatterTest {

    @Test
    fun `test around formatting with indentation preservation`() {
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromPath(Path("build/resources/test/Space.dto")))).also { it.fill() }

        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromPath(Path("build/resources/test/Space.dto"))))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.DOT, 0)
                .around(DtoLexer.COMMA, 0, 1)
                .around(DtoLexer.COLON, 0, 1)
                .around(DtoLexer.SEMICOLON, 0, 1)
                .around(DtoLexer.RIGHT_ARROW, 1)
                .around(DtoLexer.EQUAL, 1)
                .around(DtoLexer.LEFT_PARENTHESIS, 0)
                .around(DtoLexer.RIGHT_PARENTHESIS, 0)
                .around(DtoLexer.AT, 0)
                .around(DtoLexer.HASH, 0)
                .around(DtoLexer.AS, 0)
                .between(DtoLexer.RIGHT_ARROW, DtoLexer.PACKAGE, 1)
                .between(DtoLexer.AS, DtoLexer.LEFT_PARENTHESIS, 0)
                .ruleAndRuleBetween(DtoParser.RULE_explicitProp, DtoParser.RULE_dtoBody, 1)
                .indent(DtoParser.RULE_dtoBody),
            ruleInstances = ruleInstances
        )

        val expected = "export cn.enaium.model.Space\n" +
                "    -> package cn.enaium.model.dto\n" +
                "\n" +
                "@KotlinDto\n" +
                "SimpleView {\n" +
                "    #allScalars\n" +
                "\n" +
                "    fun(prop1, prop2, prop3)\n" +
                "\n" +
                "    object1 {\n" +
                "        object2 {\n" +
                "        }\n" +
                "    }\n" +
                "}"
        assertEquals(expected, result)
    }

    @Test
    fun `test between spaces`() {
        val input = "foo=bar"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }

        // Between(EQUAL, Identifier, 1): ensure 1 space between EQUAL and Identifier
        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .between(DtoLexer.EQUAL, DtoLexer.Identifier, 1)
        )

        assertEquals("foo= bar", result)
    }

    @Test
    fun `test between around combined`() {
        val input = "foo =  bar"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }

        // between(EQUAL, Identifier, 1): ensure 1 space after EQUAL
        // Around(EQUAL, 1, 1): ensure 1 space before and after EQUAL
        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.EQUAL, 1, 1)
                .between(DtoLexer.EQUAL, DtoLexer.Identifier, 1)
        )

        assertEquals("foo = bar", result)
    }

    @Test
    fun `test indent around right arrow`() {
        val input = "export foo\n    -> bar"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.RIGHT_ARROW, 1)
        )

        // The 4 spaces before -> are indentation and should be preserved
        assertEquals("export foo\n    -> bar", result)
    }

    @Test
    fun `test indent preserved with multiple spaces before token`() {
        val input = "foo\n        ->bar"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.RIGHT_ARROW, 1)
        )

        // 8 spaces before -> are indentation, should be preserved
        // After -> there's no space, so 1 space should be added
        assertEquals("foo\n        -> bar", result)
    }

    @Test
    fun `test inline spaces are modified not indentation`() {
        val input = "foo  =  bar"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .around(DtoLexer.EQUAL, 1)
        )

        // "  =  " has 2 spaces before and after EQUAL
        // After formatting: exactly 1 space before and after EQUAL
        assertEquals("foo = bar", result)
    }

    @Test
    fun `test token and rule between`() {
        val input = "import  com.example.Foo"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .tokenAndRuleBetween(DtoLexer.IMPORT, DtoParser.RULE_qualifiedName, 1),
            ruleInstances = ruleInstances
        )

        assertEquals("import com.example.Foo", result)
    }

    @Test
    fun `test rule and rule between`() {
        val input = "@KotlinDto\n" +
                "SimpleView {\n" +
                "    #allScalars\n" +
                "\n" +
                "    fun(prop1, prop2, prop3)\n" +
                "}"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        // Between macro (#allScalars) and explicitProp (fun(...)): ensure 1 space
        // Since they're on different lines, no adjustment should occur
        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .ruleAndRuleBetween(DtoParser.RULE_macro, DtoParser.RULE_explicitProp, 0),
            ruleInstances = ruleInstances
        )

        // Should remain unchanged (cross-line spacing is preserved)
        assertEquals(input, result)
    }

    @Test
    fun `test indentation formatting`() {
        val input = "SimpleView {\n" +
                "#allScalars\n" +
                "    fun(prop1)\n" +
                "  prop2\n" +
                "}"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .indent(DtoParser.RULE_dtoBody),
            ruleInstances = ruleInstances
        )

        val expected = "SimpleView {\n" +
                "    #allScalars\n" +
                "    fun(prop1)\n" +
                "    prop2\n" +
                "}"
        assertEquals(expected, result)
    }

    @Test
    fun `test nested indentation`() {
        val input = "SimpleView {\n" +
                "object1 {\n" +
                "object2 {\n" +
                "}\n" +
                "}\n" +
                "}"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace)
                .indent(DtoParser.RULE_dtoBody),
            ruleInstances = ruleInstances
        )

        val expected = "SimpleView {\n" +
                "    object1 {\n" +
                "        object2 {\n" +
                "        }\n" +
                "    }\n" +
                "}"
        assertEquals(expected, result)
    }

    @Test
    fun `test doc comment indentation preserves continuation line alignment`() {
        val input = "SimpleView {\n" +
                "/**\n" +
                " * comment\n" +
                " */\n" +
                "fun(prop1)\n" +
                "}"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace, blockCommentTokens = setOf(DtoLexer.DocComment, DtoLexer.BlockComment))
                .around(DtoLexer.COMMA, 0, 1)
                .around(DtoLexer.LEFT_PARENTHESIS, 0)
                .around(DtoLexer.RIGHT_PARENTHESIS, 0)
                .ruleAround(DtoParser.RULE_explicitProp, 0)
                .indent(DtoParser.RULE_dtoBody),
            ruleInstances = ruleInstances
        )

        val expected = "SimpleView {\n" +
                "    /**\n" +
                "     * comment\n" +
                "     */\n" +
                "    fun(prop1)\n" +
                "}"
        assertEquals(expected, result)
    }

    @Test
    fun `test block comment indentation preserves continuation line alignment`() {
        val input = "SimpleView {\n" +
                "/*\n" +
                " * block\n" +
                " */\n" +
                "fun(prop1)\n" +
                "}"
        val tokenStream =
            CommonTokenStream(DtoLexer(CharStreams.fromString(input))).also { it.fill() }
        val parser = DtoParser(
            CommonTokenStream(DtoLexer(CharStreams.fromString(input)))
        )
        val tree = parser.dto()

        val ruleInstances = mutableMapOf<Int, MutableList<IntRange>>()
        collectRuleInstances(tree, ruleInstances)

        val result = Formatter(tokenStream.tokens).process(
            SpaceBuilder(DtoLexer.WhiteSpace, blockCommentTokens = setOf(DtoLexer.DocComment, DtoLexer.BlockComment))
                .around(DtoLexer.COMMA, 0, 1)
                .around(DtoLexer.LEFT_PARENTHESIS, 0)
                .around(DtoLexer.RIGHT_PARENTHESIS, 0)
                .ruleAround(DtoParser.RULE_explicitProp, 0)
                .indent(DtoParser.RULE_dtoBody),
            ruleInstances = ruleInstances
        )

        val expected = "SimpleView {\n" +
                "    /*\n" +
                "     * block\n" +
                "     */\n" +
                "    fun(prop1)\n" +
                "}"
        assertEquals(expected, result)
    }

    private fun collectRuleInstances(ctx: ParserRuleContext, instances: MutableMap<Int, MutableList<IntRange>>) {
        val ruleIndex = ctx.ruleIndex
        if (ruleIndex >= 0) {
            instances.computeIfAbsent(ruleIndex) { mutableListOf() }
                .add(ctx.start.tokenIndex..ctx.stop.tokenIndex)
        }
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is ParserRuleContext) {
                collectRuleInstances(child, instances)
            }
        }
    }
}
