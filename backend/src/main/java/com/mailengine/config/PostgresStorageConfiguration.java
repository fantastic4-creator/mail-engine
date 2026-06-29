package com.mailengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "mail-engine.runtime.storage-mode", havingValue = "postgres")
@Import({
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    FlywayAutoConfiguration.class
})
public class PostgresStorageConfiguration {
}
