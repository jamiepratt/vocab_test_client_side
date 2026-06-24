FROM clojure:temurin-21-tools-deps

WORKDIR /app

COPY deps.edn ./
RUN clojure -P -M:server

COPY src src
COPY resources resources

ENV PORT=8080
EXPOSE 8080

CMD ["clojure", "-M:server"]
