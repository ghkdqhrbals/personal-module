ARG BASE_REGISTRY=430118834498.dkr.ecr.ap-northeast-2.amazonaws.com
FROM ${BASE_REGISTRY}/amazoncorretto:21

WORKDIR /app

# run.sh, config, lib 등 복사
#COPY run.sh ./run.sh
COPY --chmod=755 run.sh ./run.sh
COPY config/ ./config/
COPY lib/ ./lib/
COPY connectors/ ./connectors/
COPY jmx/ ./jmx/
COPY lib_metrics/ ./lib_metrics/

COPY --chmod=755 debezium-server-*-runner.jar ./debezium-server-runner.jar

ENV ENABLE_DEBEZIUM_SCRIPTING=false

ENTRYPOINT ["bash", "./run.sh"]
