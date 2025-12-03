# bryte

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Shutdown URL](https://start.ktor.io/p/shutdown-url)                   | Enables a URL that shuts down the server when accessed                             |
| [Rate Limiting](https://start.ktor.io/p/ktor-server-rate-limiting)     | Manage request rate limiting as you see fit                                        |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [WebSockets](https://start.ktor.io/p/ktor-websockets)                  | Adds WebSocket protocol support for bidirectional client connections               |
| [Dependency Injection](https://start.ktor.io/p/ktor-di)                | Enables dependency injection for your server                                       |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [kotlinx.rpc](https://start.ktor.io/p/kotlinx-rpc)                     | Adds remote procedure call (RPC) routing                                           |
| [Koog](https://start.ktor.io/p/koog)                                   | Integrate LLMs and build AI Agents with Koog framework                             |
| [Koin](https://start.ktor.io/p/koin)                                   | Provides dependency injection                                                      |
| [HTML DSL](https://start.ktor.io/p/html-dsl)                           | Generates HTML from Kotlin DSL                                                     |
| [CSS DSL](https://start.ktor.io/p/css-dsl)                             | Generates CSS from Kotlin DSL                                                      |
| [KHealth](https://start.ktor.io/p/khealth)                             | A simple and customizable health plugin                                            |
| [Status Pages](https://start.ktor.io/p/status-pages)                   | Provides exception handling for routes                                             |
| [Static Content](https://start.ktor.io/p/static-content)               | Serves static files from defined locations                                         |
| [Resources](https://start.ktor.io/p/resources)                         | Provides type-safe routing                                                         |
| [Request Validation](https://start.ktor.io/p/request-validation)       | Adds validation for incoming requests                                              |
| [Sessions](https://start.ktor.io/p/ktor-sessions)                      | Adds support for persistent sessions through cookies or headers                    |
| [Authentication](https://start.ktor.io/p/auth)                         | Provides extension point for handling the Authorization header                     |
| [Authentication JWT](https://start.ktor.io/p/auth-jwt)                 | Handles JSON Web Token (JWT) bearer authentication scheme                          |
| [Default Headers](https://start.ktor.io/p/default-headers)             | Adds a default set of headers to HTTP responses                                    |

## Structure

This project includes the following modules:

| Path             | Description                                             |
|------------------|---------------------------------------------------------|
| [server](server) | A runnable Ktor server implementation                   |
| [core](core)     | Domain objects and interfaces                           |
| [client](client) | Extensions for making requests to the server using Ktor |

## Building

To build the project, use one of the following tasks:

| Task                                            | Description                                                          |
|-------------------------------------------------|----------------------------------------------------------------------|
| `./gradlew build`                               | Build everything                                                     |
| `./gradlew :server:buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew :server:buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew :server:publishImageToLocalRegistry` | Publish the docker image locally                                     |

## Running

To run the project, use one of the following tasks:

| Task                          | Description                      |
|-------------------------------|----------------------------------|
| `./gradlew :server:run`       | Run the server                   |
| `./gradlew :server:runDocker` | Run using the local docker image |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

