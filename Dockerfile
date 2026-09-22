FROM azul-zulu:27-jre-headless-alpine

# Create a non-root user
ARG UID=1000
ARG GID=1000

RUN addgroup -g "$GID" chronivaro \
    && adduser -D -u "$UID" -G chronivaro chronivaro \
    && mkdir -p /app /chronivaro-runtime /chronivaro-logs \
    && chown -R "$UID:$GID" /app /chronivaro-runtime /chronivaro-logs \
    && chmod 775 /chronivaro-runtime /chronivaro-logs

WORKDIR /app

COPY --chown=$UID:$GID chronivaro-app/target/chronivaro.jar /app/chronivaro.jar
COPY --chown=$UID:$GID chronivaro-app/target/lib /app/lib

USER $UID:$GID

EXPOSE 8080

ENV PORT=8080 \
    CHRONIVARO_PORT=8080 \
    STROLCH_PATH=/chronivaro-runtime \
    STROLCH_ENVIRONMENT=dev

ENTRYPOINT ["java", "-jar", "/app/chronivaro.jar"]
