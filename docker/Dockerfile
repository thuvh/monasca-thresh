ARG DOCKER_IMAGE=monasca/thresh
ARG APP_REPO=https://git.openstack.org/openstack/monasca-thresh

# Branch, tag or git hash to build from.
ARG REPO_VERSION=master
ARG CONSTRAINTS_BRANCH=master

FROM storm:1.1.1

ENV MAVEN_HOME /usr/share/maven

ENV ZOOKEEPER_SERVERS="zookeeper" \
  ZOOKEEPER_PORT="2181" \
  ZOOKEEPER_WAIT="true" \
  SUPERVISOR_SLOTS_PORTS="6701,6702" \
  SUPERVISOR_MAX_MB="256" \
  WORKER_MAX_MB="784" \
  NIMBUS_SEEDS="storm-nimbus" \
  NIMBUS_MAX_MB="256" \
  UI_MAX_MB="768" \
  WORKER_LOGS_TO_STDOUT="false" \
  USE_SSL_ENABLED="true"

COPY template.py memory.py settings.xml.j2 start.sh /

COPY templates /templates
COPY logging /logging

ENV \
    COMMON_REPO=https://git.openstack.org/openstack/monasca-common.git \
    COMMON_BRANCH="master" \
    KAFKA_URI="kafka:9092" \
    KAFKA_WAIT_FOR_TOPICS=alarm-state-transitions,metrics,events \
    LOGSTASH_FIELDS="service=monasca-thresh" \
    LOG_CONFIG_FILE="/storm/log4j2/cluster.xml" \
    MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    MYSQL_USER=thresh \
    MYSQL_PASSWORD=password \
    MYSQL_DB=mon \
    NO_STORM_CLUSTER=false \
    STORM_WAIT_DELAY=5 \
    STORM_WAIT_RETRIES=12 \
    STORM_WAIT_TIMEOUT=20 \
    THRESH_BRANCH="master" \
    THRESH_REPO=https://git.openstack.org/openstack/monasca-thresh.git \
    WORKER_MAX_HEAP_MB=256

ARG SKIP_COMMON_TESTS=false
ARG SKIP_THRESH_TESTS=false

RUN apk add --no-cache --virtual build-dep maven git py2-pip python-dev git openjdk8 make g++ && \
  apk add --no-cache python mysql-client py-setuptools py2-jinja2 && \
  mkdir /root/.m2 && \
  pip install pykafka && \
  python /template.py /settings.xml.j2 /root/.m2/settings.xml && \
  mkdir /repo && \
  set -x && mkdir /monasca-common && cd /monasca-common && \
  git init && \
  git remote add origin $COMMON_REPO && \
  git fetch origin $COMMON_BRANCH && \
  git reset --hard FETCH_HEAD && \
  mvn -B clean install $([ "$SKIP_COMMON_TESTS" = "true" ] && echo "-DskipTests") && \
  cd / && \
  rm -rf /monasca-common && \
  mkdir /monasca-thresh && cd /monasca-thresh && \
  git init && \
  git remote add origin $THRESH_REPO && \
  git fetch origin $THRESH_BRANCH && \
  git reset --hard FETCH_HEAD && \
  cd thresh && \
  mvn -B clean package $([ "$SKIP_THRESH_TESTS" = "true" ] && echo "-DskipTests") && \
  cp /monasca-thresh/thresh/target/*-SNAPSHOT-shaded.jar /monasca-thresh.jar && \
  cd / && \
  rm -rf /monasca-thresh && \
  rm -rf /repo && \
  apk del build-dep

COPY kafka_wait_for_topics.py /

ENTRYPOINT ["/start.sh"]