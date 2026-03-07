# Base image — provides Java 21 runtime (Eclipse Temurin, industry standard replacement for openjdk)
FROM eclipse-temurin:21-jdk

# Metadata — who maintains this image
LABEL maintainer="Suraj Paudel"

# Copy the fat JAR from local target folder into the image as app.jar
# Source (left) = must match actual JAR name in target folder — strict
# Destination (right) = custom name inside image — flexible
COPY target/booking-service-0.0.1-SNAPSHOT.jar booking-service.jar

# Command that runs automatically when a container starts from this image
# Equivalent to: java -jar booking-service.jar
ENTRYPOINT ["java", "-jar", "booking-service.jar"]