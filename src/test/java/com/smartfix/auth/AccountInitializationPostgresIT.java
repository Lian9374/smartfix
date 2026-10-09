package com.smartfix.auth;

import com.smartfix.user.domain.Role;
import com.smartfix.user.dto.CreateUserCommand;
import com.smartfix.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Actual PostgreSQL row locking and transaction rollback, using only an isolated test schema. */
@SpringBootTest(properties={"spring.datasource.driver-class-name=org.postgresql.Driver","spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true"})
@ActiveProfiles("test")
class AccountInitializationPostgresIT {
    static final String schema="smartfix_account_init_"+UUID.randomUUID().toString().replace("-","");
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    static String env(String name){String value=System.getenv(name);assertThat(value).as(name).isNotBlank();return value;}
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) throws Exception {
        String url=env("TEST_DB_URL"), username=env("TEST_DB_USERNAME"), password=env("TEST_DB_PASSWORD");
        assertThat(url).startsWith("jdbc:postgresql:");
        try(var c=DriverManager.getConnection(url,username,password);var s=c.createStatement()){
            assertThat(c.getCatalog()).endsWith("_test");s.execute("CREATE SCHEMA "+schema);
        }
        p.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        p.add("spring.datasource.username",()->username);p.add("spring.datasource.password",()->password);
        p.add("spring.flyway.schemas",()->schema);p.add("spring.flyway.default-schema",()->schema);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var c=DriverManager.getConnection(env("TEST_DB_URL"),env("TEST_DB_USERNAME"),env("TEST_DB_PASSWORD"));var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    @BeforeEach void reset(){jdbc.update("DELETE FROM users");jdbc.update("UPDATE account_initialization SET completed=false,completed_at=null WHERE id=1");}
    CreateUserCommand command(String name,String password){var c=new CreateUserCommand();c.setUsername(name);c.setDisplayName("Synthetic Administrator");c.setRole(Role.ADMINISTRATOR);c.setPassword(password);return c;}
    @Test void concurrentDifferentNamesCreateExactlyOneAdministrator() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)){
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            Callable<Boolean> first=()->{ready.countDown();start.await();return users.createInitialAdministrator(command("initial.one","BootstrapPassword9"));};
            Callable<Boolean> second=()->{ready.countDown();start.await();return users.createInitialAdministrator(command("initial.two","BootstrapPassword9"));};
            var a=executor.submit(first);var b=executor.submit(second);assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)^b.get(20,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMINISTRATOR'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed FROM account_initialization WHERE id=1",Boolean.class)).isTrue();
        assertThat(users.createInitialAdministrator(command("changed.configuration","BootstrapPassword9"))).isFalse();
    }
    @Test void invalidInitialPasswordRollsBackMarkerThenValidInitializationSucceeds(){
        assertThatThrownBy(()->users.createInitialAdministrator(command("initial.invalid","weak"))).isInstanceOf(com.smartfix.common.exception.InputValidationException.class);
        assertThat(jdbc.queryForObject("SELECT completed FROM account_initialization WHERE id=1",Boolean.class)).isFalse();
        assertThat(users.createInitialAdministrator(command("initial.valid","BootstrapPassword9"))).isTrue();
    }
}
