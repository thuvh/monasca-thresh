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

import java.util.Arrays;
import java.util.Properties;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.sql.DataSource;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import monasca.thresh.domain.service.AlarmDAO;
import monasca.thresh.domain.service.AlarmDefinitionDAO;
import monasca.thresh.infrastructure.persistence.jooq.AlarmDefinitionSqlImpl;
import monasca.thresh.infrastructure.persistence.jooq.AlarmSqlImpl;
import monasca.thresh.infrastructure.thresholding.DataSourceFactory;

import org.jooq.SQLDialect;
import org.jooq.tools.jdbc.JDBCUtils;
import org.skife.jdbi.v2.DBI;

/**
 * Configures persistence related types.
 */
public class PersistenceModule extends AbstractModule {

  private DataSourceFactory dbConfig;


  public PersistenceModule(DataSourceFactory dbConfig) {
    this.dbConfig = dbConfig;
  }

  @Override
  protected void configure() {
    bind(AlarmDAO.class).to(AlarmSqlImpl.class).in(Scopes.SINGLETON);
    bind(AlarmDefinitionDAO.class).to(AlarmDefinitionSqlImpl.class).in(Scopes.SINGLETON);
  }

  /**
   * Getter for datasource.
   * @return Datasource
   */
  @Provides
  @Singleton
  @Named("datasource")
  public DataSource getDataSource() {
    HikariConfig hiConfig = new HikariConfig();
    hiConfig.setDriverClassName(this.dbConfig.getDriverClass());
    hiConfig.setMaximumPoolSize(Integer.parseInt(this.dbConfig.getMaxSize()));
    hiConfig.setJdbcUrl(this.dbConfig.getUrl());
    hiConfig.setUsername(this.dbConfig.getUser());
    hiConfig.setPassword(this.dbConfig.getPassword());
    hiConfig.addDataSourceProperty("databaseName", this.dbConfig.getDatabaseName());
    hiConfig.setConnectionTestQuery(this.dbConfig.getValidationQuery());
    // hiConfig.addDataSourceProperty("cachePrepStmts", "true");
    // hiConfig.addDataSourceProperty("prepStmtCacheSize", "250");
    // hiConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    return new HikariDataSource(hiConfig);
  }

  /**
   * Getter for database dialect.
   * @return SQLDialect
   */
  @Provides
  @Singleton
  @Named("dialect")
  public SQLDialect getSqlDialect() {
    return JDBCUtils.dialect(this.dbConfig.getUrl());
  }
}
