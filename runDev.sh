#!/bin/sh
# Get the short commit hash, take full hash then get just the first 7 characters
COMMIT_HASH=$(git rev-parse --short HEAD)
# Read the current version from pom.xml
CURRENT_VERSION=$(grep -m 1 "<revision>" pom.xml | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')
# Create new version with commit hash and human readable timestamp
TIMESTAMP=$(date +"%b-%d-%Y_%H-%M")
NEW_VERSION="${CURRENT_VERSION}-${COMMIT_HASH}-${TIMESTAMP}-dev"

# Ensure Maven toolchain for JDK 21 is present so tests run with MockBukkit
mkdir -p ~/.m2
cat > ~/.m2/toolchains.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>21</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>${JAVA_HOME}</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
EOF

# Build the plugin
export MAVEN_OPTS="-Xms8g -Xmx16g"
mvn -Drevision="$NEW_VERSION" clean package -T 8C #-o

# Copy latest plugin in
rm ../paper-test-1.21.8/plugins/Shop-*.jar 
cp target/Shop-*.jar ../paper-test-1.21.8/plugins

cd ../paper-test-1.21.8/
java -Xms4G -Xmx8G -jar paper*.jar --nogui
