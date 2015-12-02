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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import monasca.common.jooq.Tables;
import monasca.common.model.alarm.AggregateFunction;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmOperator;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;
import monasca.common.util.Conversions;
import monasca.thresh.domain.model.AlarmDefinition;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDefinitionDAO;

/**
 * AlarmDefinitionDAO jooq implementation.
 *
 * @author lukasz.zajaczkowski@ts.fujitsu.com
 */
public class AlarmDefinitionSqlImpl
    implements AlarmDefinitionDAO {
  private static final Logger LOGGER = LoggerFactory.getLogger(AlarmDefinitionSqlImpl.class);
  private final DataSource ds;
  private final SQLDialect dialect;
  private final Settings settings;

  /**
   * Constructor.
   *
   * @param ds - Datasource
   * @param dialect - database dialect
   */
  @Inject
  public AlarmDefinitionSqlImpl(@Named("datasource") DataSource ds,
                                @Named("dialect") SQLDialect dialect) {
    this.dialect = dialect;
    this.ds = ds;
    this.settings = new Settings().withRenderSchema(false);
  }

  @Override
  public List<AlarmDefinition> listAll() {
    DSLContext create = DSL.using(this.ds, this.dialect, this.settings);

    monasca.common.jooq.tables.AlarmDefinition ad = Tables.ALARM_DEFINITION.as("ad");
    List<AlarmDefinition> alarmDefs = create.selectDistinct(ad.ID,
                                                            ad.TENANT_ID,
                                                            ad.NAME,
                                                            ad.DESCRIPTION,
                                                            ad.EXPRESSION,
                                                            ad.SEVERITY,
                                                            ad.MATCH_BY,
                                                            ad.ACTIONS_ENABLED,
                                                            ad.CREATED_AT,
                                                            ad.UPDATED_AT,
                                                            ad.DELETED_AT)
        .from(ad)
        .where(ad.DELETED_AT.isNull())
        .orderBy(ad.CREATED_AT.asc())
        .fetch().map(new AlarmDefinitionMapper());

    for (final AlarmDefinition alarmDef : alarmDefs) {
      alarmDef.setSubExpressions(findSubExpressions(create, alarmDef.getId()));
    }
    return alarmDefs;
  }

  private List<SubExpression> findSubExpressions(DSLContext context, String alarmDefId) {
    monasca.common.jooq.tables.SubAlarmDefinition sad = Tables.SUB_ALARM_DEFINITION.as("sad");
    monasca.common.jooq.tables.SubAlarmDefinitionDimension sadd =
        Tables.SUB_ALARM_DEFINITION_DIMENSION.as("sadd");
    Select qq = context.select(sad.fields())
        .select(sadd.fields())
        .from(sad)
        .leftOuterJoin(sadd)
        .on(sad.ID.equal(sadd.SUB_ALARM_DEFINITION_ID))
        .where(sad.ALARM_DEFINITION_ID.equal(DSL.param("alarmDefId", String.class)))
        .orderBy(sad.ID.asc());

    qq.bind("alarmDefId", alarmDefId);

    Result<Record> rows = qq.fetch();

    final List<SubExpression> subExpressions = new ArrayList<>(rows.size());
    int index = 0;
    while (index < rows.size()) {
      String id = (String) rows.getValue(index, "id");
      AggregateFunction function = AggregateFunction
          .fromJson((String) rows.getValue(index, "function"));
      String metricName = (String) rows.getValue(index, "metric_name");
      AlarmOperator operator = AlarmOperator.fromJson((String) rows.getValue(index, "operator"));
      Double threshold = (Double) rows.getValue(index, "threshold");
      Integer period = Conversions.variantToInteger(rows.getValue(index, "period"));
      Integer periods = Conversions.variantToInteger(rows.getValue(index, "periods"));
      Map<String, String> dimensions = new HashMap<>();
      while (addedDimension(dimensions, id, rows, index)) {
        index++;
      }
      subExpressions.add(new SubExpression(id, new AlarmSubExpression(function,
          new MetricDefinition(metricName, dimensions), operator, threshold, period, periods)));
    }

    return subExpressions;
  }

  private boolean addedDimension(Map<String, String> dimensions, String id,
                                 Result rows, int index) {
    if (index >= rows.size()) {
      return false;
    }
    if (!rows.getValue(index, "id").equals(id)) {
      return false;
    }
    final String name = (String)rows.getValue(index, "dimension_name");
    final String value = (String)rows.getValue(index, "value");
    if ((name != null) && !name.isEmpty()) {
      dimensions.put(name, value);
    }
    return true;
  }

  @Override
  public AlarmDefinition findById(String id) {
    DSLContext context = DSL.using(this.ds, this.dialect, this.settings);
    monasca.common.jooq.tables.AlarmDefinition ad = Tables.ALARM_DEFINITION.as("ad");
    Record alarmDefinitionRecord = context.selectDistinct(ad.ID,
                                                          ad.TENANT_ID,
                                                          ad.NAME,
                                                          ad.DESCRIPTION,
                                                          ad.EXPRESSION,
                                                          ad.SEVERITY,
                                                          ad.ACTIONS_ENABLED,
                                                          ad.MATCH_BY,
                                                          ad.CREATED_AT)
        .from(ad)
        .where(ad.DELETED_AT.isNull())
        .and(ad.ID.equal(DSL.param("id", String.class)))
        .orderBy(ad.CREATED_AT.asc())
        .bind("id", id)
        .fetchAny();
    
    AlarmDefinition alarmDefinition = null;

    if (alarmDefinitionRecord != null) {
      alarmDefinition = alarmDefinitionRecord.map(new AlarmDefinitionMapper());
      alarmDefinition.setSubExpressions(findSubExpressions(context, alarmDefinition.getId()));
    }

    return alarmDefinition;
  }

  private static class AlarmDefinitionMapper implements RecordMapper<Record, AlarmDefinition> {

    private static final Splitter
        COMMA_SPLITTER =
        Splitter.on(',').omitEmptyStrings().trimResults();

    @Override
    public AlarmDefinition map(Record rec) {
      String matchBy = (String)(rec.getValue("match_by"));
      List<String> match = splitStringIntoList(matchBy);

      return new AlarmDefinition((String)(rec.getValue("id")),
                                 (String)(rec.getValue("tenant_id")),
                                 (String)(rec.getValue("name")),
                                 (String)(rec.getValue("description")),
                                 new AlarmExpression((String)(rec.getValue("expression"))),
                                 (String)(rec.getValue("severity")),
                                 (Boolean)(rec.getValue("actions_enabled")),
                                 null,
                                 match);
    }

    private List<String> splitStringIntoList(String commaDelimitedString) {
      if (commaDelimitedString == null) {
        return new ArrayList<String>();
      }
      Iterable<String> split = COMMA_SPLITTER.split(commaDelimitedString);
      return Lists.newArrayList(split);
    }
  }
}
