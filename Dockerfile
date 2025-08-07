FROM eclipse-temurin:21-jre-alpine
EXPOSE 8080

RUN apk add exiftool
RUN mkdir /vempain_admin
RUN adduser -D -h /vempain_admin/vempain -u 6666 -H vempain

USER vempain

ADD service/build/libs/vempain-file-backend-*.jar /app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
