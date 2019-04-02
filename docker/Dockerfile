ARG DOCKER_IMAGE=monasca/thresh
ARG APP_REPO=https://git.openstack.org/openstack/monasca-thresh

# Branch, tag or git hash to build from.
ARG REPO_VERSION=master
ARG CONSTRAINTS_BRANCH=master

FROM monasca/storm:1.1.1-1.0.12

ENV MAVEN_HOME /usr/share/maven
COPY health_check.py kafka_wait_for_topics.py mysql_check.py settings.xml.j2 start.sh /

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

ARG CREATION_TIME
ARG DOCKER_IMAGE
ARG APP_REPO
ARG GITHUB_REPO
ARG REPO_VERSION
ARG GIT_COMMIT
ARG CONSTRAINTS_BRANCH
ARG CONSTRAINTS_FILE
ARG EXTRA_DEPS
ARG COMMON_REPO
ARG COMMON_VERSION
ARG COMMON_GIT_COMMIT

LABEL \
    org.opencontainers.image.created="$CREATION_TIME" \
    org.opencontainers.image.title="$DOCKER_IMAGE" \
    org.opencontainers.image.source="$APP_REPO" \
    org.opencontainers.image.url="$GITHUB_REPO" \
    org.opencontainers.image.version="$REPO_VERSION" \
    org.opencontainers.image.revision="$GIT_COMMIT" \
    org.opencontainers.image.licenses="Apache-2.0" \
    org.openstack.constraints_uri="$CONSTRAINTS_FILE?h=$CONSTRAINTS_BRANCH" \
    org.openstack.monasca.python.extra_deps="$EXTRA_DEPS" \
    org.openstack.monasca.common.source="$COMMON_REPO" \
    org.openstack.monasca.common.version="$COMMON_VERSION" \
    org.openstack.monasca.common.revision="$COMMON_GIT_COMMIT"

RUN \
    apk update && \
    apk add --no-cache --virtual build-dep \
        maven \
        git \
        openssl \
        openjdk8 \
        ca-certificates \
        make \
        python \
        python3 \
        py2-pip \
        py3-pip \
        python-dev \
        python3-dev \
        g++ && \
    apk add --no-cache \
        mysql-client \
        py-setuptools && \
    mkdir /root/.m2 && \
    pip install --upgrade pip && \
    pip3 install --upgrade pip && \
    pip install pykafka && \
    python /template.py settings.xml.j2 /root/.m2/settings.xml && \
    mkdir /repo && \
    mkdir -p /app && \
    wget -O /app/upper-constraints.txt \
                "$CONSTRAINTS_FILE"?h="$CONSTRAINTS_BRANCH" && \
    # Install packages needed by wait scripts and used for templating.
    pip3 install \
        pykafka==2.6.0 \
        PyMySQL \
        Templer==1.1.4 \
        --constraint /app/upper-constraints.txt && \
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
    rm -rf /repo && \
    apk del build-dep

HEALTHCHECK --interval=5s --timeout=2s \
                   CMD python3 health_check.py || exit 1

# Implement start script in `start.sh` file.
CMD ["/start.sh"]