# Overview

The protocol allows the client to upload the `Span` events individually or in bulk. When uploading individually, the client has an option to upload one `Span` event per request or upload a stream of `Span` events over the same request. The server responds with a `ServerResponse` message at the end of the request.

## Unary Request

```protobuf
message ServerResponse {
    bool success = 1;
    string code = 2;
    string message = 3;
}

message UnaryRequest {
    string auth_token = 1;
    Span span_data = 2;
}

service Tracer {
    ...

    rpc UploadSpan (UnaryRequest) returns (ServerResponse);
    
    ...
}
```

The client can upload a single span event and await on confirmation response from the server. The server response contains a `success` field which is set to `true` when the request has succeeded. When the request fails due to a reason `success` is `false` and the error `code` is set along with the `message`.

In addition to the `Span` data, the `UnaryRequest` message contains the `auth_token` string which is issued out of band to the client. How this `auth_token` is obtained is beyond the scope of this document.

## Bulk Upload

```protobuf
message ServerResponse {
    bool success = 1;
    string code = 2;
    string message = 3;
}

message BulkRequest {
    string auth_token = 1;
    repeated Span span_data = 2;
}

service Tracer {
    ...

    rpc UploadSpanBulk (BulkRequest) returns (ServerResponse);
    
    ...
}
```

This is similar to the _Unary Request_ except that, instead of sending a single `Span` message, the client can upload a _list_ or an _array_ of `Span` messages.

## Streaming Upload

```protobuf
message ServerResponse {
    bool success = 1;
    string code = 2;
    string message = 3;
}

message StreamRequest {
    oneof request {
        ControlRequest control_request = 1;
        Span span_data = 2;
    }
}

service Tracer {
    ...

    rpc UploadSpanStream (stream StreamRequest) returns (stream ServerResponse);
    
    ...
}
```

The streaming upload allows the client to stream the span events as soon as they are emitted. This also facilitates near real-time processing at the server side. The `StreamRequest` starts and ends with `ControlRequest` messages for authentication and end-stream signalling. Once the server has accepted the authentication request, a `ServerResponse` is sent to denote success/failure. Upon success, the client can start emitting the `Span` events. When the client wishes to terminate the stream, an _end-stream_ signal must be send to the server. This much akin to an EOF signal. The server would response with an acknowledgement and terminate the connection.

### Control Request

```protobuf
message ControlRequest {
    enum RequestType {
        AUTH = 0;
        END_STREAM = 1;
    }
    RequestType request_type = 1;
    oneof params {
        google.protobuf.Struct protoStruct = 2;
        string jsonString = 3;
    }
}
```

The `ControlRequest` can be either an `AUTH` request or an `END_STREAM` signal with additional `params`. For authentication the `auth_token` is added to the params map and sent to the server. The `params` map can either be a ProtoBuf `Struct` or can be a JSON string.