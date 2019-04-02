ARG DOCKER_IMAGE=monasca/thresh
ARG APP_REPO=https://git.openstack.org/openstack/monasca-thresh.git

# Branch, tag or git hash to build from.
ARG REPO_VERSION=master
ARG CONSTRAINTS_BRANCH=master

FROM monasca/storm:1.1.1-1.0.12

ENV MAVEN_HOME /usr/share/maven
COPY health_check.py kafka_wait_for_topics.py settings.xml.j2 start.sh /

ENV COMMON_REPO=https://git.openstack.org/openstack/monasca-common.git \
  COMMON_BRANCH="master" \
  KAFKA_URI="kafka:9092" \
  KAFKA_WAIT_FOR_TOPICS=alarm-state-transitions,metrics,events \
  LOGSTASH_FIELDS="service=monasca-thresh" \
  LOG_CONFIG_FILE="/storm/log4j2/cluster.xml" \
  MYSQL_DB_DATABASE=mon \
  MYSQL_DB_HOST=mysql \
  MYSQL_DB_PASSWORD=password \
  MYSQL_DB_PORT=3306 \
  MYSQL_DB_USERNAME=thresh \
  NO_STORM_CLUSTER=false \
  STORM_WAIT_DELAY=5 \
  STORM_WAIT_RETRIES=12 \
  STORM_WAIT_TIMEOUT=20 \
  THRESH_BRANCH="master" \
  THRESH_REPO=https://git.openstack.org/openstack/monasca-thresh.git \
  WORKER_MAX_HEAP_MB=256

ARG REBUILD=1
ARG SKIP_COMMON_TESTS=false
ARG SKIP_THRESH_TESTS=false

RUN apk add --no-cache --virtual build-dep maven git py2-pip python-dev git openjdk8 make g++ && \
  apk add --no-cache python mysql-client py-setuptools && \
  mkdir /root/.m2 && \
  pip install pykafka && \
  python /template.py settings.xml.j2 /root/.m2/settings.xml && \
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

# Implement start script in `start.sh` file.
CMD ["/start.sh"]