# Netty backend for Flak

An implementation of the Flak SPI on top of [Netty](https://netty.io/), as an
alternative to `flak-backend-jdk`. The whole Flak test suite runs against it
(`./gradlew :flak-tests:testNetty`).

Flak can also serve its routes from a Netty server the application owns,
next to websockets on the same port.

See [Backends](../docs/backends.md) in the documentation.
