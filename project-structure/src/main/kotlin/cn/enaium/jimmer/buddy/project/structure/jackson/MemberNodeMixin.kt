package cn.enaium.jimmer.buddy.project.structure.jackson

import cn.enaium.jimmer.buddy.lang.parser.node.MemberNode
import cn.enaium.jimmer.buddy.lang.parser.node.MethodNode
import cn.enaium.jimmer.buddy.lang.parser.node.PropertyNode
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * @author Enaium
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    value = [
        JsonSubTypes.Type(value = MethodNode::class),
        JsonSubTypes.Type(value = PropertyNode::class),
    ]
)
class MemberNodeMixin {
}