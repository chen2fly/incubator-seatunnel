/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.format.json;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.api.table.type.SqlType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Map;

public class RowToJsonConverters implements Serializable {

    private static final long serialVersionUID = 6988876688930916940L;

    public RowToJsonConverter createConverter(SeaTunnelDataType<?> type) {
        return wrapIntoNullableConverter(createNotNullConverter(type));
    }

    private RowToJsonConverter wrapIntoNullableConverter(RowToJsonConverter converter) {
        return (mapper, reuse, value) -> {
            if (value == null) {
                return mapper.getNodeFactory().nullNode();
            }
            return converter.convert(mapper, reuse, value);
        };
    }

    private RowToJsonConverter createNotNullConverter(SeaTunnelDataType<?> type) {
        SqlType sqlType = type.getSqlType();
        switch (sqlType) {
            case ROW:
                return createRowConverter((SeaTunnelRowType) type);
            case NULL:
                return (mapper, reuse, value) -> null;
            case BOOLEAN:
                return (mapper, reuse, value) -> mapper.getNodeFactory().booleanNode((Boolean) value);
            case TINYINT:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((byte) value);
            case SMALLINT:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((short) value);
            case INT:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((int) value);
            case BIGINT:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((long) value);
            case FLOAT:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((float) value);
            case DOUBLE:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((double) value);
            case DECIMAL:
                return (mapper, reuse, value) -> mapper.getNodeFactory().numberNode((BigDecimal) value);
            case BYTES:
                return (mapper, reuse, value) -> mapper.getNodeFactory().binaryNode((byte[]) value);
            case STRING:
                return (mapper, reuse, value) -> mapper.getNodeFactory().textNode((String) value);
            case DATE:
                return (mapper, reuse, value) -> mapper.getNodeFactory().textNode(ISO_LOCAL_DATE.format((LocalDate) value));
            case TIME:
                return (mapper, reuse, value) -> mapper.getNodeFactory().textNode(TimeFormat.TIME_FORMAT.format((LocalTime) value));
            case TIMESTAMP:
                return (mapper, reuse, value) -> mapper.getNodeFactory().textNode(ISO_LOCAL_DATE_TIME.format((LocalDateTime) value));
            case ARRAY:
                return createArrayConverter((ArrayType) type);
            case MAP:
                MapType mapType = (MapType) type;
                return createMapConverter(mapType.toString(), mapType.getKeyType(), mapType.getValueType());
            default:
                throw new UnsupportedOperationException("unsupported parse type: " + type);
        }
    }

    private RowToJsonConverter createRowConverter(SeaTunnelRowType rowType) {
        final RowToJsonConverter[] fieldConverters =
                Arrays.stream(rowType.getFieldTypes())
                        .map(this::createConverter)
                        .toArray(RowToJsonConverter[]::new);
        final String[] fieldNames = rowType.getFieldNames();
        final int arity = fieldNames.length;

        return (mapper, reuse, value) -> {
            ObjectNode node;

            // reuse could be a NullNode if last record is null.
            if (reuse == null || reuse.isNull()) {
                node = mapper.createObjectNode();
            } else {
                node = (ObjectNode) reuse;
            }

            for (int i = 0; i < arity; i++) {
                String fieldName = fieldNames[i];
                SeaTunnelRow row = (SeaTunnelRow) value;
                node.set(fieldName, fieldConverters[i].convert(
                        mapper, node.get(fieldName), row.getField(i)));
            }

            return node;
        };
    }

    private RowToJsonConverter createArrayConverter(ArrayType arrayType) {
        final RowToJsonConverter elementConverter = createConverter(arrayType.getElementType());
        return (mapper, reuse, value) -> {
            ArrayNode node;

            // reuse could be a NullNode if last record is null.
            if (reuse == null || reuse.isNull()) {
                node = mapper.createArrayNode();
            } else {
                node = (ArrayNode) reuse;
                node.removeAll();
            }

            Object[] arrayData = (Object[]) value;
            int numElements = arrayData.length;
            for (int i = 0; i < numElements; i++) {
                Object element = arrayData[i];
                node.add(elementConverter.convert(mapper, null, element));
            }

            return node;
        };
    }

    private RowToJsonConverter createMapConverter(String typeSummary, SeaTunnelDataType<?> keyType, SeaTunnelDataType<?> valueType) {
        if (!SqlType.STRING.equals(keyType.getSqlType())) {
            throw new UnsupportedOperationException(
                    "JSON format doesn't support non-string as key type of map. The type is: " + typeSummary);
        }

        final RowToJsonConverter valueConverter = createConverter(valueType);
        return (mapper, reuse, value) -> {
            ObjectNode node;

            // reuse could be a NullNode if last record is null.
            if (reuse == null || reuse.isNull()) {
                node = mapper.createObjectNode();
            } else {
                node = (ObjectNode) reuse;
                node.removeAll();
            }

            Map<String, ?> mapData = (Map) value;
            for (Map.Entry<String, ?> entry : mapData.entrySet()) {
                String fieldName = entry.getKey();
                node.set(fieldName, valueConverter.convert(mapper, node.get(fieldName), entry.getValue()));
            }

            return node;
        };
    }

    public interface RowToJsonConverter extends Serializable {
        JsonNode convert(ObjectMapper mapper, JsonNode reuse, Object value);
    }
}
