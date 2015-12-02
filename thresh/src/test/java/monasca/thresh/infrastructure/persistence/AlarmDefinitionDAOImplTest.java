/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monasca.thresh.infrastructure.persistence;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.jooq.Batch;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.Converter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.SelectWhereStep;
import org.jooq.Table;
import org.jooq.TransactionalRunnable;
import org.jooq.Update;
import org.jooq.UpdateSetFirstStep;
import org.jooq.UpdateWhereStep;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import monasca.common.jooq.Tables;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.thresh.domain.model.AlarmDefinition;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDefinitionDAO;
import monasca.thresh.infrastructure.persistence.jooq.AlarmDefinitionSqlImpl;


@Test(groups = "database")
public class AlarmDefinitionDAOImplTest {
  private static final String TENANT_ID = "bob";
  private static final String ALARM_DEFINITION_ID = "123";
  private static String ALARM_NAME = "90% CPU";
  private static String ALARM_DESCR = "Description for " + ALARM_NAME;

  private static final Joiner COMMA_JOINER = Joiner.on(',');
  private DataSource ds;
  private SQLDialect dialect;
  private Settings settings;

  private DBI db;
  private Handle handle;
  private AlarmDefinitionDAO dao;

  @BeforeClass
  protected void setupClass() throws Exception {
    HikariConfig config = new HikariConfig();

    //config.setJdbcUrl("jdbc:mysql://localhost:3306/mon");
    //config.setJdbcUrl("jdbc:postgresql://localhost:5432/mon");
    config.setJdbcUrl("jdbc:h2:mem:test_ad;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false");
    config.setDriverClassName("org.h2.Driver");
    //config.setDriverClassName("org.postgresql.Driver");
    //config.setDriverClassName("org.mariadb.jdbc.Driver");
    config.setUsername("tester");
    config.setPassword("testing");
    //config.setUsername("monapi");
    //config.setPassword("password");
    config.setConnectionTestQuery("SELECT 1");
    //config.addDataSourceProperty("cachePrepStmts", "true");
    //config.addDataSourceProperty("prepStmtCacheSize", "250");
    //config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    try {
      ds = new HikariDataSource(config);

      //dialect = JDBCUtils.dialect("jdbc:mysql://localhost:3306/mon");
      dialect = JDBCUtils.dialect("jdbc:h2:mem;MODE=PostgreSQL");
      //dialect = JDBCUtils.dialect("jdbc:postgresql://localhost:5432/mon");

      settings = new Settings().withRenderSchema(false);

      db = new DBI(ds);
      handle = db.open();

      // String ddl = Resources.toString(getClass()
      //                                 .getResource("alarm_mysql.sql"),
      //                                 Charset.defaultCharset());
      // String ddl = Resources.toString(getClass()
      //                                 .getResource("alarm_postgresql.sql"),
      //                                 Charset.defaultCharset());
      String ddl = Resources.toString(getClass()
                                      .getResource("alarm.sql"),
                                      Charset.defaultCharset());

      handle
        .createScript(ddl).execute();

    } catch (Exception e) {
      if (e.getCause() instanceof java.sql.SQLException) {
        java.sql.SQLException cause = (java.sql.SQLException)e.getCause();
        System.out.println(cause.getNextException());
      }
    }

