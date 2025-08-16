#!/bin/sh
export MAVEN_OPTS="-Xms2g -Xmx4g"
mvn clean -DskipTests package -T 2C 
