FROM eclipse-temurin:25-jre

# Create a non-root user
ARG UID=1000
ARG GID=1000
RUN if ! getent group $GID >/dev/null; then groupadd -g $GID chronivaro; fi && \
    if ! getent passwd $UID >/dev/null; then useradd -u $UID -m -g $GID chronivaro; else usermod -g $GID $(getent passwd $UID | cut -d: -f1); fi

# Create the application and runtime directories and set ownership
RUN mkdir -p /app /chronivaro-runtime && chown -R $UID:$GID /app /chronivaro-runtime && chmod 775 /chronivaro-runtime

WORKDIR /app

COPY chronivaro-app/target/chronivaro.jar /app/chronivaro.jar
COPY chronivaro-app/target/lib /app/lib

# Ensure the application files are owned by chronivaro
RUN chown -R $UID:$GID /app

USER $UID

EXPOSE 8080
ENV PORT=8080
ENV CHRONIVARO_PORT=8080
ENV STROLCH_PATH=/chronivaro-runtime
ENV STROLCH_ENVIRONMENT=dev

ENTRYPOINT ["java", "-jar", "/app/chronivaro.jar"]
