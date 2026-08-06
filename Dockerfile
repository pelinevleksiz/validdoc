FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && \
    apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-tur curl && \
    rm -rf /var/lib/apt/lists/* && \
    mkdir -p /usr/share/tesseract-ocr/4.00/tessdata/configs && \
    printf 'lstm_rating_coefficient 3\n' > /usr/share/tesseract-ocr/4.00/tessdata/configs/validdoc

RUN groupadd -r validdoc && useradd -r -g validdoc -d /app validdoc

WORKDIR /app
COPY target/validdoc-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R validdoc:validdoc /app

USER validdoc

EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading", "-jar", "app.jar"]