package com.mtai.mtairouteplanner.config;

import com.mtai.mtairouteplanner.data.MockDataDatabaseLoader;
import com.mtai.mtairouteplanner.data.MockDataDatabaseRepository;
import com.mtai.mtairouteplanner.data.PostgresMockDataRepository;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration
@Conditional(PostgresMockDataCondition.class)
public class PostgresMockDataConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresMockDataConfig.class);

    @Bean
    public DataSource postgresMockDataSource(Environment environment) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(environment.getProperty("spring.datasource.driver-class-name", "org.postgresql.Driver"));
        dataSource.setUrl(environment.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/mtai_route_planner"));
        dataSource.setUsername(environment.getProperty("spring.datasource.username", "mtai"));
        dataSource.setPassword(environment.getProperty("spring.datasource.password", "mtai_dev_password"));
        return dataSource;
    }

    @Bean(initMethod = "migrate")
    public Flyway postgresMockDataFlyway(DataSource postgresMockDataSource) {
        return Flyway.configure()
                .dataSource(postgresMockDataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    public JdbcTemplate postgresMockDataJdbcTemplate(DataSource postgresMockDataSource, Flyway postgresMockDataFlyway) {
        return new JdbcTemplate(postgresMockDataSource);
    }

    @Bean
    public TransactionTemplate postgresMockDataTransactionTemplate(DataSource postgresMockDataSource, Flyway postgresMockDataFlyway) {
        return new TransactionTemplate(new DataSourceTransactionManager(postgresMockDataSource));
    }

    @Bean
    public MockDataDatabaseRepository mockDataDatabaseRepository(
            JdbcTemplate postgresMockDataJdbcTemplate,
            TransactionTemplate postgresMockDataTransactionTemplate
    ) {
        return new PostgresMockDataRepository(postgresMockDataJdbcTemplate, postgresMockDataTransactionTemplate);
    }

    @Bean
    public MockDataDatabaseLoader mockDataDatabaseLoader(MockDataDatabaseRepository mockDataDatabaseRepository) {
        return new MockDataDatabaseLoader(mockDataDatabaseRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "mock-data.load-to-db", havingValue = "true")
    public ApplicationRunner mockDataDatabaseLoadRunner(MockDataDatabaseLoader mockDataDatabaseLoader) {
        return arguments -> {
            MockDataDatabaseLoader.LoadSummary summary = mockDataDatabaseLoader.loadToDatabase();
            log.info("Mock data loaded into PostgreSQL with row counts: {}", summary.rowCounts());
        };
    }
}
