# Vempain File service

The service is part of the [Vempain](https://vempain.poltsi.fi/) project. The file service maintains a database of original files, their metadata as well as
tracks any exported files (such as jpg-files converted from the original RAW files). The main purpose of the service is metadata management and file export. The
service can be used in conjunction with the Vempain Admin as well as Vempain Simplex to handle document flows from source to web.

Service exposes REST APIs designed to be used by the Vempain File frontend.

## Swagger API

[Local UI](http://localhost:8081/actuator/swagger-ui/index.html)

## How to build

In order to build the project, you need to have Java 21 and Git installed on your machine. Begin with cloning the repository. After that run the following
command in the root directory of the project:

```bash
./gradlew clean test \
bootJar bootRun \
--args="--spring.profiles.active=local --vempain.app.frontend-url=http://localhost:3000,http://localhost:8081" \
2>&1 | tee /tmp/out-vempain_file.log
```

This will build the project, run the tests, create a JAR file, and start the application with the `local` profile. The output will be logged to
`/tmp/out-vempain_file.log`.

Note that the `vempain.app.frontend-url` argument is used to specify the frontend URL for the application. You can change it according to your needs. You need
to include the `http://localhost:8081` if you want to use the Swagger UI.
