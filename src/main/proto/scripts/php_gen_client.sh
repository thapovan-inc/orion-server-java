#!/bin/bash
source ./error.sh
export PARENT_DIR="$(dirname `pwd`)"
export OUTPUT_DIR="$PARENT_DIR/out/php_client"
mkdir -p $OUTPUT_DIR || error_exit "$LINENO: An error has occurred when creating output directory"
protoc --php_out=$OUTPUT_DIR \
        --grpc_out=$OUTPUT_DIR \
        --plugin=protoc-gen-grpc=/usr/local/bin/grpc_php_plugin  \
        --proto_path=$PARENT_DIR \
        $PARENT_DIR/*.proto || error_exit "$LINENO: An error has occurred when generating the client"