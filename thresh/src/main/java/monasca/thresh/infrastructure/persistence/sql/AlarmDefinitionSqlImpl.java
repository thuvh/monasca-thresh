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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;

import monasca.common.hibernate.db.AlarmDefinitionDb;
import monasca.common.hibernate.db.SubAlarmDefinitionDb;
import monasca.common.hibernate.db.SubAlarmDefinitionDimensionDb;
import monasca.common.model.alarm.AggregateFunction;
import monasca.common.model.alarm.AlarmExpression;
import monasca.common.model.alarm.AlarmOperator;
import monasca.common.model.alarm.AlarmSubExpression;
import monasca.common.model.metric.MetricDefinition;
import monasca.thresh.domain.model.Alarm;
import monasca.thresh.domain.model.AlarmDefinition;
import monasca.thresh.domain.model.SubExpression;
import monasca.thresh.domain.service.AlarmDefinitionDAO;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
/**
 * AlarmDefinitionDAO hibernate implementation.
 *
 * @author lukasz.zajaczkowski@ts.fujitsu.com
 */
public class AlarmDefinitionSqlImpl implements AlarmDefinitionDAO {

  private final SessionFactory sessionFactory;

  @Inject
  public AlarmDefinitionSqlImpl(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public List<AlarmDefinition> listAll() {
    Session session = null;
    final List<AlarmDefinition> alarmDefs = new ArrayList<AlarmDefinition>();
    try {
      session = sessionFactory.openSession();
      List<AlarmDefinitionDb> alarmDefDbList = session.createQuery("from AlarmDefinitionDb where deleted_at is NULL order by created_at").list();

      if (alarmDefDbList != null) {

        for(AlarmDefinitionDb alarmDefDb: alarmDefDbList) {
          final String matchByString = alarmDefDb.getMatch_by();
          final List<String> matchBy;
          if (matchByString == null || matchByString.isEmpty()) {
            matchBy = new ArrayList<>(0);
          } else {
            matchBy = new ArrayList<String>(Arrays.asList(matchByString.split(",")));
          }
          boolean actionEnable = alarmDefDb.isActions_enabled() == 1 ? true : false;

          AlarmDefinition alarmDefinition = new AlarmDefinition(alarmDefDb.getId(), alarmDefDb.getTenant_id(), alarmDefDb.getName(), alarmDefDb.getDescription(),
              new AlarmExpression(alarmDefDb.getExpression()), alarmDefDb.getSeverity().name(), actionEnable, null, matchBy);

          alarmDefinition.setSubExpressions(findSubExpressions(session, alarmDefinition.getId()));
          alarmDefs.add(alarmDefinition);
        }

      }
      return alarmDefs;
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  @Override
  public AlarmDefinition findById(String id) {
    Session session = null;
    AlarmDefinition alarmDefinition = null;
    List<Alarm> alarms = new LinkedList<>();
    try {
      session = sessionFactory.openSession();
      AlarmDefinitionDb alarmDefDb = (AlarmDefinitionDb) session.get(AlarmDefinitionDb.class, id);

      if (alarmDefDb != null) {

        final String matchByString = alarmDefDb.getMatch_by();
        final List<String> matchBy;
        if (matchByString == null || matchByString.isEmpty()) {
          matchBy = new ArrayList<>(0);
        } else {
          matchBy = new ArrayList<String>(Arrays.asList(matchByString.split(",")));
        }
        boolean actionEnable = alarmDefDb.isActions_enabled() == 1 ? true : false;

        alarmDefinition =
            new AlarmDefinition(alarmDefDb.getId(), alarmDefDb.getTenant_id(), alarmDefDb.getName(), alarmDefDb.getDescription(),
                new AlarmExpression(alarmDefDb.getExpression()), alarmDefDb.getSeverity().name(), actionEnable, null, matchBy);

        alarmDefinition.setSubExpressions(findSubExpressions(session, alarmDefinition.getId()));
      }

      return alarmDefinition;

    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  private List<SubExpression> findSubExpressions(Session session, String alarmDefId) {
    final List<SubExpression> subExpressions = new ArrayList<>();
    Map<String, Map<String, String>> dimensionMap = new HashMap<String, Map<String, String>>();
    DetachedCriteria subAlarmDefinitionDbDetachedCriteria = DetachedCriteria.forClass(SubAlarmDefinitionDb.class);
    subAlarmDefinitionDbDetachedCriteria.setProjection(Property.forName("id"));
    subAlarmDefinitionDbDetachedCriteria.add(Restrictions.eq("alarm_definition_id", alarmDefId));
    subAlarmDefinitionDbDetachedCriteria.addOrder(Order.asc("id"));

    Criteria subAlarmDefinitionDimensionDbcriteria = session.createCriteria(SubAlarmDefinitionDimensionDb.class);
    List<SubAlarmDefinitionDimensionDb> joinedSubAlarmDefinitionDimensionDb =
        subAlarmDefinitionDimensionDbcriteria.add(
            Property.forName("subAlarmDefinitionDimensionId.sub_alarm_definition_id").in(subAlarmDefinitionDbDetachedCriteria)).list();

    List<SubAlarmDefinitionDb> subAlarmDefinitionDbList =
        session.createQuery("from SubAlarmDefinitionDb where alarm_definition_id = :alarm_definition_id order by id")
            .setString("alarm_definition_id", alarmDefId).list();

    for (SubAlarmDefinitionDimensionDb defDimension : joinedSubAlarmDefinitionDimensionDb) {
      final String subAlarmId = defDimension.getSubAlarmDefinitionDimensionId().getSub_alarm_definition_id();
      final String name = defDimension.getSubAlarmDefinitionDimensionId().getDimension_name();
      final String value = defDimension.getValue();
      if (!dimensionMap.containsKey(subAlarmId)) {
        dimensionMap.put(subAlarmId, new TreeMap<String, String>());
      }
      dimensionMap.get(subAlarmId).put(name, value);
    }

    for (SubAlarmDefinitionDb subAlarmDef : subAlarmDefinitionDbList) {
      final String id = subAlarmDef.getId();
      final AggregateFunction function = AggregateFunction.fromJson(subAlarmDef.getFunction());
      final String metricName = subAlarmDef.getMetric_name();
      final AlarmOperator operator = AlarmOperator.fromJson(subAlarmDef.getOperator());
      final Double threshold = subAlarmDef.getThreshold();
      final Integer period = subAlarmDef.getPeriod();
      final Integer periods = subAlarmDef.getPeriods();
      Map<String, String> dimensions = dimensionMap.get(id);
      if(dimensions == null) {
        dimensions = new TreeMap<String, String>();
      }
      subExpressions.add(new SubExpression(id, new AlarmSubExpression(function, new MetricDefinition(metricName, dimensions), operator, threshold,
          period, periods)));
    }
    return subExpressions;
  }
}
