FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B verify

FROM tomcat:10.1-jdk17-temurin
RUN groupadd --system roleout && useradd --system --gid roleout roleout \
    && chown -R roleout:roleout /usr/local/tomcat
COPY --from=build --chown=roleout:roleout /build/target/RoleOut.war /usr/local/tomcat/webapps/RoleOut.war
COPY --chmod=755 deploy/start.sh /usr/local/bin/roleout-start
ENV ROLEOUT_PUBLIC_DEPLOYMENT=true \
    DB_POOL_MAX_SIZE=4 DB_POOL_MIN_IDLE=0 \
    JAVA_OPTS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
USER roleout
EXPOSE 8080
CMD ["/usr/local/bin/roleout-start"]
