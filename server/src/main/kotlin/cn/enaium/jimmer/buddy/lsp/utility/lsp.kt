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

package cn.enaium.jimmer.buddy.lsp.utility

import cn.enaium.jimmer.buddy.lsp.client
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.nio.file.Path

/**
 * @author Enaium
 */
fun Token.range(): Range {
    return Range(
        Position(line - 1, charPositionInLine),
        Position(
            line - 1 + text.count { it == '\n' },
            text.length - text.lastIndexOf('\n').let { if (it == -1) 0 else it } + charPositionInLine)
    )
}

fun Token.position(textLength: Boolean = false): Position {
    return Position(line - 1, charPositionInLine.let { if (textLength) it + text.length else it })
}

fun Range.overlaps(position: Position): Boolean {
    return if (start.line == position.line && end.line == position.line) {
        start.character <= position.character && end.character >= position.character
    } else if (start.line == position.line) {
        start.character <= position.character
    } else if (end.line == position.line) {
        end.character >= position.character
    } else {
        start.line <= position.line && end.line >= position.line
    }
}


fun ParserRuleContext.range(): Range {
    return Range(this.start.position(), this.stop.position())
}

fun findProjectDir(file: Path, projects: Set<Path>): Path? {
    return projects.find { file.startsWith(it) }
}

suspend fun process(title: String, block: suspend () -> Unit) {
    client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(title)))
    client?.notifyProgress(
        ProgressParams(
            Either.forLeft(title),
            Either.forLeft(WorkDoneProgressBegin().apply {
                this.title = title
                cancellable = false
            })
        )
    )
    block()
    client?.notifyProgress(
        ProgressParams(
            Either.forLeft(title),
            Either.forLeft(WorkDoneProgressEnd().apply {
                message = title
            })
        )
    )
}