# Compression

Flak compresses responses with gzip when the handler allows it, the client
sent `Accept-Encoding: gzip`, and the response has no `Content-Encoding`
yet.

## Allowing compression

- `@Compress` on a handler, or on a class to cover all its handlers
- `response.setCompressionAllowed(true)`, from within the handler

```java
@Route("/api/report")
@Compress
public String report() { ... }
```

## What gets compressed

| Response | Compressed when allowed |
| -------- | ----------------------- |
| a returned `String` or `byte[]` | if larger than the threshold |
| a returned `InputStream` | always, since its size is unknown |
| JSON written by [flak-jackson](json.md) | always |
| [static files](static-resources.md) | automatically, see below |
| bytes written by the handler into `getOutputStream()` | never |

The threshold is 1024 bytes. Set the `flak.compressThreshold` system property
to change it, e.g. `-Dflak.compressThreshold=4096`.

A handler writing into the output stream can compress what it writes itself.
It sets `Content-Encoding: gzip` and wraps the stream in a
`GZIPOutputStream`, which it must close. Flak then leaves the response alone.

## Static resources

[Static files](static-resources.md) are compressed according to their
content type, without any annotation. That means text, JSON and JavaScript by
default. Files from a directory must also exceed the threshold, while
resources from the classpath are always compressed, since their size is
unknown. A custom `ContentTypeProvider` can choose other types by overriding
`shouldCompress()`.
