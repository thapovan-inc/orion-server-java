#!/bin/bash
source ./error.sh
export PARENT_DIR="$(dirname `pwd`)"
export OUTPUT_DIR="$PARENT_DIR/out/nodejs_client"
mkdir -p $OUTPUT_DIR || error_exit "$LINENO: An error has occurred."
grpc_tools_node_protoc --js_out=import_style=commonjs,binary:$OUTPUT_DIR \
                 --grpc_out=$OUTPUT_DIR \
                 --plugin=protoc-gen-grpc=`which grpc_tools_node_protoc_plugin` \
                 --proto_path=$PARENT_DIR \
                $PARENT_DIR/*.proto || error_exit "$LINENO: An error has occurred when generating the client"