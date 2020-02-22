# flak-jackson

This flak plugin allows automatic conversion from/to JSON:
 * methods can directly accept objects de-serialized from 
 JSON data
 * methods can directly return objects, these will be converted automatically
  to JSON

## 1.2.0

A new `ObjectMapper` object is now created for each incoming request. This
is to avoid concurrent requests to be queued because of synchronization. If needed,
you can set a custom `ObjectMapperProvider` to `JacksonPlugin` to change this behavior.

