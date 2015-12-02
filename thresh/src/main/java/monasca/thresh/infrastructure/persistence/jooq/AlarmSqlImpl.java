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

package monasca.thresh.infrastructure.persistence.jooq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.jooq.Batch;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.Converter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Param;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import monasca.common.jooq.Tables;
import monasca.common.model.alarm.AlarmState;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;
import monasca.thresh.domain.model.Alarm;
import monasca.thresh.domain.model.MetricDefinitionAndTenantId;
import monasca.thresh.domain.model.SubAlarm;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDAO;


/**
 * AlarmDAO jooq implementation.
 *
 * @author lukasz.zajaczkowski@ts.fujitsu.com
 * @author tomasz.trebski@ts.fujitsu.com
 */
public class AlarmSqlImpl
    implements AlarmDAO {
  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmSqlImpl.class);
  private static final int ALARM_ID = 0;
  private static final int ALARM_DEFINITION_ID = 1;
  private static final int ALARM_STATE = 2;
  private static final int SUB_ALARM_ID = 3;
  private static final int ALARM_EXPRESSION = 4;
  private static final int SUB_EXPRESSION_ID = 5;
  private static final int TENANT_ID = 6;
  private static final int MAX_COLUMN_LENGTH = 255;
  private final DataSource ds;
  private final SQLDialect dialect;
  private final Settings settings;
  private final monasca.common.jooq.tables.Alarm at;
  private final monasca.common.jooq.tables.Alarm ati;
  private final monasca.common.jooq.tables.SubAlarm sa;
  private final monasca.common.jooq.tables.SubAlarm sai;
  private final monasca.common.jooq.tables.AlarmDefinition ad;
  private final monasca.common.jooq.tables.MetricDefinition md;
  private final monasca.common.jooq.tables.MetricDefinition mdi;
  private final monasca.common.jooq.tables.MetricDefinitionDimensions mdd;
  private final monasca.common.jooq.tables.MetricDefinitionDimensions mddi;
  private final monasca.common.jooq.tables.AlarmMetric am;
  private final monasca.common.jooq.tables.AlarmMetric ami;
  private final monasca.common.jooq.tables.MetricDimension mdim;
  private final monasca.common.jooq.tables.MetricDimension mdimi;
  private final org.jooq.Field<String> fieldDimensions;

  /**
   * Constructor.
   *
   * @param ds - datasource
   * @param dialect - database dialect
   */
  @Inject
  public AlarmSqlImpl(@Named("datasource") DataSource ds,
                      @Named("dialect") SQLDialect dialect) {
    this.dialect = dialect;
    this.ds = ds;
    this.settings = new Settings().withRenderSchema(false);
    this.at = Tables.ALARM.as("a");
    this.ati = Tables.ALARM;
    this.sa = Tables.SUB_ALARM.as("sa");
    this.sai = Tables.SUB_ALARM;
    this.ad = Tables.ALARM_DEFINITION.as("ad");
    this.md = Tables.METRIC_DEFINITION.as("md");
    this.mdi = Tables.METRIC_DEFINITION;
    this.mdd = Tables.METRIC_DEFINITION_DIMENSIONS.as("mdd");
    this.mddi = Tables.METRIC_DEFINITION_DIMENSIONS;
    this.am = Tables.ALARM_METRIC.as("am");
    this.ami = Tables.ALARM_METRIC;
    this.mdim = Tables.METRIC_DIMENSION.as("mdim");
    this.mdimi = Tables.METRIC_DIMENSION;
    this.fieldDimensions = DSL.listAgg(DSL.concat(mdim.NAME,
                                                  DSL.val("="),
                                                  mdim.VALUE),
                                       ",")
      .withinGroupOrderBy(mdim.NAME.asc())
      .as("dimensions");
  }

  @Override
  public List<Alarm> findForAlarmDefinitionId(String alarmDefinitionId) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    SelectConditionStep baseQuery = createFindAlarmsBaseQuery(context);
    Select query = baseQuery
        .and(at.ALARM_DEFINITION_ID.equal(DSL.param("alarmDefinitionId", String.class)))
        .orderBy(at.ID.asc());
    query.bind("alarmDefinitionId", alarmDefinitionId);
    Result<Record> rows = query.fetch();
    List<Alarm> listAlarms = new ArrayList<>(rows.size());
    final Map<String, Alarm> alarmMap = new HashMap<>();
    final Map<String, String> tenantIdMap = new HashMap<>();

    fillAlarms(rows, listAlarms, alarmMap, tenantIdMap);
    if (!listAlarms.isEmpty()) {
      SelectWhereStep baseQueryMetrics = createAlarmedMetricsBaseQuery(context);
      Select queryMetrics = baseQueryMetrics
          .where(at.ALARM_DEFINITION_ID.equal(DSL.param("alarmDefinitionId", String.class)))
          .groupBy(at.ID, md.NAME, mdim.DIMENSION_SET_ID)
          .orderBy(fieldDimensions);
      queryMetrics.bind("alarmDefinitionId", alarmDefinitionId);
      Result<Record> metricRows = queryMetrics.fetch();
      fillAlarmsWithMetrics(metricRows, alarmMap, tenantIdMap);
    }
    return listAlarms;
  }

  @Override
  public List<Alarm> listAll() {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    SelectConditionStep baseQuery = createFindAlarmsBaseQuery(context);
    Select query = baseQuery
        .orderBy(at.ID.asc());
    Result<Record> rows = query.fetch();
    List<Alarm> listAlarms = new ArrayList<>(rows.size());
    final Map<String, Alarm> alarmMap = new HashMap<>();
    final Map<String, String> tenantIdMap = new HashMap<>();

    fillAlarms(rows, listAlarms, alarmMap, tenantIdMap);
    if (!listAlarms.isEmpty()) {
      SelectWhereStep baseQueryMetrics = createAlarmedMetricsBaseQuery(context);
      Select queryMetrics = baseQueryMetrics
          .groupBy(at.ID, md.NAME, mdim.DIMENSION_SET_ID)
          .orderBy(fieldDimensions);
      Result<Record> metricRows = queryMetrics.fetch();
      fillAlarmsWithMetrics(metricRows, alarmMap, tenantIdMap);
    }
    return listAlarms;
  }

  private void fillAlarms(Result<Record> rows,
                          List<Alarm> alarms,
                          final Map<String, Alarm> alarmMap,
                          final Map<String, String> tenantIdMap) {

    List<SubAlarm> subAlarms = new ArrayList<SubAlarm>();
    String prevAlarmId = null;
    Alarm alarm = null;
    for (Record row : rows) {
      final String alarmId = (String)(row.getValue("id"));
      if (!alarmId.equals(prevAlarmId)) {
        if (alarm != null) {
          alarm.setSubAlarms(subAlarms);
        }
        alarm = new Alarm();
        alarm.setId(alarmId);
        alarm.setAlarmDefinitionId((String)(row.getValue("alarm_definition_id")));
        alarm.setState(AlarmState.valueOf((String)(row.getValue("state"))));
        subAlarms = new ArrayList<SubAlarm>();
        alarms.add(alarm);
        alarmMap.put(alarmId, alarm);
        tenantIdMap.put(alarmId, (String)(row.getValue("tenant_id")));
      }
      final SubExpression subExpression =
          new SubExpression((String)(row.getValue("sub_expression_id")),
                            AlarmSubExpression.of((String)(row.getValue("expression"))));
      final SubAlarm subAlarm =
          new SubAlarm((String)(row.getValue("sub_alarm_id")),
                       alarmId,
                       subExpression);
      subAlarms.add(subAlarm);
      prevAlarmId = alarmId;
    }
    if (alarm != null) {
      alarm.setSubAlarms(subAlarms);
    }
  }


  private SelectConditionStep createFindAlarmsBaseQuery(DSLContext context) {

    return context.selectDistinct(at.ID,
                                  at.ALARM_DEFINITION_ID,
                                  at.STATE,
                                  sa.ID.as("sub_alarm_id"),
                                  sa.EXPRESSION,
                                  sa.SUB_EXPRESSION_ID,
                                  ad.TENANT_ID)
      .from(at)
      .join(sa).on(sa.ALARM_ID.equal(at.ID))
      .join(ad).on(at.ALARM_DEFINITION_ID.equal(ad.ID))
      .where(ad.DELETED_AT.isNull());
  }

  private void fillAlarmsWithMetrics(Result<Record> metricRows,
                                     final Map<String, Alarm> alarmMap,
                                     final Map<String, String> tenantIdMap) {
    for (Record row : metricRows) {
      final String alarmId = (String)row.getValue("id");
      final Alarm alarm = alarmMap.get(alarmId);
      // This shouldn't happen but it is possible an Alarm gets created after the AlarmDefinition is
      // marked deleted and any existing alarms are deleted but before the Threshold Engine gets the
      // AlarmDefinitionDeleted message
      if (alarm == null) {
        continue;
      }
      final MetricDefinition md = createMetricDefinitionFromRow(row);
      alarm.addAlarmedMetric(new MetricDefinitionAndTenantId(md, tenantIdMap.get(alarmId)));
    }
  }

  private SelectWhereStep createAlarmedMetricsBaseQuery(DSLContext context) {
    return context.selectDistinct(at.ID,
                                  md.NAME,
                                  fieldDimensions)
        .from(md)
        .join(mdd).on(md.ID.equal(mdd.METRIC_DEFINITION_ID))
        .join(am).on(mdd.ID.equal(am.METRIC_DEFINITION_DIMENSIONS_ID))
        .join(at).on(am.ALARM_ID.equal(at.ID))
        .leftOuterJoin(mdim).on(mdim.DIMENSION_SET_ID.equal(mdd.METRIC_DIMENSION_SET_ID));
  }

  /**
   * addAlarmedmetric - function for save metric.
   *
   * @param alarmId - id of alarm
   * @param metricDefinition - definition of metric to save
   */
  @Override
  public void addAlarmedMetric(final String alarmId,
                               final MetricDefinitionAndTenantId metricDefinition) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);

    context.transaction(new TransactionalRunnable() {
        @Override
        public void run(Configuration configuration) throws Exception {
          DSLContext localContext = DSL.using(configuration);

          createAlarmedMetric(localContext, metricDefinition, alarmId);
        }
      });
  }

  private void createAlarmedMetric(DSLContext context, MetricDefinitionAndTenantId metricDefinition,
      String alarmId) {
    final Sha1HashId metricDefinitionDimensionId =
        insertMetricDefinitionDimension(context, metricDefinition);

    context.insertInto(ami, ami.ALARM_ID, ami.METRIC_DEFINITION_DIMENSIONS_ID)
      .values(null, (org.jooq.Field)null)
      .bind(1, alarmId)
      .bind(2, metricDefinitionDimensionId.getSha1Hash())
      .execute();
  }

  private Sha1HashId insertMetricDefinitionDimension(DSLContext context,
                                                     MetricDefinitionAndTenantId mdtid) {
    final Sha1HashId metricDefinitionId = insertMetricDefinition(context, mdtid);
    final Sha1HashId metricDimensionSetId =
        insertMetricDimensionSet(context, mdtid.metricDefinition.dimensions);
    final byte[] definitionDimensionsIdSha1Hash =
        DigestUtils.sha(metricDefinitionId.toHexString() + metricDimensionSetId.toHexString());

    Param<byte[]> mddId = DSL.param("mddId", byte[].class);
    context.insertInto(mddi, mddi.ID, mddi.METRIC_DEFINITION_ID, mddi.METRIC_DIMENSION_SET_ID)
     .select(
              DSL.select(
                         mddId,
                         DSL.param("mddDefinitionId", mdd.METRIC_DEFINITION_ID.getType()),
                         DSL.param("mddDimensionSetId", mdd.METRIC_DIMENSION_SET_ID.getType())
                         )
              .whereNotExists(
                              DSL.selectOne()
                              .from(mdd)
                              .where(mdd.ID.equal(mddId))
                              )
              )
      .bind("mddId", definitionDimensionsIdSha1Hash)
      .bind("mddDefinitionId", metricDefinitionId.getSha1Hash())
      .bind("mddDimensionSetId", metricDimensionSetId.getSha1Hash())
      .execute();

    return new Sha1HashId(definitionDimensionsIdSha1Hash);
  }

  private Sha1HashId insertMetricDimensionSet(DSLContext context, Map<String, String> dimensions) {
    final byte[] dimensionSetId = calculateDimensionSHA1(dimensions);
    Param<byte[]> mdimId = DSL.param("mdimId", byte[].class);
    Param<String> mdimName = DSL.param("mdimName", String.class);
    for (final Map.Entry<String, String> entry : dimensions.entrySet()) {
      context.insertInto(mdimi, mdimi.DIMENSION_SET_ID, mdimi.NAME, mdimi.VALUE)
        .select(
                DSL.select(
                           mdimId,
                           mdimName,
                           DSL.param("mdimValue", mdim.VALUE.getType())
                           )
                .whereNotExists(
                                DSL.selectOne()
                                .from(mdim)
                                .where(mdim.DIMENSION_SET_ID.equal(mdimId))
                                .and(mdim.NAME.equal(mdimName))
                                )
                )
        .bind("mdimId", dimensionSetId)
        .bind("mdimName", entry.getKey())
        .bind("mdimValue", entry.getValue())
        .execute();
    }

    return new Sha1HashId(dimensionSetId);
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

  private Sha1HashId insertMetricDefinition(DSLContext context, MetricDefinitionAndTenantId mdtid) {
    final String region = ""; // TODO We currently don't have region
    final String definitionIdStringToHash =
        trunc(mdtid.metricDefinition.name, MAX_COLUMN_LENGTH)
            + trunc(mdtid.tenantId, MAX_COLUMN_LENGTH) + trunc(region, MAX_COLUMN_LENGTH);
    final byte[] id = DigestUtils.sha(definitionIdStringToHash);

    Param<byte[]> mdId = DSL.param("mdId", byte[].class);
    context.insertInto(mdi, mdi.ID, mdi.NAME, mdi.TENANT_ID)
      .select(
              DSL.select(
                         mdId,
                         DSL.param("mdName", md.NAME.getType()),
                         DSL.param("mdTenantId", md.TENANT_ID.getType())
                         )
              .whereNotExists(
                              DSL.selectOne()
                              .from(md)
                              .where(md.ID.equal(mdId))
                              )
              )
      .bind("mdId", id)
      .bind("mdName", mdtid.metricDefinition.name)
      .bind("mdTenantId", mdtid.tenantId)
      .execute();

    return new Sha1HashId(id);
  }

  @Override
  public void createAlarm(final Alarm alarm) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    context.transaction(new TransactionalRunnable() {
        @Override
        public void run(Configuration configuration) throws Exception {
          DSLContext localContext = DSL.using(configuration);

          localContext.insertInto(ati, ati.ID, ati.ALARM_DEFINITION_ID, ati.STATE,
                                  ati.STATE_UPDATED_AT, ati.CREATED_AT, ati.UPDATED_AT)
            .values(null, null, null,
                    DSL.currentTimestamp(), DSL.currentTimestamp(), DSL.currentTimestamp())
            .bind(1, alarm.getId())
            .bind(2, alarm.getAlarmDefinitionId())
            .bind(3, alarm.getState().toString())
            .execute();

          for (final SubAlarm subAlarm : alarm.getSubAlarms()) {
            localContext.insertInto(sai, sai.ID, sai.ALARM_ID, sai.SUB_EXPRESSION_ID,
                                    sai.EXPRESSION, sai.CREATED_AT, sai.UPDATED_AT)
              .values(null, null, null, null,
                      DSL.currentTimestamp(), DSL.currentTimestamp())
              .bind(1, subAlarm.getId())
              .bind(2, subAlarm.getAlarmId())
              .bind(3, subAlarm.getAlarmSubExpressionId())
              .bind(4, subAlarm.getExpression().getExpression())
              .execute();
          }
          for (final MetricDefinitionAndTenantId md : alarm.getAlarmedMetrics()) {
            createAlarmedMetric(localContext, md, alarm.getId());
          }
        }
      });
  }

  @Override
  public Alarm findById(String id) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    SelectConditionStep baseQuery = createFindAlarmsBaseQuery(context);
    Select query = baseQuery
        .and(at.ID.equal(DSL.param("alarm_id", String.class)))
        .orderBy(at.ID.asc());
    query.bind("alarm_id", id);
    Result<Record> rows = query.fetch();
    List<Alarm> listAlarms = new ArrayList<>(rows.size());
    final Map<String, Alarm> alarmMap = new HashMap<>();
    final Map<String, String> tenantIdMap = new HashMap<>();

    fillAlarms(rows, listAlarms, alarmMap, tenantIdMap);
    if (!listAlarms.isEmpty()) {
      SelectWhereStep baseQueryMetrics = createAlarmedMetricsBaseQuery(context);
      Select queryMetrics = baseQueryMetrics
          .where(at.ID.equal(DSL.param("alarm_id", String.class)))
          .groupBy(at.ID, md.NAME, mdim.DIMENSION_SET_ID)
          .orderBy(fieldDimensions);
      queryMetrics.bind("alarm_id", id);
      Result<Record> metricRows = queryMetrics.fetch();
      fillAlarmsWithMetrics(metricRows, alarmMap, tenantIdMap);
    }

    if (listAlarms.isEmpty()) {
      return null;
    } else {
      return listAlarms.get(0);
    }
  }

  @Override
  public void updateState(String id, AlarmState state) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    context.update(at)
      .set(at.STATE, DSL.param("state", String.class))
      .set(at.STATE_UPDATED_AT, DSL.currentTimestamp())
      .set(at.UPDATED_AT, DSL.currentTimestamp())
      .where(at.ID.equal(DSL.param("id", String.class)))
      .bind("id", id)
      .bind("state", state.toString())
      .execute();
  }

  @Override
  public int updateSubAlarmExpressions(String alarmSubExpressionId,
      AlarmSubExpression alarmSubExpression) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    return context.update(sa)
      .set(sa.EXPRESSION, DSL.param("expression", String.class))
      .where(sa.SUB_EXPRESSION_ID.equal(DSL.param("alarmSubExpressionId", String.class)))
      .bind("expression", alarmSubExpression.getExpression())
      .bind("alarmSubExpressionId", alarmSubExpressionId)
      .execute();
  }

  /**
   * deleteByDefinitionId - remove alarm for alarmdefinition.
   *
   * @param alarmDefinitionId - id of definition
   */
  @Override
  public void deleteByDefinitionId(String alarmDefinitionId) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    context.delete(at)
      .where(at.ALARM_DEFINITION_ID.equal(DSL.param("id", String.class)))
      .bind("id", alarmDefinitionId)
      .execute();
  }

  private MetricDefinition createMetricDefinitionFromRow(final Record row) {
    final Map<String, String> dimensionMap = new HashMap<>();
    final String dimensions = (String)row.getValue("dimensions");
    if (dimensions != null) {
      for (String dimension : dimensions.split(",")) {
        final String[] parsed_dimension = dimension.split("=");
        if (parsed_dimension.length > 1) {
          dimensionMap.put(parsed_dimension[0], parsed_dimension[1]);
        }
      }
    }
    final MetricDefinition md = new MetricDefinition((String)row.getValue("name"), dimensionMap);
    return md;
  }

  private String trunc(String s, int l) {

    if (s == null) {
      return "";
    } else if (s.length() <= l) {
      return s;
    } else {
      String r = s.substring(0, l);
      LOGGER.warn(
          "Input string exceeded max column length. Truncating input string {} to {} chars", s, l);
      LOGGER.warn("Resulting string {}", r);
      return r;
    }
  }

  /**
   * This class is used when a binary id needs to be used in a map. Just using a byte[] as
   * a key fails because they are not considered as equal because the check is ==
   * @author craigbr
   *
   */

  private static class Sha1HashId {
    private final byte[] sha1Hash;

    public Sha1HashId(byte[] sha1Hash) {
      this.sha1Hash = sha1Hash;
    }

    @Override
    public String toString() {
      return "Sha1HashId{" + "sha1Hash=" + Hex.encodeHexString(sha1Hash) + "}";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof Sha1HashId))
        return false;

      Sha1HashId that = (Sha1HashId) o;

      if (!Arrays.equals(sha1Hash, that.sha1Hash))
        return false;

      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(sha1Hash);
    }

    public byte[] getSha1Hash() {
      return sha1Hash;
    }

    public String toHexString() {
      return Hex.encodeHexString(sha1Hash);
    }
  }
}
