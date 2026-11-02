# Stage 1: Build older versions with jdk13
FROM adoptopenjdk:13-jdk-hotspot AS jdk13

WORKDIR /app

RUN apt-get update && apt-get install -y wget git

RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN java -jar BuildTools.jar --rev 1.14.4 && \
    java -jar BuildTools.jar --rev 1.15.2 && \
    java -jar BuildTools.jar --rev 1.16.1 && \
    java -jar BuildTools.jar --rev 1.16.3 && \
    java -jar BuildTools.jar --rev 1.16.5 && \
    rm -r /app/*

# Stage 2: Build older versions with JDK 17 (Temurin)
FROM eclipse-temurin:17-jdk-jammy AS jdk17

COPY --from=jdk13 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git

RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN java -jar BuildTools.jar --remapped --rev 1.17.1 && \
    java -jar BuildTools.jar --remapped --rev 1.18 && \
    java -jar BuildTools.jar --remapped --rev 1.18.2 && \
    java -jar BuildTools.jar --remapped --rev 1.19 && \
    java -jar BuildTools.jar --remapped --rev 1.19.3 && \
    java -jar BuildTools.jar --remapped --rev 1.19.4 && \
    java -jar BuildTools.jar --remapped --rev 1.20 && \
    java -jar BuildTools.jar --remapped --rev 1.20.2 && \
    java -jar BuildTools.jar --remapped --rev 1.20.4 && \
    rm -r /app/*

# Stage 3: Build versions with JDK 21 (Temurin)
FROM eclipse-temurin:21-jdk-jammy AS jdk21

COPY --from=jdk17 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git

RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN java -jar BuildTools.jar --remapped --rev 1.20.6 && \
    java -jar BuildTools.jar --remapped --rev 1.21 && \
    java -jar BuildTools.jar --remapped --rev 1.21.1 && \
    rm -r /app/*

# Stage 4: Build Minecraft 26.2 with JDK 25 (Temurin)
FROM eclipse-temurin:25-jdk-jammy AS jdk25

COPY --from=jdk21 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git

RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN java -jar BuildTools.jar --remapped --rev 26.2 && \
    rm -r /app/*

CMD ["sleep", "infinity"]
