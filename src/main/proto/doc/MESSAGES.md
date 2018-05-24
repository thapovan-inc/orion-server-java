# Overview

The protocol takes two assumptions. Every user action is a `Trace`. Every `Trace` spans across different computational contexts. The computational context can be a _service_, a _class_ or a _method_. Each context is represented by a `Span`. Individual spans can report multiple events. Though events can be of many types, for the sake of simplicity we broadly classify the events as `StartEvent`, `StopEvent` and `LogEvent`.

`StartEvent` denotes start of a span, `StopEvent` denotes end of a span and `LogEvent` denotes any other event that is emitted during the lifetime of the span.

Each of the event carries optional `metadata` and mandatory `event_id`

> Note: In platforms where unsigned 64-bit integers are not supported, the highest possible native integer precision must be used

## Trace

```protobuf
message Trace {
    string trace_id = 1;
}
```

A trace is denoted by a `trace_id` which must be a [UUID v4](https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_(random)).

A trace by itself is of little help. Hence the `Trace` context is always represented and reported with a `Span` context. 

## Span

```protobuf
message Span {
    Trace trace_context = 1;
    string span_id = 2;
    oneof event {
        StartEvent start_event = 3;
        EndEvent end_event = 4;
        LogEvent log_event = 5;
    }
    uint64 timestamp  = 6;
    string service_name = 7;
    string event_location = 8;
    string parent_span_id = 9;
}
```

The `Span` message/class actually denotes an event from the span's lifecycle. Meaning, when a `Span` object is sent to the server, it is always accompanied by an event and only one event is allowed per `Span` message.

A span is uniquely identified by a combination of its `Trace` context and a `span_id` (UUID v4). Optionally, where available the `parent_span_id` can be mentioned to denote that the current span is a child of another span within the same trace context.

Each span message is timestamped by the producer (the entity which emits the span message) and the `timestamp` is denoted an unsigned 64-bit integer representing the UTC time in microsecond. In platforms where microsecond precision is not available or not plausible due to performance constraints the microsecond part of the integer (last 3 digits) can be set to zero. This sets the minimum acceptable precision to be milliseconds. `timestamp` must always be for **UTC**.

The `event` field can store either a `StartEvent`, an `EndEvent` or a `LogEvent`. The structure of these event messages are explained in the following sections.

The field `service_name` is a string which denotes the canonical name of the computational context denoted by the span. By convention, this is the process/server through which the request gets processed. In case of mobile applications this denotes the application identifier or the `app_id`.

The field `location` is a string which denotes the code location within which the event is being emitted. In class oriented languages this can be of the form `ClassName::methodName::line_number`. Depending on the language support and convention, this can also be of the form `Filename::line_number`. 

## StartEvent

```protobuf
message StartEvent {
    int64 event_id = 1;
    oneof metadata {
        google.protobuf.Struct protoStruct = 2;
        string jsonString = 3;
    }
}
```

The `StartEvent` is emitted only once and at the start of the span. Any span without a start event is marked as an anomaly. The `event_id` is denoted by an unsigned 64-bit integer. The `event_id` must be [monotonically increasing](https://en.wikipedia.org/wiki/Monotonic_function) within a `Span`. 

Optionally `metadata` can be added to the event as a `jsonString` or as a `protobuf Struct`. It would make sense to include the platform details such as OS, `process_id` etc. as the metadata for the `StartEvent`

## EndEvent

```protobuf
message EndEvent {
    uint64 event_id = 1;
    oneof metadata {
        google.protobuf.Struct protoStruct = 2;
        string jsonString = 3;
    }
}
```

The `EndEvent` denotes the end of a span. Any messages sent after the `EndEvent` will be discarded, if they fall under the same Span context.

## LogEvent

```protobuf
message LogEvent {
    uint64 event_id = 1;
    LogLevel level = 2;
    string message = 3;
    oneof metadata {
        google.protobuf.Struct protoStruct = 4;
        string jsonString = 5;
    }
}
```

`LogEvent` is emitted anytime between the `StartEvent` and `EndEvent` events within a span. `LogEvent` is always accompanied by a message string and is labeled using a `LogLevel`. `LogLevel` can be one of the following values: `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`. Any level above `INFO` triggers a notification event which would alert the developers/stakeholders of the system.

# Trace ID Injection

## HTTP Requests
When the trace context is passed on to a HTTP service, the following headers must be passed to denote the trace context

* `X-ORION-TRACE-ID` : The UUID v4 trace ID of the originating context
* `X-ORION-PARENT-SPAN-ID` : The UUID v4 trace ID of the originating span.

The receiving service is responsible to parse the `Trace` context and the parent `Span` content. It is also the responsibility of the receiver to set up appropriate **CORS** headers.

# Metadata

## Generic

|Key|Value|
|---|-----|
|`service.os`| The OS on which the service runs |
|`service.version`| Fully qualified service version |
|`service.platform`| The language platform eg: `JVM`, `CLR`, `PHP`, `NODEJS` |

## HTTP Services

|Key|Value|
|---|-----|
|`http.method`| Any of the valid HTTP methods viz. `GET`, `POST`, `PUT`, `HEADER`, `OPTIONS`, `DELETE`, `PATCH`|
|`http.content_type`| MIME content type of the request|
|`http.status_code`| Response status code |
|`http.url`| Request URL |
|`http.request.body` | JSON request body content. Must not exceed 64kb. Any other content type will not be processed by the tracer|
|`http.response.body` | JSON response body content. Must not exceed 64kb. Any other content type will not be processed by the tracer|
|`http.response.content_length` | Content length of response body |

## Mobile Application

|Key|Value|
|---|-----|
|`app.version`| Fully qualified app version|
|`app.os`| Either `iOS` or `Android`|
|`app.osVersion`| Fully qualified OS version as a string |


### Android Application

|Key|Value|
|---|-----|
|`android.activity`| The activity from which the event is emitted|
|`android.fragment`| The fragment within the activity from which the event is emitted|
|`android.service`| Denoted the service from which the event originates|