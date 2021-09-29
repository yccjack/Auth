FROM openjdk:11-jdk-alpine
VOLUME /tmp
ADD auth-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]