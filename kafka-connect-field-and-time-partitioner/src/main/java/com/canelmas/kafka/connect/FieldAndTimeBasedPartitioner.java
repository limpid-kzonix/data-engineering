/*
 * Copyright (C) 2020 Can Elmas <canelm@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.canelmas.kafka.connect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.confluent.connect.storage.common.StorageCommonConfig;
import io.confluent.connect.storage.partitioner.TimeBasedPartitioner;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.sink.SinkRecord;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static io.confluent.connect.storage.partitioner.PartitionerConfig.*;

//
public final class FieldAndTimeBasedPartitioner<T> extends TimeBasedPartitioner<T> {

    public static final String PARTITION_FIELD_FORMAT_PATH_CONFIG = "partition.field.format.path";
    public static final String PARTITION_FIELD_FORMAT_PATH_DOC = "Whether directory labels should be included when partitioning for custom fields e.g. " +
        "whether this 'orgId=XXXX/appId=ZZZZ/customField=YYYY' or this 'XXXX/ZZZZ/YYYY'.";
    public static final String PARTITION_FIELD_FORMAT_PATH_DISPLAY = "Partition Field Format Path";
    public static final boolean PARTITION_FIELD_FORMAT_PATH_DEFAULT = true;
    private static final Logger log = LoggerFactory.getLogger(FieldAndTimeBasedPartitioner.class);
    private PartitionFieldExtractor fieldExtractor;

    protected void init(long partitionDurationMs, String pathFormat, Locale locale, DateTimeZone timeZone, Map<String, Object> config) {
        super.init(partitionDurationMs, pathFormat, locale, timeZone, config);

        final List<String> fieldNames = (List<String>) config.get(PARTITION_FIELD_NAME_CONFIG);
        // option value is parse as string all other type is cast as string by kafka connect need to parse by ourselves
        final boolean formatPath = Boolean.parseBoolean((String) config.getOrDefault(PARTITION_FIELD_FORMAT_PATH_CONFIG, PARTITION_FIELD_FORMAT_PATH_DEFAULT));

        this.fieldExtractor = new PartitionFieldExtractor(fieldNames, formatPath);
    }

    public String encodePartition(final SinkRecord sinkRecord, final long nowInMillis) {
        final String partitionsForTimestamp = super.encodePartition(sinkRecord, nowInMillis);
        final String partitionsForFields = this.fieldExtractor.extract(sinkRecord);
        final String partition = String.join(this.delim, partitionsForFields, partitionsForTimestamp);

        log.info("Encoded partition : {}", partition);

        return partition;
    }

    public String encodePartition(final SinkRecord sinkRecord) {
        final String partitionsForTimestamp = super.encodePartition(sinkRecord);
        final String partitionsForFields = this.fieldExtractor.extract(sinkRecord);
        final String partition = String.join(this.delim, partitionsForFields, partitionsForTimestamp);

        log.info("Encoded partition : {}", partition);

        return partition;
    }

    public static class PartitionFieldExtractor {

        private static final String DELIMITER_EQ = "=";

        private final boolean formatPath;
        private final List<String> fieldNames;

        private final JsonParser jsonParser;

        private final Map<String, List<String>> keys;

        PartitionFieldExtractor(final List<String> fieldNames, final boolean formatPath) {
            this.fieldNames = fieldNames;
            this.formatPath = formatPath;
            this.jsonParser = new JsonParser();
            this.keys = fieldNames.stream().map(fieldName -> {
                if (fieldName.contains(".")) {
                    final String[] split = fieldName.split("\\.");
                    return Map.entry(fieldName, Arrays.asList(split));
                } else {
                    return Map.entry(fieldName, List.of(fieldName));
                }

            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        }

        private static String listToString(final List<String> list) {
            return String.join(", ", list);
        }

        public String extract(final ConnectRecord<?> record) {
            final Object value = record.value();
            final JsonElement rootElement = jsonParser.parse(value.toString());
            final StringBuilder builder = new StringBuilder();

            for (final Map.Entry<String, List<String>> entry : keys.entrySet()) {
                JsonElement element = rootElement;
                final String fieldName = entry.getKey();
                final List<String> fieldPathParts = entry.getValue();

                for (final String fieldPart : fieldPathParts) {
                    if (element == null) {
                        log.error("Field {} is not found in the record", fieldName);
                        break;
                    }
                    element = element.getAsJsonObject().get(fieldPart);
                }

                if (builder.length() != 0) {
                    builder.append(StorageCommonConfig.DIRECTORY_DELIM_DEFAULT);
                }

                String elementAsString;
                if (element != null) {
                    log.info("Extracted field {} with value {}", fieldName, element);
                    try {
                        elementAsString = element.getAsString();
                    } catch (Exception e) {
                        log.error("Field {} is not a string, trying to convert to number. {}", fieldName, element, e);
                        elementAsString = "unknown";
                    }
                } else {
                    elementAsString = "unknown";
                }
                if (formatPath) {
                    builder.append(String.join(DELIMITER_EQ, fieldName, elementAsString));
                } else {
                    builder.append(elementAsString);
                }
            }
            return builder.toString();

        }
    }

}
