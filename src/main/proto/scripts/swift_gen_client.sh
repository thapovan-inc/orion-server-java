#!/bin/bash
source ./error.sh
export PARENT_DIR="$(dirname `pwd`)"
export OUTPUT_DIR="$PARENT_DIR/out/swift_client"
mkdir -p $OUTPUT_DIR || error_exit "$LINENO: An error has occurred."
protoc --swift_out=$OUTPUT_DIR \
       --swiftgrpc_out=Visibility=Public,Server=false,Client=true:$OUTPUT_DIR \
       --proto_path=$PARENT_DIR \
       $PARENT_DIR/*.proto || error_exit "$LINENO: An error has occurred when generating the client"