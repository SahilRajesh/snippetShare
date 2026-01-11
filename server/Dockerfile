# Use a base image with Java runtime
FROM openjdk:23-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the application JAR file
COPY target/snippet-share-0.0.1-SNAPSHOT.jar app.jar

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]