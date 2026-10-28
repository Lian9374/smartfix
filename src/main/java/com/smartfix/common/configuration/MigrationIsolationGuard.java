package com.smartfix.common.configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import java.nio.file.Path;
import java.util.Arrays;
/** Fail before any datasource/Flyway initialization. Only the migration environment is permitted. */
public class MigrationIsolationGuard implements EnvironmentPostProcessor, Ordered {
 public int getOrder(){return Ordered.LOWEST_PRECEDENCE;}
 public void postProcessEnvironment(ConfigurableEnvironment e,SpringApplication a){
  String url=e.getProperty("spring.datasource.url","");
  require(e.getProperty("spring.flyway.url")==null && e.getProperty("spring.flyway.user")==null && e.getProperty("spring.flyway.password")==null,"Separate Flyway connection forbidden");
  require(e.getProperty("spring.datasource.jndi-name")==null && e.getProperty("spring.datasource.hikari.jdbc-url")==null && e.getProperty("spring.datasource.hikari.data-source-class-name")==null,"Datasource connection override forbidden");
  require(e.getProperty("spring.datasource.type")==null,"Custom datasource forbidden");
  if(Arrays.asList(e.getActiveProfiles()).contains("test") && url.startsWith("jdbc:h2:mem:")) return;
  require("true".equals(e.getProperty("smartfix.migration.isolated")),"Explicit isolated environment required");
  String scenario=e.getProperty("smartfix.migration.scenario","stage3");
  require(scenario.equals("stage3")||scenario.equals("stage7"),"Unknown isolation scenario");
  boolean stage7=scenario.equals("stage7");
  require((stage7?"jdbc:postgresql://127.0.0.1:15463/smartfix_stage7_core":"jdbc:postgresql://127.0.0.1:15453/smartfix_migration_core").equals(url),"Database URL is outside the stage3 allowlist");
  require((stage7?"sf_stage7_core":"sf_migration_core").equals(e.getProperty("spring.datasource.username")),"Wrong service database identity");
  require((stage7?"http://127.0.0.1:19094":"http://127.0.0.1:19092").equals(e.getProperty("smartfix.community.base-url","http://127.0.0.1:19092")),"Wrong internal service endpoint");
  require("127.0.0.1".equals(e.getProperty("server.address")),"Loopback binding required");
  require((stage7?"19095":"19091").equals(e.getProperty("server.port")),"Wrong migration web port");
  require("false".equals(e.getProperty("smartfix.notification.email.enabled")),"External mail must be disabled");
  require("false".equals(e.getProperty("smartfix.bootstrap-admin.enabled")),"Bootstrap must be disabled");
  require("false".equals(e.getProperty("smartfix.notification.delivery.scheduling-enabled")),"Delivery scanner must be disabled");
  require("false".equals(e.getProperty("smartfix.notification.delivery.eager-enabled")),"Eager delivery must be disabled");
  require((stage7?"SMARTFIX_STAGE7_CORE":"SMARTFIX_MIGRATION_sf_migration_core").equals(e.getProperty("server.servlet.session.cookie.name")),"Isolated cookie required");
  Path root=Path.of(e.getProperty("smartfix.migration.root","")).toAbsolutePath().normalize();
  require(root.equals(Path.of("C:/Users/zhour/smartfix-microservices").toAbsolutePath().normalize()),"Wrong migration root");
  require(root.equals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()),"Wrong working directory");
  Path uploads=Path.of(e.getProperty("smartfix.uploads.dir","")).toAbsolutePath().normalize();
  require(uploads.equals(root.resolve(stage7?".migration-runtime/stage7/uploads-core":".migration-runtime/uploads-sf_migration_core")),"Wrong attachment location");
 }
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("MIGRATION_ISOLATION_REJECTED: "+message);}
}
