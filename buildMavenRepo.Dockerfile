# Stage 1: Build older versions with jdk13
FROM adoptopenjdk:13-jdk-hotspot as jdk13

# Set working directory
WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y wget git

# Download the latest BuildTools.jar from SpigotMC
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

RUN java -jar BuildTools.jar --rev 1.14.4 && \
    java -jar BuildTools.jar --rev 1.15.2 && \
    java -jar BuildTools.jar --rev 1.16.1 && \
    java -jar BuildTools.jar --rev 1.16.3 && \
    java -jar BuildTools.jar --rev 1.16.5 && \
    rm -r /app/*

# Stage 2: Build older versions with JDK17
FROM openjdk:17-slim as jdk17

COPY --from=jdk13 /root/.m2 /root/.m2

WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y wget git

# Download the latest BuildTools.jar from SpigotMC
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

# Build our Spigot jar files
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

# Stage 3: Build current versions with JDK21
FROM openjdk:21-slim as jdk21

COPY --from=jdk17 /root/.m2 /root/.m2

WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y wget git

# Download the latest BuildTools.jar from SpigotMC
RUN wget -O BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

# Build our Spigot jar files
RUN java -jar BuildTools.jar --remapped --rev 1.20.6 && \
    java -jar BuildTools.jar --remapped --rev 1.21 && \
    java -jar BuildTools.jar --remapped --rev 1.21.1 && \
    rm -r /app/*

# Keep container running
CMD ["sleep", "infinity"]


