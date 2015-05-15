#!/usr/bin/env bash

source common.sh

mkdir -p bin

${JAVAC} -classpath ${APGAS_IMPL_HOME}/lib/hazelcast-3.4.jar:${APGAS_HOME}/src:${APGAS_IMPL_HOME}/src:${APGAS_EXAMPLES_HOME}/src \
    ${APGAS_HOME}/src/apgas/*.java \
    ${APGAS_HOME}/src/apgas/util/*.java \
    ${APGAS_IMPL_HOME}/src/apgas/impl/GlobalRuntimeImpl.java \
    -d bin
