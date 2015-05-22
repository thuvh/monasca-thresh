/*
 * Copyright 2015 FUJITSU LIMITED
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

package monasca.thresh.infrastructure.persistence.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.inject.Inject;

import monasca.common.hibernate.db.AlarmDb;
import monasca.common.hibernate.db.AlarmMetricDb;
import monasca.common.hibernate.db.MetricDefinitionDb;
import monasca.common.hibernate.db.MetricDefinitionDimensionsDb;
import monasca.common.hibernate.db.MetricDimensionDb;
import monasca.common.hibernate.db.SubAlarmDb;
import monasca.common.model.alarm.AlarmState;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;
import monasca.thresh.domain.model.Alarm;
import monasca.thresh.domain.model.MetricDefinitionAndTenantId;
import monasca.thresh.domain.model.SubAlarm;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDAO;

import org.apache.commons.codec.digest.DigestUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AlarmDAO hibernate implementation.
 *
 * @author lukasz.zajaczkowski@ts.fujitsu.com
 */
public class AlarmSqlImpl implements AlarmDAO {
  private static final Logger logger = LoggerFactory.getLogger(AlarmSqlImpl.class);
  private static final int ALARM_ID = 0;
  private static final int ALARM_DEFINITION_ID = 1;
  private static final int ALARM_STATE = 2;
  private static final int SUB_ALARM_ID = 3;
  private static final int ALARM_EXPRESSION = 4;
  private static final int SUB_EXPRESSION_ID = 5;
  private static final int TENANT_ID = 6;
  private static final int MAX_COLUMN_LENGTH = 255;
  private final SessionFactory sessionFactory;

