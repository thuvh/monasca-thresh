/*
 * Enum tables
 */
CREATE TABLE `alarm_state` (
  `name` varchar(20) NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `alarm_definition_severity` (
  `name` varchar(20) NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `notification_method_type` (
  `name` varchar(20) NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `stream_actions_action_type` (
  `name` varchar(20) NOT NULL,
  PRIMARY KEY (`name`)
);

CREATE TABLE `notification_method` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `name` varchar(250) DEFAULT NULL,
  `type` varchar(20) NOT NULL,
  `address` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_alarm_noticication_method_type` FOREIGN KEY (`type`) REFERENCES `notification_method_type` (`name`)
);

CREATE TABLE `alarm_definition` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `description` varchar(255) DEFAULT NULL,
  `expression` longtext NOT NULL,
  `severity` varchar(20) NOT NULL,
  `match_by` varchar(255) DEFAULT '',
  `actions_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `tenant_id` (`tenant_id`),
  KEY `deleted_at` (`deleted_at`),
  CONSTRAINT `fk_alarm_definition_severity` FOREIGN KEY (`severity`) REFERENCES `alarm_definition_severity` (`name`)
);

CREATE TABLE `alarm` (
  `id` varchar(36) NOT NULL,
  `alarm_definition_id` varchar(36) NOT NULL DEFAULT '',
  `state` varchar(20) NOT NULL,
  `lifecycle_state` varchar(50) DEFAULT NULL,
  `link` varchar(512) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `state_updated_at` datetime,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `alarm_definition_id` (`alarm_definition_id`),
  CONSTRAINT `fk_alarm_definition_id` FOREIGN KEY (`alarm_definition_id`) REFERENCES `alarm_definition` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_alarm_alarm_state` FOREIGN KEY (`state`) REFERENCES `alarm_state` (`name`)
);

CREATE TABLE `alarm_action` (
  `alarm_definition_id` varchar(36) NOT NULL,
  `alarm_state` varchar(20) NOT NULL,
  `action_id` varchar(36) NOT NULL,
  PRIMARY KEY (`alarm_definition_id`,`alarm_state`,`action_id`),
  CONSTRAINT `fk_alarm_action_alarm_definition_id` FOREIGN KEY (`alarm_definition_id`) REFERENCES `alarm_definition` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_alarm_action_notification_method_id` FOREIGN KEY (`action_id`) REFERENCES `notification_method` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_alarm_action_alarm_state` FOREIGN KEY (`alarm_state`) REFERENCES `alarm_state` (`name`)
);


CREATE TABLE `alarm_metric` (
  `alarm_id` varchar(36) NOT NULL,
  `metric_definition_dimensions_id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  PRIMARY KEY (`alarm_id`,`metric_definition_dimensions_id`),
  KEY `alarm_id` (`alarm_id`),
  KEY `metric_definition_dimensions_id` (`metric_definition_dimensions_id`),
  CONSTRAINT `fk_alarm_id` FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`id`) ON DELETE CASCADE
);

CREATE TABLE `metric_definition` (
  `id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  `name` varchar(255) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `region` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`)
);

CREATE TABLE `metric_definition_dimensions` (
  `id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  `metric_definition_id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  `metric_dimension_set_id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  KEY `metric_definition_id` (`metric_definition_id`),
  KEY `metric_dimension_set_id` (`metric_dimension_set_id`),
  PRIMARY KEY (`id`)
);

CREATE TABLE `metric_dimension` (
  `dimension_set_id` binary(20) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  `name` varchar(255) NOT NULL DEFAULT '',
  `value` varchar(255) NOT NULL DEFAULT '',
   KEY `dimension_set_id` (`dimension_set_id`)
);

CREATE TABLE `sub_alarm_definition` (
  `id` varchar(36) NOT NULL,
  `alarm_definition_id` varchar(36) NOT NULL DEFAULT '',
  `function` varchar(10) NOT NULL,
  `metric_name` varchar(100) DEFAULT NULL,
  `operator` varchar(5) NOT NULL,
  `threshold` double NOT NULL,
  `period` int(11) NOT NULL,
  `periods` int(11) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sub_alarm_definition` (`alarm_definition_id`),
  CONSTRAINT `fk_sub_alarm_definition` FOREIGN KEY (`alarm_definition_id`) REFERENCES `alarm_definition` (`id`) ON DELETE CASCADE
);

