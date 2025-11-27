[![Dependabot Updates](https://github.com/Vempain/vempain-file-backend/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/dependabot/dependabot-updates)
[![CodeQL](https://github.com/Vempain/vempain-file-backend/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/github-code-scanning/codeql)
[![CI](https://github.com/Vempain/vempain-file-backend/actions/workflows/ci.yaml/badge.svg)](https://github.com/Vempain/vempain-file-backend/actions/workflows/ci.yaml)
![GitHub Tag](https://img.shields.io/github/v/tag/Vempain/vempain-file-backend)
![GitHub License](https://img.shields.io/github/license/Vempain/vempain-file-backend?color=green)

# Vempain File service

The service is part of the [Vempain](https://vempain.poltsi.fi/) project. The file service maintains a database of original files, their metadata as well as
tracks any exported files (such as jpg-files converted from the original RAW files). The service also forms general notion of file groups based on the
directories in which the files are placed, however other groupings can also be created. The main purpose of the service is metadata management and file export.

The service can be used in conjunction with the Vempain Admin as well as Vempain Simplex to handle document flows from source to web.

Service exposes REST APIs designed to be used by the Vempain File frontend.

## File grouping

The default way to group files is based on the directory structure. Each directory forms a file group and all files within that directory belong to that group.
This is the default behavior when files are uploaded via Vempain Simplex or Vempain Admin and is also the way which requires the least amount of of effort to
maintain. Thus, the recommended way to store the files is to place them in leaf directories, i.e. directories that do not contain any other directories and
also grouped on higher level according to relatedness.

A clarifying example where the upper level directories group related files together:

```
Animals/
    Cats/
      Cats_On_The_Beach/
         cat_beach1.raw
         cat_beach2.raw
      Cats_At_Home/
         cat_home1.raw
         cat_home2.raw
    Dogs/
        Dogs_In_The_Park/
            dog_park1.raw
            dog_park2.raw
        Dogs_At_Home/
            dog_home1.raw
            dog_home2.raw
```

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
