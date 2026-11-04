# ---------------------------------------------------------------
#  Receita para publicar o MaxRevolution PDV na internet (Render etc.)
#  Etapa 1: compila o Java e baixa o driver do PostgreSQL.
#  Etapa 2: monta uma imagem pequena só com o necessário para rodar.
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out && javac -encoding UTF-8 -d out -sourcepath src src/pdv/Main.java
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar /app/postgresql.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY --from=build /app/postgresql.jar ./postgresql.jar
COPY web ./web
# A plataforma informa PORT e DATABASE_URL por variáveis de ambiente
CMD ["java", "-Xmx300m", "-Dfile.encoding=UTF-8", "-cp", "out:postgresql.jar", "pdv.Main"]
