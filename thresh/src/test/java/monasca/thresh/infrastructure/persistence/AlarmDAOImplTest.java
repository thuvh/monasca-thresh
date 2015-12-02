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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import monasca.common.jooq.Tables;
import monasca.common.model.alarm.AggregateFunction;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmState;
import monasca.common.model.metric.MetricDefinition;
import monasca.thresh.domain.model.Alarm;
import monasca.thresh.domain.model.AlarmDefinition;
import monasca.thresh.domain.model.MetricDefinitionAndTenantId;
import monasca.thresh.domain.model.SubAlarm;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDAO;
import monasca.thresh.infrastructure.persistence.jooq.AlarmSqlImpl;


/**
 * Tests of DAO for alarms.
 *
 * @author craigbr
 *
 */
@Test(groups = "database")
public class AlarmDAOImplTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmDAOImplTest.class);
  private static final String TENANT_ID = "bob";
  private static String ALARM_NAME = "90% CPU";
  private static String ALARM_DESCR = "Description for " + ALARM_NAME;
  private static Boolean ALARM_ENABLED = Boolean.TRUE;
  private MetricDefinitionAndTenantId newMetric;

  private AlarmDefinition alarmDef;

  private DBI db;
  private Handle handle;
  private AlarmDAO dao;

  private DataSource ds;
  private SQLDialect dialect;
  private Settings settings;


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

    dao = new AlarmSqlImpl(ds, dialect);
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

    final String expr = "avg(load{first=first_value}) > 10 and max(cpu) < 90";
    alarmDef =
        new AlarmDefinition(TENANT_ID, ALARM_NAME, ALARM_DESCR, new AlarmExpression(
            expr), "LOW", ALARM_ENABLED, new ArrayList<String>());
    AlarmDefinitionDAOImplTest.insertAlarmDefinition(context, alarmDef);

    final Map<String, String> dimensions = new HashMap<String, String>();
    dimensions.put("first", "first_value");
    dimensions.put("second", "second_value");
    final MetricDefinition mdl = new MetricDefinition("load", dimensions);
    newMetric = new MetricDefinitionAndTenantId(mdl, TENANT_ID);
  }

  @Test(groups = "database")
  public void shouldFindForAlarmDefinitionId() {
    verifyAlarmList(dao.findForAlarmDefinitionId(alarmDef.getId()));

    final Alarm firstAlarm = new Alarm(alarmDef, AlarmState.OK);
    firstAlarm.addAlarmedMetric(newMetric);
    LOGGER.debug("shouldFindForAlarmDefinitionId 1");
    dao.createAlarm(firstAlarm);

    final Alarm secondAlarm = new Alarm(alarmDef, AlarmState.OK);
    secondAlarm.addAlarmedMetric(newMetric);
    LOGGER.debug("shouldFindForAlarmDefinitionId 2");
    dao.createAlarm(secondAlarm);

    final AlarmDefinition secondAlarmDef =
        new AlarmDefinition(TENANT_ID, "Second", null,
                            new AlarmExpression("avg(cpu{disk=vda, instance_id=123}) > 10"),
                            "LOW", true, Arrays.asList("dev"));

    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    AlarmDefinitionDAOImplTest.insertAlarmDefinition(context, secondAlarmDef);

    final Alarm thirdAlarm = new Alarm(secondAlarmDef, AlarmState.OK);
    final Map<String, String> dims = new HashMap<>();
    dims.put("disk", "vda");
    dims.put("instance_id", "123");
    thirdAlarm.addAlarmedMetric(new MetricDefinitionAndTenantId(new MetricDefinition("cpu", dims),
                                                                secondAlarmDef.getTenantId()));
    LOGGER.debug("shouldFindForAlarmDefinitionId 3");
    dao.createAlarm(thirdAlarm);

    List<Alarm> ll =  dao.findForAlarmDefinitionId(alarmDef.getId());
    verifyAlarmList(ll, firstAlarm, secondAlarm);

    verifyAlarmList(dao.findForAlarmDefinitionId(secondAlarmDef.getId()), thirdAlarm);

    verifyAlarmList(dao.listAll(), firstAlarm, secondAlarm, thirdAlarm);
  }

  private void verifyAlarmList(final List<Alarm> found, Alarm... expected) {
    assertEquals(found.size(), expected.length);
    for (final Alarm alarm : expected) {
      assertTrue(found.contains(alarm));
    }
  }

  @Test(groups = "database")
  public void shouldFindById() {
    final Alarm newAlarm = new Alarm(alarmDef, AlarmState.OK);
    assertNull(dao.findById(newAlarm.getId()));

    dao.createAlarm(newAlarm);

    assertEquals(dao.findById(newAlarm.getId()), newAlarm);

    dao.addAlarmedMetric(newAlarm.getId(), newMetric);
    newAlarm.addAlarmedMetric(newMetric);

    assertEquals(dao.findById(newAlarm.getId()), newAlarm);

    // Make sure it can handle MetricDefinition with no dimensions
    final MetricDefinitionAndTenantId anotherMetric =
        new MetricDefinitionAndTenantId(new MetricDefinition("cpu", new HashMap<String, String>()),
            TENANT_ID);
    dao.addAlarmedMetric(newAlarm.getId(), anotherMetric);
    newAlarm.addAlarmedMetric(anotherMetric);

    assertEquals(dao.findById(newAlarm.getId()), newAlarm);
  }

  @Test(groups = "database")
  public void checkComplexMetrics() {
    final Alarm newAlarm = new Alarm(alarmDef, AlarmState.ALARM);

    for (final String hostname : Arrays.asList("vivi", "eleanore")) {
      for (final String metricName : Arrays.asList("cpu", "load")) {
        final Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("first", "first_value");
        dimensions.put("second", "second_value");
        dimensions.put("hostname", hostname);
        final MetricDefinition md = new MetricDefinition(metricName, dimensions);
        newAlarm.addAlarmedMetric(new MetricDefinitionAndTenantId(md, TENANT_ID));
      }
    }
    dao.createAlarm(newAlarm);

    final Alarm found = dao.findById(newAlarm.getId());
    // Have to check both ways because there was a bug in AlarmDAOImpl and it showed up if both
    // ways were tested
    assertTrue(newAlarm.equals(found));
    assertTrue(found.equals(newAlarm));
  }

  @Test(groups = "database")
  public void shouldUpdateState() {
    final Alarm newAlarm = new Alarm(alarmDef, AlarmState.OK);

    dao.createAlarm(newAlarm);
    dao.updateState(newAlarm.getId(), AlarmState.ALARM);
    assertEquals(dao.findById(newAlarm.getId()).getState(), AlarmState.ALARM);
  }

  @Test(groups = "database")
  public void shouldUpdate() {
    final Alarm newAlarm = new Alarm(alarmDef, AlarmState.OK);
    dao.createAlarm(newAlarm);

    final SubExpression first = alarmDef.getSubExpressions().get(0);
    final AggregateFunction newFunction = AggregateFunction.COUNT;
    first.getAlarmSubExpression().setFunction(newFunction);
    assertEquals(1, dao.updateSubAlarmExpressions(first.getId(), first.getAlarmSubExpression()));
    // Find the SubAlarm that was created from the changed SubExpression
    boolean found = false;
    for (final SubAlarm subAlarm : newAlarm.getSubAlarms()) {
      if (subAlarm.getAlarmSubExpressionId().equals(first.getId())) {
        found = true;
        // This is what dao.updateSubAlarmExpressions() should have changed
        subAlarm.getExpression().setFunction(newFunction);
        break;
      }
    }
    assertTrue(found);
    assertEquals(dao.findById(newAlarm.getId()), newAlarm);
  }

  @Test(groups = "database")
  public void validateNoDuplicates() {
    final Alarm alarm1 = new Alarm(alarmDef, AlarmState.OK);
    alarm1.addAlarmedMetric(newMetric);
    dao.createAlarm(alarm1);
    assertEquals(dao.findById(alarm1.getId()), alarm1);

    final Alarm alarm2 = new Alarm(alarmDef, AlarmState.OK);
    alarm2.addAlarmedMetric(newMetric);
    dao.createAlarm(alarm2);
    assertEquals(dao.findById(alarm2.getId()), alarm2);

    assertEquals(1, handle.select("select * from metric_definition").size());
    assertEquals(1, handle.select("select * from metric_definition_dimensions").size());
    List<Map<String, Object>> rows = handle.select("select * from metric_dimension");
    assertEquals(2, rows.size());
  }
}
