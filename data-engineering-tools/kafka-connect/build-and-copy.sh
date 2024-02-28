#!/usr/bin/env bash

p=$PWD
# Move one directory up
cd ../..

echo "$(ls -1)"

# Find all JAR files within subdirectories of `flink-kafka-*` directory
flink_jars=$(find ./kafka-connect-* -path "*.jar")

# Check if any JAR files were found
if [[ -z "$flink_jars" ]]; then
  echo "No JAR files found in the specified location."
  exit 1
fi

# Copy each JAR file to the current directory
for jar_file in $flink_jars; do
  cp "$jar_file" $p
  echo "Copied: $jar_file"
done

echo "All JAR files copied successfully!"