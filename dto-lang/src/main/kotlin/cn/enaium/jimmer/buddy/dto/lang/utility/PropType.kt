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

import cn.enaium.jimmer.buddy.dto.lang.ImmutableProp

fun ImmutableProp.type(): PropType {
    return if (isId) {
        PropType.ID
    } else if (isKey) {
        PropType.KEY
    } else if (isEmbedded) {
        PropType.EMBEDDED
    } else if (isFormula) {
        PropType.FORMULA
    } else if (isTransient) {
        if (hasTransientResolver()) PropType.CALCULATION else PropType.TRANSIENT
    } else if (isRecursive) {
        PropType.RECURSIVE
    } else if (isAssociation(true)) {
        PropType.ASSOCIATION
    } else if (isList) {
        PropType.LIST
    } else if (isLogicalDeleted) {
        PropType.LOGICAL_DELETED
    } else if (isNullable) {
        PropType.NULLABLE
    } else {
        PropType.PROPERTY
    }
}

enum class PropType(val description: String) {
    ID("Id"),
    KEY("Key"),
    EMBEDDED("Embedded"),
    FORMULA("Formula"),
    CALCULATION("Calculation"),
    TRANSIENT("Transient"),
    RECURSIVE("Recursive"),
    ASSOCIATION("Association"),
    LIST("List"),
    LOGICAL_DELETED("LogicalDeleted"),
    NULLABLE("Nullable"),
    PROPERTY("Property")
}