#!/bin/bash

export APGAS_VERSION=$1

if [[ -z "$APGAS_VERSION" ]]; then
    echo "usage: $0 must specify APGAS version"
    exit 1
fi

zip -x *.git* -r apgas-scala-$APGAS_VERSION.zip apgas.scala apgas.scala.examples README.md
