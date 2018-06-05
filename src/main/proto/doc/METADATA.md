# Metadata

## Signals

**Key** - `orion.signal`

|Allowed Value|Description|
|-------------|-------------|
| `START_TRACE`| Indicates that a trace has started. This must be included at the trace origin as the `StartEvent`'s metadata |
| `END_TRACE`| Indicates that a trace has ended. This must be included at the trace origin as the `EndEvent`'s metadata |

## Generic

|Key|Value|
|---|-----|
|`service.os`| The OS on which the service runs |
|`service.version`| Fully qualified service version |
|`service.platform`| The language platform eg: `JVM`, `CLR`, `PHP`, `NODEJS` |
|`user.id`| User ID if any |
|`user.email`| User email if present |

## HTTP Services

|Key|Value|
|---|-----|
|`http.method`| Any of the valid HTTP methods viz. `GET`, `POST`, `PUT`, `HEADER`, `OPTIONS`, `DELETE`, `PATCH`|
|`http.content_type`| MIME content type of the request|
|`http.status_code`| Response status code |
|`http.url`| Request URL |
|`http.request.body` | JSON request body content. Must not exceed 64kb. Any other content type will not be processed by the tracer|
|`http.response.body` | JSON response body content. Must not exceed 64kb. Any other content type will not be processed by the tracer|
|`http.request.ip`| Originating IP of the request |
|`http.request.country`| Country of originating IP |
|`http.request.headers`| Headers which are part of the request, including cookie header |
|`http.response.content_length` | Content length of response body |
|`http.response.headers`| Headers which are part of the response |

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
