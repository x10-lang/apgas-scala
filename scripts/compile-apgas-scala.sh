#!/usr/bin/env bash

source common.sh

mkdir -p ${APGAS_SCALA_HOME}/bin

cd ${APGAS_SCALA_HOME} && ${SCALAC} -d bin -classpath "bin:${AKKA_HOME}/lib/akka/*:${APGAS_HOME}/lib/apgas.jar" `find src -name "*.scala" -type f`
