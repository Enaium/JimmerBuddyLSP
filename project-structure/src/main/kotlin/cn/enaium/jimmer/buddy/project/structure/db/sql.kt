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

package cn.enaium.jimmer.buddy.project.structure.db

import cn.enaium.jimmer.buddy.lang.parser.entity.ClassEntity
import org.babyfish.jimmer.sql.dialect.SQLiteDialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.newKSqlClient
import org.babyfish.jimmer.sql.runtime.ConnectionManager
import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy
import org.sqlite.javax.SQLiteConnectionPoolDataSource
import java.nio.file.Path

/**
 * @author Enaium
 */
fun sql(path: Path): KSqlClient {
    return newKSqlClient {
        val dataSource = SQLiteConnectionPoolDataSource().apply {
            url = "jdbc:sqlite://${path.toFile().absolutePath}"
        }

        dataSource.connection.use {
            it.createStatement().use { statement ->
                statement.execute(
                    """
                    create table if not exists class_node(
                        qualified_name text primary key not null,
                        type text not null,
                        class_node blob not null,
                        path text not null
                    )
                """.trimIndent()
                )
            }
        }
        setDatabaseValidationMode(DatabaseValidationMode.ERROR)
        setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
        setScalarProvider(ClassEntity::classNode, ClassNodeScalarProvider())
        setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
        setDialect(SQLiteDialect())
    }
}