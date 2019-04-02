# Still need fixing to rst format

===============================
Docker image for Monasca Thresh
===============================

This image has a containerized version of the Monasca Threshold Engine. For
more information on the Monasca project, see [the wiki][1].

Sources: [monasca-thresh][2] &middot; [monasca-docker][3] &middot; [Dockerfile][4]


Usage
=====

The Threshold engine requires configured instances of MySQL, Kafka,
Zookeeper, and optionally Storm [monasca-api][5]. In environments resembling the official
[docker-compose][3] or [Kubernetes][6] environments, this image requires little
to no configuration and can be minimally run like so:

    docker run monasca/thresh:master

Environment variables
~~~~~~~~~~~~~~~~~~~~~

============================= ====================================== ======================================================================
 Variable                     Default                                Description
============================= ====================================== ======================================================================
 KAFKA_URI                    kafka:9092                             URI to Apache Kafka
 KAFKA_WAIT_FOR_TOPICS        alarm-state-transitions,metrics,events Comma-separated list of topic names to check
 KAFKA_WAIT_RETRIES           24                                     Number of Kafka connection attempts
 KAFKA_WAIT_DELAY             5                                      Seconds to wait between attempts
 MYSQL_HOST                   mysql                                  MySQL hostname
 MYSQL_PORT                   3306                                   MySQL port
 MYSQL_USER                   thresh                                 MySQL username
 MYSQL_PASSWORD               password                               MySQL password
 MYSQL_DATABASE               mon                                    MySQL database name
 MYSQL_WAIT_RETRIES           24                                     Number of MySQL connection attempts
 MYSQL_WAIT_DELAY             5                                      Seconds to wait between attempts
 ZOOKEEPER_URL                zookeeper:2181                         Zookeeper URL
 NO_STORM_CLUSTER             unset                                  If ``true``, run without Storm daemons
 STORM_WAIT_RETRIES           24                                     # of tries to verify Storm availability
 STORM_WAIT_DELAY             5                                      # seconds between retry attempts
 WORKER_MAX_MB                unset                                  If set and ``NO_STORM_CLUSTER``is ``true``, use as MaxRam Size for JVM
 METRIC_SPOUT_THREADS         2                                      Metric Spout threads
 METRIC_SPOUT_TASKS           2                                      Metric Spout tasks
 EVENT_SPOUT_THREADS          2                                      Event Spout Threads
 EVENT_SPOUT_TASKS            2                                      Event Spout Tasks
 EVENT_BOLT_THREADS           2                                      Event Bolt Threads
 EVENT_BOLT_TASKS             2                                      Event Bolt Tasks
 FILTERING_BOLT_THREADS       2                                      Filtering Bolt Threads
 FILTERING_BOLT_TASKS         2                                      Filtering Bolt Tasks
 ALARM_CREATION_BOLT_THREADS  2                                      Alarm Creation Bolt Threads
 ALARM_CREATION_BOLT_TASKS    2                                      Alarm Creation Bolt Tasks
 AGGREGATION_BOLT_THREADS     2                                      Aggregation Bolt Threads
 AGGREGATION_BOLT_TASKS       2                                      Aggregation Bolt Tasks
 THRESHOLDING_BOLT_THREADS    2                                      Thresholding Bolt Threads
 THRESHOLDING_BOLT_TASKS      2                                      Thresholding Bolt Tasks
 THRESH_STACK_SIZE            1024k                                  JVM stack size
============================= ====================================== ======================================================================


Wait scripts environment variables
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
======================== ================================ =========================================
Variable                 Default                          Description
======================== ================================ =========================================
KAFKA_URI                kafka:9092                       URI to Apache Kafka
KAFKA_WAIT_FOR_TOPICS    alarm-state-transitions,metrics, Comma-separated list of topic names
                         events                           to check
KAFKA_WAIT_RETRIES       24                               Number of kafka connection attempts
KAFKA_WAIT_DELAY         5                                Seconds to wait between attempts
MYSQL_HOST               mysql                            The host for MySQL
MYSQL_PORT               3306                             The port for MySQL
MYSQL_USER               monapi                           The MySQL username
MYSQL_PASSWORD           password                         The MySQL password
MYSQL_DB                 mon                              The MySQL database name
MYSQL_WAIT_RETRIES       24                               Number of MySQL connection attempts
MYSQL_WAIT_DELAY         5                                Seconds to wait between attempts
======================== ================================ =========================================

Building Monasca Thresh image
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Example:
  $ ./build_image.sh <repository_version> <upper_constains_branch> <common_version>

Everything after ``./build_image.sh`` is optional and by default configured
to get versions from ``Dockerfile``. ``./build_image.sh`` also contain more
detailed build description.


Scripts
~~~~~~~
start.sh
    In this starting script provide all steps that lead to the proper service
    start. Including usage of wait scripts and templating of configuration
    files. You also could provide the ability to allow running container after
    service died for easier debugging.

health_check.py
  This file will be used for checking the status of the application.

# Test how it's working or if it's working

Running with and without Storm
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Threshold Engine can be run in two different modes, with Storm Daemons or without Storm Daemons.
If run with the Storm Daemons, multiple Storm Supervisor containers can be used with more than one worker process
in each. With no Storm Daemons, only a single Threshold Engine container can be run with a single worker process.

The default docker-compose.yml file is configured to run without Storm. To change docker-compose.yml to run
with Storm, delete the `thresh` service entry and replace it with the below::

  storm-nimbus:
    image: monasca/storm:1.0.3
    command: storm nimbus
    environment:
        STORM_LOCAL_HOSTNAME: "storm-nimbus"
        WORKER_LOGS_TO_STDOUT: "true"
    depends_on:
        - zookeeper

  storm-supervisor:
    image: monasca/storm:1.0.3
    command: storm supervisor
    depends_on:
      - storm-nimbus
      - zookeeper
      - kafka

  thresh-init:
    image: monasca/thresh:master
    environment:
      STORM_WAIT_RETRIES: 50
    depends_on:
      - zookeeper
      - storm-nimbus
      - storm-supervisor


[1]: https://wiki.openstack.org/wiki/Monasca
[2]: https://opendev.org/openstack/monasca-thresh
[3]: https://github.com/monasca/monasca-docker/
[4]: https://github.com/monasca/monasca-docker/blob/master/monasca-thresh/Dockerfile
[5]: https://github.com/monasca/monasca-docker/blob/master/storm/Dockerfile
[6]: https://github.com/monasca/monasca-helm
[7]: https://v2.developer.pagerduty.com/docs/events-api
