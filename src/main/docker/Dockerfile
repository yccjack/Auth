FROM openjdk:11-jdk
VOLUME /tmp
ADD  target/${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]