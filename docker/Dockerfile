ARG DOCKER_IMAGE=monasca/thresh
ARG APP_REPO=https://review.opendev.org/openstack/monasca-thresh

# Branch, tag or git hash to build from.
ARG REPO_VERSION=master
ARG CONSTRAINTS_BRANCH=master

FROM storm:1.1.2

ENV \
    MAVEN_HOME="/usr/share/maven" \
    ZOOKEEPER_SERVERS="zookeeper" \
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

COPY memory.py settings.xml.j2 /
COPY start.sh mysql_check.py kafka_wait_for_topics.py /

COPY templates /templates
COPY logging /logging

ENV \
    COMMON_REPO=https://review.opendev.org/openstack/monasca-common.git \
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
    THRESH_REPO=https://review.opendev.org/openstack/monasca-thresh.git \
    THRESH_BRANCH="master" \
    WORKER_MAX_HEAP_MB=256

ARG SKIP_COMMON_TESTS=false
ARG SKIP_THRESH_TESTS=false

SHELL ["/bin/ash", "-eo", "pipefail", "-c"]
RUN \
    echo 'http://dl-cdn.alpinelinux.org/alpine/v3.8/main' > /etc/apk/repositories && \
    echo 'http://dl-cdn.alpinelinux.org/alpine/v3.8/community' | tee -a /etc/apk/repositories && \
    apk upgrade --purge && \
    apk add --no-cache --virtual build-dep maven git py3-pip python3-dev git openjdk8 make g++ && \
    apk add --no-cache python3 mysql-client py3-setuptools py3-jinja2 && \
    mkdir /root/.m2 && \
    pip3 install \
        pykafka \
        Templer==1.1.4 && \
    mkdir /repo && \
    set -x && mkdir /monasca-common && cd /monasca-common && \
    git init && \
    git remote add origin "$COMMON_REPO" && \
    git fetch origin "$COMMON_BRANCH" && \
    git reset --hard FETCH_HEAD && \
    mvn --quiet -B clean install $([ "$SKIP_COMMON_TESTS" = "true" ] && echo "-DskipTests") && \
    cd / && \
    rm -rf /monasca-common && \
    mkdir /monasca-thresh && cd /monasca-thresh && \
    git init && \
    git remote add origin "$THRESH_REPO" && \
    git fetch origin "$THRESH_BRANCH" && \
    git reset --hard FETCH_HEAD && \
    cd thresh && \
    mvn --quiet -B clean package $([ "$SKIP_THRESH_TESTS" = "true" ] && echo "-DskipTests") && \
    cp /monasca-thresh/thresh/target/*-SNAPSHOT-shaded.jar /monasca-thresh.jar && \
    cd / && \
    # Save info about build to `/VERSIONS` file.
    printf "App:        %s\\n" "$DOCKER_IMAGE" >> /VERSIONS && \
    printf "Repository: %s\\n" "$APP_REPO" >> /VERSIONS && \
    printf "Version:    %s\\n" "$REPO_VERSION" >> /VERSIONS && \
    printf "Revision:   %s\\n" "$GIT_COMMIT" >> /VERSIONS && \
    printf "Build date: %s\\n" "$CREATION_TIME" >> /VERSIONS && \
    printf "Revision:   %s\\n" \
        "$(git -C /app rev-parse FETCH_HEAD)" >> /VERSIONS && \
    printf "Monasca-common version:     %s\\n" "$COMMON_VERSION" \
        >> /VERSIONS && \
    printf "Monasca-common pip version: %s\\n" \
        "$(pip3 freeze 2>&1 | grep 'monasca-common')" >> /VERSIONS && \
    printf "Monasca-common revision:    %s\\n" \
        "$COMMON_GIT_COMMIT" >> /VERSIONS && \
    printf "Constraints file: %s\\n" \
        "$CONSTRAINTS_FILE"?h="$CONSTRAINTS_BRANCH" >> /VERSIONS && \
    rm -rf /monasca-thresh && \
    rm -rf /repo && \
    apk del build-dep

ENTRYPOINT ["/start.sh"]
