#!/bin/sh
# Get the short commit hash, take full hash then get just the first 7 characters
COMMIT_HASH=$(git rev-parse --short HEAD)
# Read the current version from pom.xml
CURRENT_VERSION=$(grep -m 1 "<revision>" pom.xml | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')
# Create new version with commit hash and human readable timestamp
TIMESTAMP=$(date +"%b-%d-%Y_%H-%M")
NEW_VERSION="${CURRENT_VERSION}-${COMMIT_HASH}-${TIMESTAMP}-dev"

# Set up the JDK 21 toolchain and install integration APIs not resolvable from upstream (Dynmap, BlockProt)
"$(dirname "$0")/scripts/setup-build-env.sh"

# Build the plugin
export MAVEN_OPTS="-Xms8g -Xmx16g"
mvn -Drevision="$NEW_VERSION" clean package -T 8C #-o

# Copy latest plugin in
rm ../paper-test-26.1.2/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-26.1.2/plugins

cd ../paper-test-26.1.2/
java -Xms4G -Xmx8G -jar paper*.jar --nogui
