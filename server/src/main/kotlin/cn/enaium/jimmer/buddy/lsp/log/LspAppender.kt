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

package cn.enaium.jimmer.buddy.lsp.log

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.Layout
import cn.enaium.jimmer.buddy.lsp.client
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType


/**
 * @author Enaium
 */
class LspAppender : AppenderBase<ILoggingEvent>() {

    private var layout: Layout<ILoggingEvent>? = null

    fun setLayout(layout: Layout<ILoggingEvent>) {
        this.layout = layout
    }

    override fun append(eventObject: ILoggingEvent) {
        layout?.doLayout(eventObject)?.also {
            client?.logMessage(
                MessageParams(
                    when (eventObject.level) {
                        Level.ERROR -> MessageType.Error
                        Level.WARN -> MessageType.Warning
                        Level.INFO -> MessageType.Info
                        else -> MessageType.Log
                    }, it
                )
            )
        }
    }
}