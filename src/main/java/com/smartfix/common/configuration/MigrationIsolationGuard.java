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
  require("jdbc:postgresql://127.0.0.1:15453/smartfix_migration_core".equals(url),"Database URL is outside the stage3 allowlist");
  require("sf_migration_core".equals(e.getProperty("spring.datasource.username")),"Wrong service database identity");
  require("127.0.0.1".equals(e.getProperty("server.address")),"Loopback binding required");
  require("19091".equals(e.getProperty("server.port")),"Wrong migration web port");
  require("false".equals(e.getProperty("smartfix.notification.email.enabled")),"External mail must be disabled");
  require("false".equals(e.getProperty("smartfix.bootstrap-admin.enabled")),"Bootstrap must be disabled");
  require("false".equals(e.getProperty("smartfix.notification.delivery.scheduling-enabled")),"Delivery scanner must be disabled");
  require("false".equals(e.getProperty("smartfix.notification.delivery.eager-enabled")),"Eager delivery must be disabled");
  require("SMARTFIX_MIGRATION_sf_migration_core".equals(e.getProperty("server.servlet.session.cookie.name")),"Isolated cookie required");
  Path root=Path.of(e.getProperty("smartfix.migration.root","")).toAbsolutePath().normalize();
  require(root.equals(Path.of("C:/Users/zhour/smartfix-microservices").toAbsolutePath().normalize()),"Wrong migration root");
  require(root.equals(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()),"Wrong working directory");
  Path uploads=Path.of(e.getProperty("smartfix.uploads.dir","")).toAbsolutePath().normalize();
  require(uploads.equals(root.resolve(".migration-runtime/uploads-sf_migration_core")),"Wrong attachment location");
 }
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("MIGRATION_ISOLATION_REJECTED: "+message);}
}
