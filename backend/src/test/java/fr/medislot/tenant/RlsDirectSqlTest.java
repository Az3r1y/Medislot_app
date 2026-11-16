package fr.medislot.tenant;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.sql.*;

class RlsDirectSqlTest extends PostgresTestBase {
    static final String[] TABLES = {
        "professional", "slot", "appointment", "audit_event", "notification"
    };

    @Test
    void withoutContextEveryOwnedTableIsEmpty() throws Exception {
        try (var c = connection();
                var s = c.createStatement()) {
            for (String table : TABLES)
                try (var r = s.executeQuery("select count(*) from " + table)) {
                    r.next();
                    assertThat(r.getLong(1)).as(table).isZero();
                }
        }
    }

    @Test
    void contextFiltersEveryTableEvenWithoutJava() throws Exception {
        try (var c = connection()) {
            c.setAutoCommit(false);
            setClinic(c, "1");
            try (var s = c.createStatement()) {
                for (String table : TABLES)
                    try (var r =
                            s.executeQuery(
                                    "select count(*), count(*) filter(where clinic_id<>1) from "
                                            + table)) {
                        r.next();
                        assertThat(r.getLong(1)).as(table).isPositive();
                        assertThat(r.getLong(2)).as(table).isZero();
                    }
                try (var r =
                        s.executeQuery(
                                "select rolsuper,rolbypassrls from pg_roles where rolname=current_user")) {
                    r.next();
                    assertThat(r.getBoolean(1)).isFalse();
                    assertThat(r.getBoolean(2)).isFalse();
                }
            }
            c.rollback();
        }
    }

    @Test
    void foreignClinicInsertAndUpdateAreRejectedByWithCheck() throws Exception {
        for (String sql :
                new String[] {
                    "insert into professional(clinic_id,name,specialty,city,address,bio,fee) values(2,'Intrus','general','Lyon','test','test',30)",
                    "update professional set clinic_id=2 where clinic_id=1"
                }) {
            try (var c = connection()) {
                c.setAutoCommit(false);
                setClinic(c, "1");
                assertThatThrownBy(() -> c.createStatement().executeUpdate(sql))
                        .isInstanceOf(SQLException.class)
                        .satisfies(
                                error ->
                                        assertThat(((SQLException) error).getSQLState())
                                                .isEqualTo("42501"));
                c.rollback();
            }
        }
    }

    @Test
    void transactionEndRemovesContextAndOwnerIsAlsoIsolated() throws Exception {
        try (var c = connection()) {
            c.setAutoCommit(false);
            setClinic(c, "1");
            c.commit();
            try (var s = c.createStatement();
                    var r = s.executeQuery("select count(*) from appointment")) {
                r.next();
                assertThat(r.getLong(1)).isZero();
            }
            try (var s = c.createStatement();
                    var r =
                            s.executeQuery(
                                    "select count(*) from pg_class where relname in ('professional','slot','appointment','audit_event','notification') and relrowsecurity and relforcerowsecurity")) {
                r.next();
                assertThat(r.getLong(1)).isEqualTo(5);
            }
            c.rollback();
        }
        // FORCE doit aussi s'appliquer au propriétaire non-superutilisateur.
        try (var owner =
                DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            owner.setAutoCommit(false);
            try (var statement = owner.createStatement()) {
                statement.execute("create role rls_test_owner nologin nosuperuser nobypassrls");
                statement.execute("grant usage,create on schema public to rls_test_owner");
                statement.execute("alter table professional owner to rls_test_owner");
                statement.execute("set local role rls_test_owner");
                setClinic(owner, "1");
                try (var rows =
                        statement.executeQuery(
                                "select count(*),count(*) filter(where clinic_id<>1) from professional")) {
                    rows.next();
                    assertThat(rows.getLong(1)).isPositive();
                    assertThat(rows.getLong(2)).isZero();
                }
            } finally {
                owner.rollback();
            }
        }
    }
}