  @Inject
  public AlarmSqlImpl(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Alarm findById(String id) {
    final List<Alarm> alarms = findAlarms("a.id = :alarm_id ", "alarm_id", id);
    if (alarms.isEmpty()) {
      return null;
    } else {
      return alarms.get(0);
    }
  }

  @Override
  public List<Alarm> findForAlarmDefinitionId(String alarmDefinitionId) {
    return findAlarms("a.alarm_definition_id = :alarmDefinitionId ", "alarmDefinitionId", alarmDefinitionId);
  }

  @Override
  public List<Alarm> listAll() {
    return findAlarms("1=1"); // This is basically "true" and gets optimized out
  }

  @Override
  public void updateState(String id, AlarmState state) {
    Transaction tx = null;
    Session session = null;
    try {
      session = sessionFactory.openSession();
      tx = session.beginTransaction();
      AlarmDb alarm = (AlarmDb) session.get(AlarmDb.class, id);
      alarm.setState(state);
      alarm.setUpdated_at(new DateTime());
      alarm.setState_updated_at(new DateTime());
      session.update(alarm);

      tx.commit();
    } catch (RuntimeException e) {
      try {
        tx.rollback();
      } catch (RuntimeException rbe) {
        logger.error("Couldn’t roll back transaction", rbe);
      }
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  @Override
  public void addAlarmedMetric(String id, MetricDefinitionAndTenantId metricDefinition) {
    Transaction tx = null;
    Session session = null;
    try {
      session = sessionFactory.openSession();
      tx = session.beginTransaction();
      createAlarmedMetric(session, metricDefinition, id);
      tx.commit();
    } catch (RuntimeException e) {
      try {
        tx.rollback();
      } catch (RuntimeException rbe) {
        logger.error("Couldn’t roll back transaction", rbe);
      }
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  @Override
  public void createAlarm(Alarm newAlarm) {
    Transaction tx = null;
    Session session = null;
    try {
      session = sessionFactory.openSession();
      tx = session.beginTransaction();
      AlarmDb alarm = new AlarmDb(newAlarm.getId(), newAlarm.getAlarmDefinitionId(), newAlarm.getState(), null, null, new DateTime(), new DateTime(), new DateTime());
      session.save(alarm);

      for (final SubAlarm subAlarm : newAlarm.getSubAlarms()) {

        SubAlarmDb subAlarmDb =
            new SubAlarmDb(subAlarm.getId(), subAlarm.getAlarmId(), subAlarm.getAlarmSubExpressionId(), subAlarm.getExpression().getExpression(),
                new DateTime(), new DateTime());

        session.save(subAlarmDb);
      }
      for (final MetricDefinitionAndTenantId md : newAlarm.getAlarmedMetrics()) {
        createAlarmedMetric(session, md, newAlarm.getId());
      }

      tx.commit();
    } catch (RuntimeException e) {
      try {
        tx.rollback();
      } catch (RuntimeException rbe) {
        logger.error("Couldn’t roll back transaction", rbe);
      }
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  @Override
  public int updateSubAlarmExpressions(String alarmSubExpressionId, AlarmSubExpression alarmSubExpression) {
    Transaction tx = null;
    Session session = null;
    int updatedItems = 0;
    try {
      session = sessionFactory.openSession();
      tx = session.beginTransaction();
      updatedItems =
          session.createQuery("update SubAlarmDb set expression=:expression where sub_expression_id=:alarmSubExpressionId")
              .setString("expression", alarmSubExpression.getExpression()).setString("alarmSubExpressionId", alarmSubExpressionId).executeUpdate();
      tx.commit();
      return updatedItems;
    } catch (RuntimeException e) {
      try {
        tx.rollback();
      } catch (RuntimeException rbe) {
        logger.error("Couldn’t roll back transaction", rbe);
      }
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  @Override
  public void deleteByDefinitionId(String alarmDefinitionId) {
    Transaction tx = null;
    Session session = null;
    try {
      session = sessionFactory.openSession();
      tx = session.beginTransaction();

      session.createQuery("delete from AlarmDb where alarm_definition_id = :id").setString("id", alarmDefinitionId).executeUpdate();

      tx.commit();
    } catch (RuntimeException e) {
      try {
        tx.rollback();
      } catch (RuntimeException rbe) {
        logger.error("Couldn’t roll back transaction", rbe);
      }
      throw e;
    } finally {
      if (session != null) {
        session.close();
      }
    }

  }

  private void addQueryParameters(final Query query, String... params) {
    for (int i = 0; i < params.length;) {
      query.setString(params[i], params[i + 1]);
      i += 2;
    }
  }

  private List<Alarm> findAlarms(final String additionalWhereClause, String... params) {
    Session session = null;
    List<Alarm> alarms = new LinkedList<>();
    try {
      session = sessionFactory.openSession();

      final String ALARMS_SQL =
          "select a.id, a.alarm_definition_id, a.state, sa.id as sub_alarm_id, sa.expression, sa.sub_expression_id, ad.tenant_id from AlarmDb a, "
              + "SubAlarmDb sa, AlarmDefinitionDb ad where sa.alarm_id = a.id and a.alarm_definition_id = ad.id "
              + "and ad.deleted_at is null and %s order by a.id";
      final String sql = String.format(ALARMS_SQL, additionalWhereClause);

      Query qAlarmDefinition = session.createQuery(sql);

      addQueryParameters(qAlarmDefinition, params);
      final List<Object[]> rows = qAlarmDefinition.list();
      alarms = createAlarms(session, rows, additionalWhereClause, params);
      return alarms;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  private Map<String, List<MetricDefinition>> getAlarmedMetrics(List<Object[]> alarmList) {

    Map<String, List<MetricDefinition>> result = new HashMap<String, List<MetricDefinition>>();
    Map<UUID, List<MetricDefinition>> metricDefinitionList = new HashMap<UUID, List<MetricDefinition>>();
    Map<UUID, Map<String, Map<String, String>>> metricList = new HashMap<UUID, Map<String, Map<String, String>>>();
    Map<String, Set<UUID>> mapAssociationIds = new HashMap<String, Set<UUID>>();
    for (Object[] alarmRow : alarmList) {
      String alarmId = (String) alarmRow[0];
      String metric_name = (String) alarmRow[1];
      String dimension_name = (String) alarmRow[2];
      String dimension_value = (String) alarmRow[3];
      UUID dimensionSetId = (UUID) alarmRow[4];

      if (!metricList.containsKey(dimensionSetId)) {
        metricList.put(dimensionSetId, new HashMap<String, Map<String, String>>());
      }
      Map<String, Map<String, String>> dimensions = metricList.get(dimensionSetId);
      if (!dimensions.containsKey(metric_name)) {
        dimensions.put(metric_name, new HashMap<String, String>());
      }
      if (!mapAssociationIds.containsKey(alarmId)) {
        mapAssociationIds.put(alarmId, new HashSet<UUID>());
      }
      mapAssociationIds.get(alarmId).add(dimensionSetId);
      dimensions.get(metric_name).put(dimension_name, dimension_value);
    }

    for (UUID keyDimensionSetId : metricList.keySet()) {
      List<MetricDefinition> valueList = new ArrayList<MetricDefinition>();
      Map<String, Map<String, String>> metrics = metricList.get(keyDimensionSetId);
      for (String keyMetricName : metrics.keySet()) {
        MetricDefinition md = new MetricDefinition(keyMetricName, metrics.get(keyMetricName));
        valueList.add(md);
      }
      metricDefinitionList.put(keyDimensionSetId, valueList);
    }

    for (String keyAlarmId : mapAssociationIds.keySet()) {
      if (!result.containsKey(keyAlarmId)) {
        result.put(keyAlarmId, new LinkedList<MetricDefinition>());
      }
      Set<UUID> setDimensionId = mapAssociationIds.get(keyAlarmId);
      for (UUID keyDimensionId : setDimensionId) {
        List<MetricDefinition> metricDefList = metricDefinitionList.get(keyDimensionId);
        result.get(keyAlarmId).addAll(metricDefList);

      }
    }

    return result;
  }

  private List<Alarm> createAlarms(Session session, List<Object[]> alarmList, final String additionalWhereClause, String... params) {
    final List<Alarm> alarms = new ArrayList<Alarm>();
    List<SubAlarm> subAlarms = new ArrayList<SubAlarm>();
    String prevAlarmId = null;
    Alarm alarm = null;
    final Map<String, Alarm> alarmMap = new HashMap<>();
    final Map<String, String> tenantIdMap = new HashMap<>();
    for (Object[] alarmRow : alarmList) {
      final String alarmId = (String) alarmRow[ALARM_ID];
      if (!alarmId.equals(prevAlarmId)) {
        if (alarm != null) {
          alarm.setSubAlarms(subAlarms);
        }
        alarm = new Alarm();
        alarm.setId(alarmId);
        alarm.setAlarmDefinitionId((String) alarmRow[ALARM_DEFINITION_ID]);
        alarm.setState((AlarmState) alarmRow[ALARM_STATE]);
        subAlarms = new ArrayList<SubAlarm>();
        alarms.add(alarm);
        alarmMap.put(alarmId, alarm);
        tenantIdMap.put(alarmId, (String) alarmRow[TENANT_ID]);
      }
      final SubExpression subExpression =
          new SubExpression((String) alarmRow[SUB_EXPRESSION_ID], AlarmSubExpression.of((String) alarmRow[ALARM_EXPRESSION]));
      final SubAlarm subAlarm = new SubAlarm((String) alarmRow[SUB_ALARM_ID], alarmId, subExpression);
      subAlarms.add(subAlarm);
      prevAlarmId = alarmId;
    }

    if (alarm != null) {
      alarm.setSubAlarms(subAlarms);
    }

    if (!alarms.isEmpty()) {
      getAlarmedMetrics(session, alarmMap, tenantIdMap, additionalWhereClause, params);
    }

    return alarms;
  }

  private void getAlarmedMetrics(Session session, final Map<String, Alarm> alarmMap, final Map<String, String> tenantIdMap,
      final String additionalWhereClause, String... params) {

    final String baseSql =
        "select a.id, md.name as metric_def_name, mdg.id.name, mdg.id.value, mdg.id.dimension_set_id from MetricDefinitionDb as md, "
            + "MetricDefinitionDimensionsDb as mdd, " + "AlarmMetricDb as am, " + "AlarmDb as a, "
            + "MetricDimensionDb as mdg where md.id = mdd.metric_definition_id and mdd.id = am.alarmMetricId.metric_definition_dimensions_id and "
            + "am.alarmMetricId.alarm_id = a.id and mdg.id.dimension_set_id = mdd.metric_dimension_set_id and %s";
    final HashSet<String> existingAlarmId = new HashSet<String>();
    final String sql = String.format(baseSql, additionalWhereClause);
    final Query query = session.createQuery(sql);
    addQueryParameters(query, params);
    final List<Object[]> metricRows = query.list();
    Map<String, List<MetricDefinition>> alarmMetrics = getAlarmedMetrics(metricRows);

    for (final Object[] row : metricRows) {
      final String alarmId = (String) row[ALARM_ID];
      final Alarm alarm = alarmMap.get(alarmId);
      // This shouldn't happen but it is possible an Alarm gets created after the AlarmDefinition is
      // marked deleted and any existing alarms are deleted but before the Threshold Engine gets the
      // AlarmDefinitionDeleted message
      if (alarm == null) {
        continue;
      }
      if (!existingAlarmId.contains(alarmId)) {
        List<MetricDefinition> mdList = alarmMetrics.get(alarmId);
        for (MetricDefinition md : mdList) {
          alarm.addAlarmedMetric(new MetricDefinitionAndTenantId(md, tenantIdMap.get(alarmId)));
        }

      }
      existingAlarmId.add(alarmId);
    }
  }

  private void createAlarmedMetric(Session session, MetricDefinitionAndTenantId metricDefinition, String alarmId) {
    final Sha1HashId metricDefinitionDimensionId = insertMetricDefinitionDimension(session, metricDefinition);

    AlarmMetricDb alarmMetric = new AlarmMetricDb(alarmId, metricDefinitionDimensionId.getSha1Hash());
    session.save(alarmMetric);

  }

  private Sha1HashId insertMetricDefinitionDimension(Session session, MetricDefinitionAndTenantId mdtid) {
    final Sha1HashId metricDefinitionId = insertMetricDefinition(session, mdtid);
    final Sha1HashId metricDimensionSetId = insertMetricDimensionSet(session, mdtid.metricDefinition.dimensions);
    final byte[] definitionDimensionsIdSha1Hash = DigestUtils.sha(metricDefinitionId.toHexString() + metricDimensionSetId.toHexString());
    MetricDefinitionDimensionsDb metricDefinitionDimensions =
        new MetricDefinitionDimensionsDb(definitionDimensionsIdSha1Hash, metricDefinitionId.getSha1Hash(), metricDimensionSetId.getSha1Hash());

    session.saveOrUpdate(metricDefinitionDimensions);

    return new Sha1HashId(definitionDimensionsIdSha1Hash);
  }

  private Sha1HashId insertMetricDimensionSet(Session session, Map<String, String> dimensions) {
    final byte[] dimensionSetId = calculateDimensionSHA1(dimensions);
    for (final Map.Entry<String, String> entry : dimensions.entrySet()) {
      MetricDimensionDb metricDimension = new MetricDimensionDb(dimensionSetId, entry.getKey(), entry.getValue());

      if (session.get(MetricDimensionDb.class, metricDimension.getId()) == null) {
        session.saveOrUpdate(metricDimension);
      }

    }
    return new Sha1HashId(dimensionSetId);
  }

  private Sha1HashId insertMetricDefinition(Session session, MetricDefinitionAndTenantId mdtid) {
    final String region = ""; // TODO We currently don't have region
    final String definitionIdStringToHash =
        trunc(mdtid.metricDefinition.name, MAX_COLUMN_LENGTH) + trunc(mdtid.tenantId, MAX_COLUMN_LENGTH) + trunc(region, MAX_COLUMN_LENGTH);
    final byte[] id = DigestUtils.sha(definitionIdStringToHash);
    MetricDefinitionDb metricDefinition = new MetricDefinitionDb(id, mdtid.metricDefinition.name, mdtid.tenantId, region);
    if (session.get(MetricDefinitionDb.class, metricDefinition.getPkey()) == null) {
      session.saveOrUpdate(metricDefinition);
    }
    return new Sha1HashId(id);
  }

  private String trunc(String s, int l) {

    if (s == null) {
      return "";
    } else if (s.length() <= l) {
      return s;
    } else {
      String r = s.substring(0, l);
      logger.warn("Input string exceeded max column length. Truncating input string {} to {} chars", s, l);
      logger.warn("Resulting string {}", r);
      return r;
    }
  }

  private byte[] calculateDimensionSHA1(final Map<String, String> dimensions) {
    // Calculate dimensions sha1 hash id.
    final StringBuilder dimensionIdStringToHash = new StringBuilder("");
    if (dimensions != null) {
      // Sort the dimensions on name and value.
      TreeMap<String, String> dimensionTreeMap = new TreeMap<>(dimensions);
      for (String dimensionName : dimensionTreeMap.keySet()) {
        if (dimensionName != null && !dimensionName.isEmpty()) {
          String dimensionValue = dimensionTreeMap.get(dimensionName);
          if (dimensionValue != null && !dimensionValue.isEmpty()) {
            dimensionIdStringToHash.append(trunc(dimensionName, MAX_COLUMN_LENGTH));
            dimensionIdStringToHash.append(trunc(dimensionValue, MAX_COLUMN_LENGTH));
          }
        }
      }
    }

    final byte[] dimensionIdSha1Hash = DigestUtils.sha(dimensionIdStringToHash.toString());
    return dimensionIdSha1Hash;
  }
}
