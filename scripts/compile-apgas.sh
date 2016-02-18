#!/usr/bin/env bash

source common.sh

mkdir -p ${APGAS_HOME}/bin

${JAVAC} -classpath ${APGAS_HOME}/lib/hazelcast-3.4.jar:${APGAS_HOME}/src:${APGAS_EXAMPLES_HOME}/src \
    ${APGAS_HOME}/src/apgas/*.java \
    ${APGAS_HOME}/src/apgas/util/*.java \
    -d ${APGAS_HOME}/bin
