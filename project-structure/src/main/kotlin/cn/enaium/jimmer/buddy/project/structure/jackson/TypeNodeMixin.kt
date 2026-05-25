package cn.enaium.jimmer.buddy.project.structure.jackson

import cn.enaium.jimmer.buddy.lang.parser.node.ClassTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.PrimitiveTypeNode
import cn.enaium.jimmer.buddy.lang.parser.node.WildcardTypeNode
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * @author Enaium
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(value = ClassTypeNode::class),
        JsonSubTypes.Type(value = PrimitiveTypeNode::class),
        JsonSubTypes.Type(value = WildcardTypeNode::class),
    ]
)
class TypeNodeMixin {
}