FROM eclipse-temurin:25-jre-alpine
EXPOSE 8080 8081

RUN apk add --no-cache curl exiftool ffmpeg
RUN mkdir /vempain_file
RUN adduser -D -h /vempain_file/vempain -u 6666 -H vempain

USER vempain

ADD service/build/libs/vempain-file-backend-*.jar /app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