    dao = new AlarmDefinitionSqlImpl(ds, dialect);
  }

  @AfterClass
  protected void afterClass() {
    handle.close();
  }

  @BeforeMethod
  protected void beforeMethod() {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);

    final monasca.common.jooq.tables.NotificationMethod nm = Tables.NOTIFICATION_METHOD;
    final monasca.common.jooq.tables.AlarmAction aa = Tables.ALARM_ACTION;
    final monasca.common.jooq.tables.Alarm a = Tables.ALARM;
    final monasca.common.jooq.tables.SubAlarm sa = Tables.SUB_ALARM;
    final monasca.common.jooq.tables.SubAlarmDefinition sad = Tables.SUB_ALARM_DEFINITION;
    final monasca.common.jooq.tables.SubAlarmDefinitionDimension sadd =
        Tables.SUB_ALARM_DEFINITION_DIMENSION;
    final monasca.common.jooq.tables.AlarmDefinition ad = Tables.ALARM_DEFINITION;
    final monasca.common.jooq.tables.AlarmMetric am = Tables.ALARM_METRIC;
    final monasca.common.jooq.tables.MetricDefinition md = Tables.METRIC_DEFINITION;
    final monasca.common.jooq.tables.MetricDefinitionDimensions mdd =
        Tables.METRIC_DEFINITION_DIMENSIONS;
    final monasca.common.jooq.tables.MetricDimension mdi = Tables.METRIC_DIMENSION;

    context.transaction(new TransactionalRunnable() {
        @Override
        public void run(Configuration configuration) throws Exception {
          DSLContext create = DSL.using(configuration);

          create.delete(nm).execute();
          create.delete(a).execute();
          create.delete(sa).execute();
          create.delete(sad).execute();
          create.delete(sadd).execute();
          create.delete(aa).execute();
          create.delete(ad).execute();
          create.delete(am).execute();
          create.delete(md).execute();
          create.delete(mdd).execute();
          create.delete(mdi).execute();
        }
      });
  }

  @Test(groups = "database")
  public void testGetById() {
    assertNull(dao.findById(ALARM_DEFINITION_ID));

    final AlarmExpression expression = new AlarmExpression("max(cpu{service=nova}) > 90");
    final AlarmDefinition alarmDefinition =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, expression, "LOW",
            false, Arrays.asList("fred"));
    insertAndCheck(alarmDefinition);

    final AlarmExpression expression2 = new AlarmExpression("max(cpu{service=swift}) > 90");
    final AlarmDefinition alarmDefinition2 =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, expression2, "LOW",
            false, Arrays.asList("hostname", "dev"));
    insertAndCheck(alarmDefinition2);

    // Make sure it works when there are no dimensions
    final AlarmExpression expression3 = new AlarmExpression("max(cpu) > 90");
    final AlarmDefinition alarmDefinition3 =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, expression3, "LOW",
            false, Arrays.asList("hostname", "dev"));
    insertAndCheck(alarmDefinition3);
  }

  @Test(groups = "database")
  public void testListAll() {
    assertEquals(0, dao.listAll().size());

    final AlarmExpression expression = new AlarmExpression("max(cpu{service=nova}) > 90");
    final AlarmDefinition alarmDefinition =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, expression, "LOW",
            false, Arrays.asList("fred", "barney"));
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    insertAlarmDefinition(context, alarmDefinition);

    verifyListAllMatches(alarmDefinition);
    final AlarmExpression expression2 = new AlarmExpression("max(cpu{service=swift}) > 90");
    final AlarmDefinition alarmDefinition2 =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, expression2, "LOW",
            false, Arrays.asList("fred", "barney", "wilma", "betty"));
    insertAlarmDefinition(context, alarmDefinition2);

    verifyListAllMatches(alarmDefinition, alarmDefinition2);
  }

  private void insertAndCheck(final AlarmDefinition alarmDefinition) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    insertAlarmDefinition(context, alarmDefinition);

    assertEquals(dao.findById(alarmDefinition.getId()), alarmDefinition);
  }

  private void verifyListAllMatches(final AlarmDefinition... alarmDefinitions) {
    List<AlarmDefinition> found = dao.listAll();
    assertEquals(alarmDefinitions.length, found.size());

    for (AlarmDefinition alarmDef : alarmDefinitions) {
      assertTrue(found.contains(alarmDef));
    }
  }

  // This method is not a test but without this TestNG tries to run it
  @Test(enabled = false)
  public static void insertAlarmDefinition(DSLContext context,
                                           final AlarmDefinition alarmDefinition) {
    final monasca.common.jooq.tables.AlarmDefinition ad = Tables.ALARM_DEFINITION;
    final monasca.common.jooq.tables.SubAlarmDefinition sad = Tables.SUB_ALARM_DEFINITION;
    final monasca.common.jooq.tables.SubAlarmDefinitionDimension sadd =
        Tables.SUB_ALARM_DEFINITION_DIMENSION;

    context.transaction(new TransactionalRunnable() {
        @Override
        public void run(Configuration configuration) throws Exception {
          DSLContext create = DSL.using(configuration);
          try {
            create.batch(create.insertInto(ad, ad.ID, ad.TENANT_ID, ad.NAME, ad.DESCRIPTION,
                                           ad.SEVERITY, ad.EXPRESSION,
                                           ad.MATCH_BY, ad.ACTIONS_ENABLED,
                                           ad.CREATED_AT, ad.UPDATED_AT, ad.DELETED_AT)
                       .values(null, null, null, null,
                               null, null, null, null,
                               DSL.currentTimestamp(),
                               DSL.currentTimestamp(),
                               (Field<Timestamp>) null)
                       )
            .bind(alarmDefinition.getId(),
                  alarmDefinition.getTenantId(),
                  alarmDefinition.getName(),
                  alarmDefinition.getDescription(),
                  "LOW",
                  alarmDefinition.getAlarmExpression().getExpression(),
                  alarmDefinition.getMatchBy().isEmpty() ? null : COMMA_JOINER.join(alarmDefinition
                                                                                    .getMatchBy()),
                  alarmDefinition.isActionsEnabled(),
                  null
                  )
            .execute();

            for (final SubExpression subExpression : alarmDefinition.getSubExpressions()) {
              final AlarmSubExpression alarmSubExpr = subExpression.getAlarmSubExpression();
              create.batch(create.insertInto(sad, sad.ID, sad.ALARM_DEFINITION_ID, sad.FUNCTION,
                                             sad.METRIC_NAME, sad.OPERATOR, sad.THRESHOLD,
                                             sad.PERIOD, sad.PERIODS,
                                             sad.CREATED_AT, sad.UPDATED_AT)
                           .values((org.jooq.Field)null, null, null,
                                   null, null, null,
                                   null, null,
                                   DSL.currentTimestamp(), DSL.currentTimestamp()
                                   )
                           )
                .bind(subExpression.getId(), alarmDefinition.getId(),
                      alarmSubExpr.getFunction().name(),
                      alarmSubExpr.getMetricDefinition().name,
                      alarmSubExpr.getOperator().name(), alarmSubExpr.getThreshold(),
                      alarmSubExpr.getPeriod(), alarmSubExpr.getPeriods())
                .execute();
              for (final Map.Entry<String, String> entry : alarmSubExpr
                     .getMetricDefinition()
                     .dimensions.entrySet()) {
                create.batch(create.insertInto(sadd, sadd.SUB_ALARM_DEFINITION_ID,
                                               sadd.DIMENSION_NAME, sadd.VALUE)
                             .values((String)null, null, null)
                             )
                  .bind(subExpression.getId(), entry.getKey(), entry.getValue())
                  .execute();
              }
            }
          } catch (org.jooq.exception.DataAccessException e) {
            if (e.getCause() instanceof java.sql.SQLException) {
              java.sql.SQLException cause = (java.sql.SQLException)e.getCause();
              System.out.println(cause.getNextException());
            }
          }
        }
      });
  }
}
