# Imagem a partir do jar já buildado.
# O build não acontece dentro do Docker porque o service depende da interface pma:exchange,
# que só existe no repositório Maven local (./mvnw install em pma26-exchange).
#
#   ./mvnw clean package
#   docker build -t exchange .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/exchange-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
