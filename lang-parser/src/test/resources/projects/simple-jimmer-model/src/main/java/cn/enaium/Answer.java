package cn.enaium;

import java.lang.String;
import java.util.List;
import java.util.UUID;
import org.babyfish.jimmer.sql.*;

@Entity
public interface Answer extends BaseEntity {
    String content();

    @IdView
    UUID peopleId();

    @IdView
    UUID questionId();

    @ManyToOne
    People people();

    @ManyToOne
    Question question();

    @OneToMany(
            mappedBy = "answer"
    )
    List<Comment> comments();
}