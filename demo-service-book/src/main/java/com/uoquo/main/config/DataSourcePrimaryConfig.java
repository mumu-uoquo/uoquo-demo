package com.uoquo.main.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 数据源 1
 */
@Configuration
@MapperScan(basePackages = DataSourcePrimaryConfig.PACKAGE, sqlSessionFactoryRef = DataSourcePrimaryConfig.NAME_SESSION_FACTORY)
public class DataSourcePrimaryConfig {
    static final String PACKAGE = "com.uoquo.**.mapper";
    static final String PREFIX  = "spring.datasource.druid.primary";

    static final String NAME_DATASOURCE       = "primaryDataSource";
    static final String NAME_TRANSACTION      = "primaryTransactionManager";
    static final String NAME_SESSION_FACTORY  = "primarySqlSessionFactory";
    static final String NAME_SESSION_TEMPLATE = "primarySqlSessionTemplate";

    @Primary
    @ConfigurationProperties(prefix = PREFIX)
    @Bean(name = NAME_DATASOURCE)
    public DataSource primaryDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = NAME_TRANSACTION)
    public DataSourceTransactionManager primaryTransactionManager(@Qualifier(NAME_DATASOURCE) DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @Bean(name = NAME_SESSION_FACTORY)
    public SqlSessionFactory primarySqlSessionFactory(@Qualifier(NAME_DATASOURCE) DataSource dataSource, Interceptor[] interceptors) throws Exception {
        final SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        sessionFactory.setPlugins(interceptors);
        return sessionFactory.getObject();
    }

    @Primary
    @Bean(name = NAME_SESSION_TEMPLATE)
    public SqlSessionTemplate primarySqlSessionTemplate(
        @Qualifier(NAME_SESSION_FACTORY) SqlSessionFactory sqlSessionFactory) throws Exception {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

}
