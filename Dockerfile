FROM openjdk:17-slim as build

WORKDIR /app
COPY . .

RUN ./gradlew :server:installDist --scan

FROM openjdk:17-slim

ENV PORT=4394

COPY --from=build app/server/build/install/server ./server
VOLUME ./server/vol
EXPOSE $PORT

ENTRYPOINT ["/bin/bash", "-c", "./server/bin/server"]