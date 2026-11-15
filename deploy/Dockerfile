# Build the program first on your computer:  mvn clean package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/inventory-management-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
