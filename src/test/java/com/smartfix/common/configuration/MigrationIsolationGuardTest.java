package com.smartfix.common.configuration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;
import static org.junit.jupiter.api.Assertions.*;
class MigrationIsolationGuardTest {
 MockEnvironment valid(){return new MockEnvironment().withProperty("smartfix.migration.isolated","true").withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:15453/smartfix_migration_core").withProperty("spring.datasource.username","sf_migration_core").withProperty("server.address","127.0.0.1").withProperty("server.port","19091").withProperty("smartfix.notification.email.enabled","false").withProperty("smartfix.bootstrap-admin.enabled","false").withProperty("smartfix.notification.delivery.scheduling-enabled","false").withProperty("smartfix.notification.delivery.eager-enabled","false").withProperty("server.servlet.session.cookie.name","SMARTFIX_MIGRATION_sf_migration_core").withProperty("smartfix.migration.root","C:/Users/zhour/smartfix-microservices").withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix-microservices/.migration-runtime/uploads-sf_migration_core");}
 void check(MockEnvironment e){new MigrationIsolationGuard().postProcessEnvironment(e,new SpringApplication());}
 @Test void missingOptInFailsClosed(){assertThrows(IllegalStateException.class,()->check(new MockEnvironment()));}
 @Test void originalAndRecoveryAndRemoteUrlsRejected(){for(String u:new String[]{"jdbc:postgresql://localhost:5433/smartfix","jdbc:postgresql://127.0.0.1:15443/smartfix","jdbc:postgresql://remote:15453/smartfix_migration_core"})assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.url",u)));}
 @Test void wrongRoleRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("spring.datasource.username","postgres")));}
 @Test void reusedCookieRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("server.servlet.session.cookie.name","JSESSIONID")));}
 @Test void externalUploadRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix/uploads")));}
 @Test void realMailRejected(){assertThrows(IllegalStateException.class,()->check(valid().withProperty("smartfix.notification.email.enabled","true")));}
 @Test void alternateFlywayAndHikariConnectionRejected(){for(String k:new String[]{"spring.flyway.url","spring.flyway.user","spring.datasource.jndi-name","spring.datasource.hikari.jdbc-url","spring.datasource.type"})assertThrows(IllegalStateException.class,()->check(valid().withProperty(k,"forbidden")));}
 @Test void h2TestProfileCannotBypassFlywayGuard(){MockEnvironment e=new MockEnvironment().withProperty("spring.datasource.url","jdbc:h2:mem:unit").withProperty("spring.flyway.url","jdbc:postgresql://127.0.0.1:1/sentinel");e.setActiveProfiles("test");assertThrows(IllegalStateException.class,()->check(e));}
 @Test void stage7RequiresItsOwnDatabasePortRoleCookieAttachmentsAndPeer(){
  var e=valid().withProperty("smartfix.migration.scenario","stage7").withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:15463/smartfix_stage7_core").withProperty("spring.datasource.username","sf_stage7_core").withProperty("server.port","19095").withProperty("server.servlet.session.cookie.name","SMARTFIX_STAGE7_CORE").withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix-microservices/.migration-runtime/stage7/uploads-core").withProperty("smartfix.community.base-url","http://127.0.0.1:19094");
  assertDoesNotThrow(()->check(e));
  assertThrows(IllegalStateException.class,()->check(e.withProperty("smartfix.community.base-url","http://127.0.0.1:19090")));
  assertThrows(IllegalStateException.class,()->check(valid().withProperty("smartfix.migration.scenario","stage7")));
 }

 @Test void stage9RejectsProtectedResourcesAndAcceptsOnlyItsOwnIdentity(){
  String prior=System.getProperty("user.dir");
  try {System.setProperty("user.dir","C:/Users/zhour/smartfix-microservices");
   var e=valid().withProperty("smartfix.migration.scenario","stage9").withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:15483/smartfix_stage9_core").withProperty("spring.datasource.username","sf_stage9_core").withProperty("server.port","19111").withProperty("server.servlet.session.cookie.name","SMARTFIX_STAGE9_CORE").withProperty("smartfix.uploads.dir","C:/Users/zhour/smartfix-microservices/.migration-runtime/stage9/uploads-core").withProperty("smartfix.community.base-url","http://127.0.0.1:19114");
   assertDoesNotThrow(()->check(e));
   for(String forbidden:new String[]{"19090","19101","8080"})assertThrows(IllegalStateException.class,()->check(e.withProperty("server.port",forbidden)));
   e.withProperty("server.port","19111");
   assertThrows(IllegalStateException.class,()->check(e.withProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:15443/smartfix")));
  } finally {System.setProperty("user.dir",prior);}
 }
}
