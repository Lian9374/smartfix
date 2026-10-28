package com.smartfix.communityapp;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;
import static org.junit.jupiter.api.Assertions.*;
class MigrationIsolationGuardTest {
 MockEnvironment valid(){return new MockEnvironment().withProperty("smartfix.migration.isolated","true").withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:15453/smartfix_migration_community").withProperty("spring.datasource.username","sf_migration_community").withProperty("server.address","127.0.0.1").withProperty("server.port","19092").withProperty("smartfix.notification.email.enabled","false").withProperty("smartfix.bootstrap-admin.enabled","false").withProperty("smartfix.notification.delivery.scheduling-enabled","false").withProperty("smartfix.notification.delivery.eager-enabled","false").withProperty("server.servlet.session.cookie.name","SMARTFIX_MIGRATION_sf_migration_community").withProperty("smartfix.migration.root","C:/Users/zhour/smartfix-microservices").withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix-microservices/.migration-runtime/uploads-sf_migration_community");}
 void check(MockEnvironment e){new MigrationIsolationGuard().postProcessEnvironment(e,new SpringApplication());}
 @Test void missingOptInFailsClosed(){assertThrows(IllegalStateException.class,()->check(new MockEnvironment()));}
 @Test void originalAndRecoveryAndRemoteUrlsRejected(){for(String u:new String[]{"jdbc:postgresql://localhost:5433/smartfix","jdbc:postgresql://127.0.0.1:15443/smartfix","jdbc:postgresql://remote:15453/smartfix_migration_community"})assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.url",u)));}
 @Test void wrongRoleRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.username","postgres")));}
 @Test void reusedCookieRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("server.servlet.session.cookie.name","JSESSIONID")));}
 @Test void externalUploadRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix/uploads")));}
 @Test void realMailRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("smartfix.notification.email.enabled","true")));}
 @Test void alternateFlywayAndHikariConnectionRejected(){for(String k:new String[]{"spring.flyway.url","spring.flyway.user","spring.datasource.jndi-name","spring.datasource.hikari.jdbc-url","spring.datasource.type"})assertThrows(IllegalStateException.class,()->check(valid().withProperty(k,"forbidden")));}
 @Test void h2TestProfileCannotBypassFlywayGuard(){MockEnvironment e=new MockEnvironment().withProperty("spring.datasource.url","jdbc:h2:mem:unit").withProperty("spring.flyway.url","jdbc:postgresql://127.0.0.1:1/sentinel");e.setActiveProfiles("test");assertThrows(IllegalStateException.class,()->check(e));}
}
