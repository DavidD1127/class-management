package com.example.classmanagement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class DatabaseMigrationTest {
    @Test void phaseThreeMigrationPreservesExistingStudentsAndClasses()throws Exception {
        String url="jdbc:h2:mem:legacy-phase-two;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("2").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.execute("INSERT INTO users(id,username,password,role) VALUES(18,'retained-admin','test-fixture-hash','ADMIN')");
            sql.execute("INSERT INTO classes(id,class_name,grade,academic_year,created_at,updated_at) VALUES(26,'Retained class','G1','2026',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO students(id,student_no,name,class_id,status,created_at,updated_at) VALUES(42,'RETAINED001','Retained student',26,'在读',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            var flyway=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
            try(var r=sql.executeQuery("SELECT s.id,s.student_no,s.class_id,c.class_name FROM students s JOIN classes c ON c.id=s.class_id")){
                assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(42);assertThat(r.getString(2)).isEqualTo("RETAINED001");assertThat(r.getLong(3)).isEqualTo(26);assertThat(r.getString(4)).isEqualTo("Retained class");
            }
            try(var r=sql.executeQuery("SELECT username,password,role FROM users WHERE id=18")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("retained-admin");assertThat(r.getString(2)).isEqualTo("test-fixture-hash");assertThat(r.getString(3)).isEqualTo("ADMIN");}
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
    @Test void nonemptyPhaseOneDatabasePreservesAccountsAndMigratesOnce() throws Exception {
        String url="jdbc:h2:mem:legacy-phase-one;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE users(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(100) NOT NULL UNIQUE,password VARCHAR(255) NOT NULL,role VARCHAR(30) NOT NULL)");
            sql.execute("INSERT INTO users(id,username,password,role) VALUES(17,'legacy-teacher','test-fixture-hash','TEACHER')");
            var flyway=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("1").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);
            try(var account=sql.executeQuery("SELECT id,username,password,role FROM users")) {
                assertThat(account.next()).isTrue();
                assertThat(account.getLong("id")).isEqualTo(17);
                assertThat(account.getString("username")).isEqualTo("legacy-teacher");
                assertThat(account.getString("password")).isEqualTo("test-fixture-hash");
                assertThat(account.getString("role")).isEqualTo("TEACHER");
                assertThat(account.next()).isFalse();
            }
            sql.execute("INSERT INTO classes(id,class_name,grade,academic_year,created_at,updated_at) VALUES(25,'Test class','G1','2026',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.execute("INSERT INTO students(id,student_no,name,class_id,status,created_at,updated_at) VALUES(41,'LEGACY001','Test student',25,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try(var student=sql.executeQuery("SELECT id,student_no,class_id FROM students")) {
                assertThat(student.next()).isTrue();
                assertThat(student.getLong("id")).isEqualTo(41);
                assertThat(student.getString("student_no")).isEqualTo("LEGACY001");
                assertThat(student.getLong("class_id")).isEqualTo(25);
                assertThat(student.next()).isFalse();
            }
            assertThatThrownBy(()->sql.execute("DELETE FROM classes WHERE id=25")).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(()->sql.execute("INSERT INTO students(student_no,name,class_id,status,created_at,updated_at) VALUES('LEGACY001','Another',25,'active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
