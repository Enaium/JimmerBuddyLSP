package cn.enaium;

import java.lang.String;
import java.util.List;
import java.util.UUID;
import org.babyfish.jimmer.sql.*;

@Entity
public interface Post extends BaseEntity {
    String title();

    String content();

    @IdView
    UUID peopleId();

    @ManyToOne
    People people();

    @ManyToMany
    List<Topic> topics();
}