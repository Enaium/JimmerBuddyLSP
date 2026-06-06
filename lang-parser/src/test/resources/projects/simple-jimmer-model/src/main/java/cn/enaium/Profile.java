package cn.enaium;

import java.lang.String;
import java.util.UUID;
import org.babyfish.jimmer.sql.Formula;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.IdView;
import org.babyfish.jimmer.sql.OneToOne;

@Entity
public interface Profile extends BaseEntity {
    @IdView
    UUID peopleId();

    String firstName();

    String lastName();

    @Formula(dependencies = {"firstName", "lastName"})
    default String fullName() {
        return firstName() + ' ' + lastName();
    }

    String email();

    @OneToOne
    People people();
}