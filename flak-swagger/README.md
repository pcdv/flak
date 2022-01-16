# OpenAPI generator

The goal of this utility is to automate as much as possible the generation of an
OpenAPI specification from what Flak is able to scan.

The part that cannot be guessed automatically can be provided via 
[Swagger annotations](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations#quick-annotation-overview).

The generator can probably not generate a complete specification, but as its goal is
to return an OpenAPI object, it is always possible to add missing information in the
generated object.

Automated features:
 * schemas from returned types and types mapped to request body. Note that for proper generation, a Jackson ObjectMapper should be passed to the generator before scanning.

Supported annotations:
 * @Tag (at class or method level, the default tag is the class name)
 * @Parameter (query parameters are not automatically generated and need an annotation)
 * @Description
 * ...

Not supported (yet):
 * Automatic generation of path parameters
 * Scanning a full Flak app (scanning is only at handler class level)
 * 

See this [example](https://github.com/pcdv/flak/tree/master/flak-tests/src/test/java/flask/test/SwaggerTest.java)
from JUnits.
