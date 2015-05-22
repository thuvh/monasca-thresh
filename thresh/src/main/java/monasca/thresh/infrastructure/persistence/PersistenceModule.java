/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package monasca.thresh.infrastructure.persistence;

import java.util.Properties;

import javax.inject.Singleton;

import monasca.common.hibernate.db.AlarmActionDb;
import monasca.common.hibernate.db.AlarmDb;
import monasca.common.hibernate.db.AlarmDefinitionDb;
import monasca.common.hibernate.db.AlarmMetricDb;
import monasca.common.hibernate.db.MetricDefinitionDb;
import monasca.common.hibernate.db.MetricDefinitionDimensionsDb;
import monasca.common.hibernate.db.MetricDimensionDb;
import monasca.common.hibernate.db.NotificationMethodDb;
import monasca.common.hibernate.db.SubAlarmDb;
import monasca.common.hibernate.db.SubAlarmDefinitionDb;
import monasca.common.hibernate.db.SubAlarmDefinitionDimensionDb;
import monasca.thresh.domain.service.AlarmDAO;
import monasca.thresh.domain.service.AlarmDefinitionDAO;
import monasca.thresh.infrastructure.persistence.sql.AlarmDefinitionSqlImpl;
import monasca.thresh.infrastructure.persistence.sql.AlarmSqlImpl;
import monasca.thresh.infrastructure.thresholding.DataSourceFactory;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.skife.jdbi.v2.DBI;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;

/**
 * Configures persistence related types.
 */
public class PersistenceModule extends AbstractModule {
  private final DataSourceFactory dbConfig;

  public PersistenceModule(DataSourceFactory dbConfig) {
    this.dbConfig = dbConfig;
  }

  @Override
  protected void configure() {

    if (dbConfig.isHibernateSupport()) {
      bind(AlarmDAO.class).to(AlarmSqlImpl.class).in(Scopes.SINGLETON);
      bind(AlarmDefinitionDAO.class).to(AlarmDefinitionSqlImpl.class).in(Scopes.SINGLETON);
    } else {
      bind(AlarmDAO.class).to(AlarmDAOImpl.class).in(Scopes.SINGLETON);
      bind(AlarmDefinitionDAO.class).to(AlarmDefinitionDAOImpl.class).in(Scopes.SINGLETON);
    }
  }

  @Provides
  @Singleton
  public DBI dbi() throws Exception {
    Class.forName(dbConfig.getDriverClass());
    return new DBI(dbConfig.getUrl(), dbConfig.getUser(), dbConfig.getPassword());
  }

  @Provides
  @Singleton
  public SessionFactory sessionFactory() {
    try {
      Configuration configuration = new Configuration();
      configuration.addAnnotatedClass(AlarmDb.class);
      configuration.addAnnotatedClass(AlarmDefinitionDb.class);
      configuration.addAnnotatedClass(AlarmMetricDb.class);
      configuration.addAnnotatedClass(MetricDefinitionDb.class);
      configuration.addAnnotatedClass(MetricDefinitionDimensionsDb.class);
      configuration.addAnnotatedClass(MetricDimensionDb.class);
      configuration.addAnnotatedClass(SubAlarmDefinitionDb.class);
      configuration.addAnnotatedClass(SubAlarmDefinitionDimensionDb.class);
      configuration.addAnnotatedClass(SubAlarmDb.class);
      configuration.addAnnotatedClass(AlarmActionDb.class);
      configuration.addAnnotatedClass(NotificationMethodDb.class);
      configuration.setProperties(this.getHikariProperties());
      ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();

      // builds a session factory from the service registry
      return configuration.buildSessionFactory(serviceRegistry);
    } catch (Throwable ex) {
      throw new ProvisionException("Failed to provision Hibernate DB", ex);
    }
  }

  private Properties getHikariProperties() {
    Properties properties = new Properties();
    properties.put("hibernate.connection.provider_class", dbConfig.getProviderClass());
    properties.put("hibernate.hbm2ddl.auto", dbConfig.getAutoConfig());
    properties.put("show_sql", false);
    properties.put("hibernate.hikari.dataSourceClassName", dbConfig.getDriverClass());
    properties.put("hibernate.hikari.dataSource.serverName", dbConfig.getServerName());
    properties.put("hibernate.hikari.dataSource.portNumber", dbConfig.getPortNumber());
    properties.put("hibernate.hikari.dataSource.databaseName", dbConfig.getDatabaseName());
    properties.put("hibernate.hikari.dataSource.user", dbConfig.getUser());
    properties.put("hibernate.hikari.dataSource.password", dbConfig.getPassword());
    properties.put("hibernate.hikari.dataSource.initialConnections", dbConfig.getMinSize());
    properties.put("hibernate.hikari.dataSource.maxConnections", dbConfig.getMaxSize());
    properties.put("hibernate.hikari.connectionTestQuery", dbConfig.getValidationQuery());
    properties.put("hibernate.hikari.connectionTimeout", "5000");
    properties.put("hibernate.hikari.initializationFailFast", "false");
    return properties;
  }
}