CREATE TABLE `sub_alarm_definition_dimension` (
  `sub_alarm_definition_id` varchar(36) NOT NULL DEFAULT '',
  `dimension_name` varchar(255) NOT NULL DEFAULT '',
  `value` varchar(255) DEFAULT NULL,
  CONSTRAINT `fk_sub_alarm_definition_dimension` FOREIGN KEY (`sub_alarm_definition_id`) REFERENCES `sub_alarm_definition` (`id`) ON DELETE CASCADE
);

CREATE TABLE `sub_alarm` (
  `id` varchar(36) NOT NULL,
  `alarm_id` varchar(36) NOT NULL DEFAULT '',
  `sub_expression_id` varchar(36) NOT NULL DEFAULT '',
  `expression` longtext NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_sub_alarm` (`alarm_id`),
  KEY `fk_sub_alarm_expr` (`sub_expression_id`),
  CONSTRAINT `fk_sub_alarm` FOREIGN KEY (`alarm_id`) REFERENCES `alarm` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sub_alarm_expr` FOREIGN KEY (`sub_expression_id`) REFERENCES `sub_alarm_definition` (`id`)
);

CREATE TABLE `schema_migrations` (
  `version` varchar(255) NOT NULL,
  UNIQUE KEY `unique_schema_migrations` (`version`)
);

/*
 * The tables needed by Monasca for event stream definitions
 */

CREATE TABLE `stream_definition` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `name` varchar(190) NOT NULL DEFAULT '',
  `description` varchar(255) DEFAULT NULL,
  `select_by` longtext DEFAULT NULL,
  `group_by` longtext DEFAULT NULL,
  `fire_criteria` longtext DEFAULT NULL,
  `expiration` int(10) UNSIGNED DEFAULT '0',
  `actions_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `name` (`name`),
  KEY `sd_deleted_at` (`deleted_at`),
  KEY `sd_created_at` (`created_at`),
  KEY `sd_updated_at` (`updated_at`)
);

CREATE TABLE `stream_actions` (
  `stream_definition_id` varchar(36) NOT NULL,
  `action_id` varchar(36) NOT NULL,
  `action_type` varchar(20) NOT NULL,
  PRIMARY KEY (`stream_definition_id`,`action_id`,`action_type`),
  KEY `stream_definition_id` (`stream_definition_id`),
  KEY `action_type` (`action_type`),
  KEY `fk_stream_action_notification_method_id` (`action_id`),
  CONSTRAINT `fk_stream_action_stream_definition_id` FOREIGN KEY (`stream_definition_id`) REFERENCES `stream_definition` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_stream_action_notification_method_id` FOREIGN KEY (`action_id`) REFERENCES `notification_method` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_stream_actions_action_type` FOREIGN KEY (`action_type`) REFERENCES `stream_actions_action_type` (`name`)
);

CREATE TABLE `event_transform` (
  `id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `name` varchar(64) NOT NULL,
  `description` varchar(250) NOT NULL,
  `specification` longtext NOT NULL,
  `enabled` bool DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `et_tenant_name` (`tenant_id`,`name`),
  KEY `et_name` (`name`),
  KEY `et_tenant_id` (`tenant_id`),
  KEY `et_deleted_at` (`deleted_at`),
  KEY `et_created_at` (`created_at`),
  KEY `et_updated_at` (`updated_at`)
);

insert into `alarm_state` values ('UNDETERMINED');
insert into `alarm_state` values ('OK');
insert into `alarm_state` values ('ALARM');

insert into `alarm_definition_severity` values ('LOW');
insert into `alarm_definition_severity` values ('MEDIUM');
insert into `alarm_definition_severity` values ('HIGH');
insert into `alarm_definition_severity` values ('CRITICAL');

insert into `notification_method_type` values ('EMAIL');
insert into `notification_method_type` values ('WEBHOOK');
insert into `notification_method_type` values ('PAGERDUTY');

insert into `stream_actions_action_type` values ('FIRE');
insert into `stream_actions_action_type` values ('EXPIRE');
