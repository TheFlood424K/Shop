# Stage 1: Build older versions with jdk13
FROM adoptopenjdk:13-jdk-hotspot AS jdk13

WORKDIR /app

RUN apt-get update && apt-get install -y wget git > /dev/null 2>&1

RUN wget -q -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN for rev in 1.14.4 1.15.2 1.16.1 1.16.3 1.16.5; do \
      echo "Building Spigot $rev..." && \
      java -jar BuildTools.jar --rev $rev > build.log 2>&1 || (cat build.log && exit 1); \
    done && rm -r /app/*

# Stage 2: Build older versions with JDK 17 (Temurin)
FROM eclipse-temurin:17-jdk-jammy AS jdk17

COPY --from=jdk13 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git > /dev/null 2>&1

RUN wget -q -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN for rev in 1.17.1 1.18 1.18.2 1.19 1.19.3 1.19.4 1.20 1.20.2 1.20.4; do \
      echo "Building Spigot $rev (remapped)..." && \
      java -jar BuildTools.jar --remapped --rev $rev > build.log 2>&1 || (cat build.log && exit 1); \
    done && rm -r /app/*

# Stage 3: Build versions with JDK 21 (Temurin)
FROM eclipse-temurin:21-jdk-jammy AS jdk21

COPY --from=jdk17 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git > /dev/null 2>&1

RUN wget -q -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN for rev in 1.20.6 1.21 1.21.1; do \
      echo "Building Spigot $rev (remapped)..." && \
      java -jar BuildTools.jar --remapped --rev $rev > build.log 2>&1 || (cat build.log && exit 1); \
    done && rm -r /app/*

# Stage 4: Build Minecraft 26.2 with JDK 25 (Temurin)
FROM eclipse-temurin:25-jdk-jammy AS jdk25

COPY --from=jdk21 /root/.m2 /root/.m2

WORKDIR /app

RUN apt-get update && apt-get install -y wget git > /dev/null 2>&1

RUN wget -q -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN echo "Building Spigot 26.2 (remapped)..." && \
    java -jar BuildTools.jar --remapped --rev 26.2 > build.log 2>&1 || (cat build.log && exit 1) && \
    rm -r /app/*

CMD ["sleep", "infinity"]
