package cn.enaium

import java.util.UUID
import kotlin.String
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.IdView
import org.babyfish.jimmer.sql.OneToOne
import org.babyfish.jimmer.sql.Formula

@Entity
public interface Profile : BaseEntity {
    @IdView
    public val peopleId: UUID

    public val firstName: String

    public val lastName: String

    @Formula(dependencies = ["firstName", "lastName"])
    val fullName: String
        get() = "$firstName $lastName"

    public val email: String

    @OneToOne
    public val people: People
}