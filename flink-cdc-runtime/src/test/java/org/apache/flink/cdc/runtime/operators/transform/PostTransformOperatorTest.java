/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.runtime.operators.transform;

import org.apache.flink.cdc.common.data.DecimalData;
import org.apache.flink.cdc.common.data.TimestampData;
import org.apache.flink.cdc.common.data.binary.BinaryStringData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.source.SupportedMetadataColumn;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.common.types.RowType;
import org.apache.flink.cdc.runtime.testutils.operators.RegularEventOperatorTestHarness;
import org.apache.flink.cdc.runtime.typeutils.BinaryRecordDataGenerator;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;

/** Unit tests for the {@link PostTransformOperator}. */
public class PostTransformOperatorTest {
    private static final TableId CUSTOMERS_TABLEID =
            TableId.tableId("my_company", "my_branch", "customers");
    private static final Schema CUSTOMERS_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("col2", DataTypes.STRING())
                    .physicalColumn("col12", DataTypes.STRING())
                    .primaryKey("col1")
                    .build();

    private static final TableId DATATYPE_TABLEID =
            TableId.tableId("my_company", "my_branch", "data_types");
    private static final Schema DATATYPE_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("colString", DataTypes.STRING().notNull())
                    .physicalColumn("colBoolean", DataTypes.BOOLEAN())
                    .physicalColumn("colTinyint", DataTypes.TINYINT())
                    .physicalColumn("colSmallint", DataTypes.SMALLINT())
                    .physicalColumn("colInt", DataTypes.INT())
                    .physicalColumn("colBigint", DataTypes.BIGINT())
                    .physicalColumn("colDate", DataTypes.DATE())
                    .physicalColumn("colTime", DataTypes.TIME())
                    .physicalColumn("colTimestamp", DataTypes.TIMESTAMP())
                    .physicalColumn("colFloat", DataTypes.FLOAT())
                    .physicalColumn("colDouble", DataTypes.DOUBLE())
                    .physicalColumn("colDecimal", DataTypes.DECIMAL(6, 2))
                    .primaryKey("colString")
                    .build();

    private static final TableId METADATA_TABLEID =
            TableId.tableId("my_company", "my_branch", "metadata_table");
    private static final Schema METADATA_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .primaryKey("col1")
                    .build();
    private static final Schema EXPECTED_METADATA_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("identifier_name", DataTypes.STRING())
                    .physicalColumn("__namespace_name__", DataTypes.STRING().notNull())
                    .physicalColumn("__schema_name__", DataTypes.STRING().notNull())
                    .physicalColumn("__table_name__", DataTypes.STRING().notNull())
                    .primaryKey("col1")
                    .build();

    private static final TableId METADATA_AS_TABLEID =
            TableId.tableId("my_company", "my_branch", "metadata_as_table");
    private static final Schema METADATA_AS_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("sid", DataTypes.INT().notNull())
                    .physicalColumn("name", DataTypes.STRING())
                    .physicalColumn("name_upper", DataTypes.STRING())
                    .physicalColumn("tbname", DataTypes.STRING().notNull())
                    .primaryKey("sid")
                    .build();

    private static final TableId TIMESTAMP_TABLEID =
            TableId.tableId("my_company", "my_branch", "timestamp_table");
    private static final Schema TIMESTAMP_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("time_equal", DataTypes.INT())
                    .physicalColumn("timestamp_equal", DataTypes.INT())
                    .physicalColumn("date_equal", DataTypes.INT())
                    .primaryKey("col1")
                    .build();

    private static final TableId TIMESTAMPDIFF_TABLEID =
            TableId.tableId("my_company", "my_branch", "timestampdiff_table");
    private static final Schema TIMESTAMPDIFF_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("second_diff", DataTypes.INT())
                    .physicalColumn("minute_diff", DataTypes.INT())
                    .physicalColumn("hour_diff", DataTypes.INT())
                    .physicalColumn("day_diff", DataTypes.INT())
                    .primaryKey("col1")
                    .build();

    private static final TableId NULL_TABLEID =
            TableId.tableId("my_company", "my_branch", "data_null");
    private static final Schema NULL_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("colString", DataTypes.STRING())
                    .physicalColumn("nullInt", DataTypes.INT())
                    .physicalColumn("nullBoolean", DataTypes.BOOLEAN())
                    .physicalColumn("nullTinyint", DataTypes.TINYINT())
                    .physicalColumn("nullSmallint", DataTypes.SMALLINT())
                    .physicalColumn("nullBigint", DataTypes.BIGINT())
                    .physicalColumn("nullFloat", DataTypes.FLOAT())
                    .physicalColumn("nullDouble", DataTypes.DOUBLE())
                    .physicalColumn("nullChar", DataTypes.STRING())
                    .physicalColumn("nullVarchar", DataTypes.STRING())
                    .physicalColumn("nullDecimal", DataTypes.DECIMAL(4, 2))
                    .physicalColumn("nullTimestamp", DataTypes.TIMESTAMP(3))
                    .primaryKey("col1")
                    .build();

    private static final TableId CAST_TABLEID =
            TableId.tableId("my_company", "my_branch", "data_cast");
    private static final Schema CAST_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("castInt", DataTypes.INT())
                    .physicalColumn("castBoolean", DataTypes.BOOLEAN())
                    .physicalColumn("castTinyint", DataTypes.TINYINT())
                    .physicalColumn("castSmallint", DataTypes.SMALLINT())
                    .physicalColumn("castBigint", DataTypes.BIGINT())
                    .physicalColumn("castFloat", DataTypes.FLOAT())
                    .physicalColumn("castDouble", DataTypes.DOUBLE())
                    .physicalColumn("castChar", DataTypes.STRING())
                    .physicalColumn("castVarchar", DataTypes.STRING())
                    .physicalColumn("castDecimal", DataTypes.DECIMAL(4, 2))
                    .physicalColumn("castTimestamp", DataTypes.TIMESTAMP(3))
                    .primaryKey("col1")
                    .build();

    private static final TableId TIMEZONE_TABLEID =
            TableId.tableId("my_company", "my_branch", "timezone_table");
    private static final Schema TIMEZONE_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("datetime", DataTypes.STRING())
                    .primaryKey("col1")
                    .build();

    private static final TableId CONDITION_TABLEID =
            TableId.tableId("my_company", "my_branch", "condition_table");
    private static final Schema CONDITION_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.STRING().notNull())
                    .physicalColumn("condition_result", DataTypes.BOOLEAN())
                    .primaryKey("col1")
                    .build();

    private static final TableId REDUCE_TABLEID =
            TableId.tableId("my_company", "my_branch", "reduce_table");

    private static final Schema REDUCE_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.STRING().notNull())
                    .physicalColumn("age", DataTypes.INT())
                    .physicalColumn("ref1", DataTypes.STRING())
                    .physicalColumn("ref2", DataTypes.INT())
                    .primaryKey("id")
                    .partitionKey("id")
                    .options(ImmutableMap.of("key1", "value1", "key2", "value2"))
                    .build();

    private static final Schema EXPECTED_REDUCE_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.STRING().notNull())
                    .physicalColumn("uid", DataTypes.STRING())
                    .physicalColumn("newage", DataTypes.INT())
                    .physicalColumn("ref1", DataTypes.STRING())
                    .physicalColumn("seventeen", DataTypes.INT())
                    .primaryKey("id")
                    .partitionKey("id")
                    .options(ImmutableMap.of("key1", "value1", "key2", "value2"))
                    .build();

    private static final TableId WILDCARD_TABLEID =
            TableId.tableId("my_company", "my_branch", "wildcard_table");

    private static final Schema WILDCARD_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.STRING().notNull())
                    .physicalColumn("age", DataTypes.INT())
                    .physicalColumn("name", DataTypes.STRING())
                    .primaryKey("id")
                    .partitionKey("id")
                    .options(ImmutableMap.of("key1", "value1", "key2", "value2"))
                    .build();

    private static final Schema EXPECTED_WILDCARD_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.STRING().notNull())
                    .physicalColumn("age", DataTypes.INT())
                    .physicalColumn("name", DataTypes.STRING())
                    .physicalColumn("newage", DataTypes.INT())
                    .primaryKey("id")
                    .partitionKey("id")
                    .options(ImmutableMap.of("key1", "value1", "key2", "value2"))
                    .build();

    private static final TableId COLUMN_SQUARE_TABLE =
            TableId.tableId("my_company", "my_branch", "column_square");
    private static final Schema COLUMN_SQUARE_SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("col1", DataTypes.INT().notNull())
                    .physicalColumn("col2", DataTypes.INT())
                    .physicalColumn("square_col2", DataTypes.INT())
                    .primaryKey("col1")
                    .build();

    @Test
    void testDataChangeEventTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                CUSTOMERS_TABLEID.identifier(),
                                "*, concat(col1,col2) col12",
                                "col1 = '1'")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(CUSTOMERS_TABLEID, CUSTOMERS_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) CUSTOMERS_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("2"), null
                                }));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("2"),
                                    new BinaryStringData("12")
                                }));
        // Insert will be ignored
        DataChangeEvent insertEventIgnored =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("2"), new BinaryStringData("2"), null
                                }));
        // Update
        DataChangeEvent updateEvent =
                DataChangeEvent.updateEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("2"), null
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("3"), null
                                }));
        DataChangeEvent updateEventExpect =
                DataChangeEvent.updateEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("2"),
                                    new BinaryStringData("12")
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("3"),
                                    new BinaryStringData("13")
                                }));

        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(CUSTOMERS_TABLEID, CUSTOMERS_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transform.processElement(new StreamRecord<>(updateEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(updateEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testDataChangeEventTransformTwice() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                CUSTOMERS_TABLEID.identifier(),
                                "*, concat(col1, '1') col12",
                                "col1 = '1'")
                        .addTransform(
                                CUSTOMERS_TABLEID.identifier(),
                                "*, concat(col1, '2') col12",
                                "col1 = '2'")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(CUSTOMERS_TABLEID, CUSTOMERS_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) CUSTOMERS_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("2"), null
                                }));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("2"),
                                    new BinaryStringData("11")
                                }));
        // Insert
        DataChangeEvent insertEvent2 =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("2"), new BinaryStringData("2"), null
                                }));
        DataChangeEvent insertEvent2Expect =
                DataChangeEvent.insertEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("2"),
                                    new BinaryStringData("2"),
                                    new BinaryStringData("22")
                                }));
        // Update
        DataChangeEvent updateEvent =
                DataChangeEvent.updateEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("2"), null
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"), new BinaryStringData("3"), null
                                }));
        DataChangeEvent updateEventExpect =
                DataChangeEvent.updateEvent(
                        CUSTOMERS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("2"),
                                    new BinaryStringData("11")
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("3"),
                                    new BinaryStringData("11")
                                }));

        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(CUSTOMERS_TABLEID, CUSTOMERS_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transform.processElement(new StreamRecord<>(insertEvent2));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEvent2Expect));
        transform.processElement(new StreamRecord<>(updateEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(updateEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testDataChangeEventTransformProjectionDataTypeConvert() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                DATATYPE_TABLEID.identifier(),
                                "*",
                                null,
                                null,
                                null,
                                null,
                                null,
                                new SupportedMetadataColumn[0])
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(DATATYPE_TABLEID, DATATYPE_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) DATATYPE_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        DATATYPE_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("3.14"),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Integer(1),
                                    new Long(1),
                                    new Integer(1704471599),
                                    new Integer(1704471599),
                                    TimestampData.fromMillis(1704471599),
                                    new Float(3.14f),
                                    new Double(3.14d),
                                    DecimalData.fromBigDecimal(new BigDecimal(3.14), 6, 2),
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(DATATYPE_TABLEID, DATATYPE_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEvent));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testMetadataTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                METADATA_TABLEID.identifier(),
                                "*, __namespace_name__ || '.' || __schema_name__ || '.' || __table_name__ identifier_name, __namespace_name__, __schema_name__, __table_name__",
                                " __table_name__ = 'metadata_table' ")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(METADATA_TABLEID, METADATA_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) METADATA_SCHEMA.toRowDataType()));
        BinaryRecordDataGenerator expectedRecordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) EXPECTED_METADATA_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        METADATA_TABLEID,
                        recordDataGenerator.generate(new Object[] {new BinaryStringData("1")}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        METADATA_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("my_company.my_branch.metadata_table"),
                                    new BinaryStringData("my_company"),
                                    new BinaryStringData("my_branch"),
                                    new BinaryStringData("metadata_table")
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(METADATA_TABLEID, EXPECTED_METADATA_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testMetadataASTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                METADATA_AS_TABLEID.identifier(),
                                "sid, name, UPPER(name) as name_upper, __table_name__ as tbname",
                                "sid < 3")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(METADATA_AS_TABLEID, METADATA_AS_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) METADATA_AS_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        METADATA_AS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {1, new BinaryStringData("abc"), null, null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        METADATA_AS_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    1,
                                    new BinaryStringData("abc"),
                                    new BinaryStringData("ABC"),
                                    new BinaryStringData("metadata_as_table")
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(METADATA_AS_TABLEID, METADATA_AS_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testSoftDeleteTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                METADATA_TABLEID.identifier(),
                                "*, __namespace_name__ || '.' || __schema_name__ || '.' || __table_name__ identifier_name, __namespace_name__, __schema_name__, __table_name__, __data_event_type__",
                                " __table_name__ = 'metadata_table' ",
                                "",
                                "",
                                "",
                                "SOFT_DELETE",
                                new SupportedMetadataColumn[0])
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        Schema expectedSchema =
                Schema.newBuilder()
                        .physicalColumn("col1", DataTypes.STRING().notNull())
                        .physicalColumn("identifier_name", DataTypes.STRING())
                        .physicalColumn("__namespace_name__", DataTypes.STRING().notNull())
                        .physicalColumn("__schema_name__", DataTypes.STRING().notNull())
                        .physicalColumn("__table_name__", DataTypes.STRING().notNull())
                        .physicalColumn("__data_event_type__", DataTypes.STRING().notNull())
                        .primaryKey("col1")
                        .build();

        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(METADATA_TABLEID, METADATA_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) METADATA_SCHEMA.toRowDataType()));
        BinaryRecordDataGenerator expectedRecordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) expectedSchema.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        METADATA_TABLEID,
                        recordDataGenerator.generate(new Object[] {new BinaryStringData("1")}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        METADATA_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("my_company.my_branch.metadata_table"),
                                    new BinaryStringData("my_company"),
                                    new BinaryStringData("my_branch"),
                                    new BinaryStringData("metadata_table"),
                                    new BinaryStringData("+I")
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(new CreateTableEvent(METADATA_TABLEID, expectedSchema)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));

        // Delete
        DataChangeEvent deleteEvent =
                DataChangeEvent.deleteEvent(
                        METADATA_TABLEID,
                        recordDataGenerator.generate(new Object[] {new BinaryStringData("1")}));
        DataChangeEvent deleteEventExpect =
                DataChangeEvent.insertEvent(
                        METADATA_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("my_company.my_branch.metadata_table"),
                                    new BinaryStringData("my_company"),
                                    new BinaryStringData("my_branch"),
                                    new BinaryStringData("metadata_table"),
                                    new BinaryStringData("-D")
                                }));
        transform.processElement(new StreamRecord<>(deleteEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(deleteEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testDataChangeEventTransformWithDuplicateColumns() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                COLUMN_SQUARE_TABLE.identifier(),
                                "col1, col2, col2 * col2 as square_col2",
                                "col2 < 3 OR col2 > 5")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(COLUMN_SQUARE_TABLE, COLUMN_SQUARE_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) COLUMN_SQUARE_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        COLUMN_SQUARE_TABLE,
                        recordDataGenerator.generate(new Object[] {1, 1, null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        COLUMN_SQUARE_TABLE, recordDataGenerator.generate(new Object[] {1, 1, 1}));

        DataChangeEvent insertEvent2 =
                DataChangeEvent.insertEvent(
                        COLUMN_SQUARE_TABLE,
                        recordDataGenerator.generate(new Object[] {6, 6, null}));
        DataChangeEvent insertEventExpect2 =
                DataChangeEvent.insertEvent(
                        COLUMN_SQUARE_TABLE, recordDataGenerator.generate(new Object[] {6, 6, 36}));

        DataChangeEvent insertEvent3 =
                DataChangeEvent.insertEvent(
                        COLUMN_SQUARE_TABLE,
                        recordDataGenerator.generate(new Object[] {4, 4, null}));

        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(COLUMN_SQUARE_TABLE, COLUMN_SQUARE_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transform.processElement(new StreamRecord<>(insertEvent2));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect2));
        transform.processElement(new StreamRecord<>(insertEvent3));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isNull();
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testTimestampTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                TIMESTAMP_TABLEID.identifier(),
                                "col1, IF(LOCALTIME = CURRENT_TIME, 1, 0) as time_equal,"
                                        + " IF(DATE_FORMAT(CAST(CURRENT_TIMESTAMP AS TIMESTAMP), 'yyyy-MM-dd HH:mm:ss') = DATE_FORMAT(CAST(NOW() AS TIMESTAMP), 'yyyy-MM-dd HH:mm:ss'), 1, 0) as timestamp_equal,"
                                        + " IF(TO_DATE(DATE_FORMAT(LOCALTIMESTAMP, 'yyyy-MM-dd')) = CURRENT_DATE, 1, 0) as date_equal",
                                "LOCALTIMESTAMP = CAST(CURRENT_TIMESTAMP AS TIMESTAMP)")
                        .addTimezone("UTC")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(TIMESTAMP_TABLEID, TIMESTAMP_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) TIMESTAMP_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        TIMESTAMP_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), null, null, null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        TIMESTAMP_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), 1, 1, 1}));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(TIMESTAMP_TABLEID, TIMESTAMP_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testTimestampDiffTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                TIMESTAMPDIFF_TABLEID.identifier(),
                                "col1, TIMESTAMP_DIFF('SECOND', LOCALTIMESTAMP, CAST(CURRENT_TIMESTAMP AS TIMESTAMP)) as second_diff,"
                                        + " TIMESTAMP_DIFF('MINUTE', LOCALTIMESTAMP, CAST(CURRENT_TIMESTAMP AS TIMESTAMP)) as minute_diff,"
                                        + " TIMESTAMP_DIFF('HOUR', LOCALTIMESTAMP, CAST(CURRENT_TIMESTAMP AS TIMESTAMP)) as hour_diff,"
                                        + " TIMESTAMP_DIFF('DAY', LOCALTIMESTAMP, CAST(CURRENT_TIMESTAMP AS TIMESTAMP)) as day_diff",
                                "col1='1'")
                        .addTransform(
                                TIMESTAMPDIFF_TABLEID.identifier(),
                                "col1, TIMESTAMP_DIFF('SECOND', LOCALTIMESTAMP, CAST(NOW() AS TIMESTAMP)) as second_diff,"
                                        + " TIMESTAMP_DIFF('MINUTE', LOCALTIMESTAMP, CAST(NOW() AS TIMESTAMP)) as minute_diff,"
                                        + " TIMESTAMP_DIFF('HOUR', LOCALTIMESTAMP, CAST(NOW() AS TIMESTAMP)) as hour_diff,"
                                        + " TIMESTAMP_DIFF('DAY', LOCALTIMESTAMP, CAST(NOW() AS TIMESTAMP)) as day_diff",
                                "col1='2'")
                        .addTimezone("Asia/Shanghai")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(TIMESTAMPDIFF_TABLEID, TIMESTAMPDIFF_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) TIMESTAMPDIFF_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        TIMESTAMPDIFF_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), null, null, null, null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        TIMESTAMPDIFF_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), 0, 0, 0, 0}));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(TIMESTAMPDIFF_TABLEID, TIMESTAMPDIFF_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));

        DataChangeEvent insertEvent2 =
                DataChangeEvent.insertEvent(
                        TIMESTAMPDIFF_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("2"), null, null, null, null}));
        DataChangeEvent insertEventExpect2 =
                DataChangeEvent.insertEvent(
                        TIMESTAMPDIFF_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("2"), 0, 0, 0, 0}));

        transform.processElement(new StreamRecord<>(insertEvent2));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect2));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testTimezoneTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                TIMEZONE_TABLEID.identifier(),
                                "col1, DATE_FORMAT(TO_TIMESTAMP('2024-08-01 00:00:00'), 'yyyy-MM-dd HH:mm:ss') as datetime",
                                null)
                        .addTimezone("UTC")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(TIMEZONE_TABLEID, TIMEZONE_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) TIMEZONE_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        TIMEZONE_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        TIMEZONE_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new BinaryStringData("2024-08-01 00:00:00")
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(TIMEZONE_TABLEID, TIMEZONE_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testNullCastTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                NULL_TABLEID.identifier(),
                                "col1"
                                        + ",colString"
                                        + ",cast(colString as int) as nullInt"
                                        + ",cast(colString as boolean) as nullBoolean"
                                        + ",cast(colString as tinyint) as nullTinyint"
                                        + ",cast(colString as smallint) as nullSmallint"
                                        + ",cast(colString as bigint) as nullBigint"
                                        + ",cast(colString as float) as nullFloat"
                                        + ",cast(colString as double) as nullDouble"
                                        + ",cast(colString as char) as nullChar"
                                        + ",cast(colString as varchar) as nullVarchar"
                                        + ",cast(colString as DECIMAL(4,2)) as nullDecimal"
                                        + ",cast(colString as TIMESTAMP(3)) as nullTimestamp",
                                null)
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(NULL_TABLEID, NULL_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) NULL_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        NULL_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(new CreateTableEvent(NULL_TABLEID, NULL_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEvent));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testCastTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(col1 as int) as castInt"
                                        + ",cast(col1 as boolean) as castBoolean"
                                        + ",cast(col1 as tinyint) as castTinyint"
                                        + ",cast(col1 as smallint) as castSmallint"
                                        + ",cast(col1 as bigint) as castBigint"
                                        + ",cast(col1 as float) as castFloat"
                                        + ",cast(col1 as double) as castDouble"
                                        + ",cast(col1 as char) as castChar"
                                        + ",cast(col1 as varchar) as castVarchar"
                                        + ",cast(col1 as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '1'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castInt as int) as castInt"
                                        + ",cast(castInt as boolean) as castBoolean"
                                        + ",cast(castInt as tinyint) as castTinyint"
                                        + ",cast(castInt as smallint) as castSmallint"
                                        + ",cast(castInt as bigint) as castBigint"
                                        + ",cast(castInt as float) as castFloat"
                                        + ",cast(castInt as double) as castDouble"
                                        + ",cast(castInt as char) as castChar"
                                        + ",cast(castInt as varchar) as castVarchar"
                                        + ",cast(castInt as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '2'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castBoolean as int) as castInt"
                                        + ",cast(castBoolean as boolean) as castBoolean"
                                        + ",cast(castBoolean as tinyint) as castTinyint"
                                        + ",cast(castBoolean as smallint) as castSmallint"
                                        + ",cast(castBoolean as bigint) as castBigint"
                                        + ",cast(castBoolean as float) as castFloat"
                                        + ",cast(castBoolean as double) as castDouble"
                                        + ",cast(castBoolean as char) as castChar"
                                        + ",cast(castBoolean as varchar) as castVarchar"
                                        + ",cast(castBoolean as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '3'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castTinyint as int) as castInt"
                                        + ",cast(castTinyint as boolean) as castBoolean"
                                        + ",cast(castTinyint as tinyint) as castTinyint"
                                        + ",cast(castTinyint as smallint) as castSmallint"
                                        + ",cast(castTinyint as bigint) as castBigint"
                                        + ",cast(castTinyint as float) as castFloat"
                                        + ",cast(castTinyint as double) as castDouble"
                                        + ",cast(castTinyint as char) as castChar"
                                        + ",cast(castTinyint as varchar) as castVarchar"
                                        + ",cast(castTinyint as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '4'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castSmallint as int) as castInt"
                                        + ",cast(castSmallint as boolean) as castBoolean"
                                        + ",cast(castSmallint as tinyint) as castTinyint"
                                        + ",cast(castSmallint as smallint) as castSmallint"
                                        + ",cast(castSmallint as bigint) as castBigint"
                                        + ",cast(castSmallint as float) as castFloat"
                                        + ",cast(castSmallint as double) as castDouble"
                                        + ",cast(castSmallint as char) as castChar"
                                        + ",cast(castSmallint as varchar) as castVarchar"
                                        + ",cast(castSmallint as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '5'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castBigint as int) as castInt"
                                        + ",cast(castBigint as boolean) as castBoolean"
                                        + ",cast(castBigint as tinyint) as castTinyint"
                                        + ",cast(castBigint as smallint) as castSmallint"
                                        + ",cast(castBigint as bigint) as castBigint"
                                        + ",cast(castBigint as float) as castFloat"
                                        + ",cast(castBigint as double) as castDouble"
                                        + ",cast(castBigint as char) as castChar"
                                        + ",cast(castBigint as varchar) as castVarchar"
                                        + ",cast(castBigint as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '6'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",castInt"
                                        + ",cast(castFloat as boolean) as castBoolean"
                                        + ",castTinyint"
                                        + ",castSmallint"
                                        + ",castBigint"
                                        + ",cast(castFloat as float) as castFloat"
                                        + ",cast(castFloat as double) as castDouble"
                                        + ",cast(castFloat as char) as castChar"
                                        + ",cast(castFloat as varchar) as castVarchar"
                                        + ",cast(castFloat as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '7'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",castInt"
                                        + ",cast(castDouble as boolean) as castBoolean"
                                        + ",castTinyint"
                                        + ",castSmallint"
                                        + ",castBigint"
                                        + ",cast(castDouble as float) as castFloat"
                                        + ",cast(castDouble as double) as castDouble"
                                        + ",cast(castDouble as char) as castChar"
                                        + ",cast(castDouble as varchar) as castVarchar"
                                        + ",cast(castDouble as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '8'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",castInt"
                                        + ",cast(castDecimal as boolean) as castBoolean"
                                        + ",castTinyint"
                                        + ",castSmallint"
                                        + ",castBigint"
                                        + ",cast(castDecimal as float) as castFloat"
                                        + ",cast(castDecimal as double) as castDouble"
                                        + ",cast(castDecimal as char) as castChar"
                                        + ",cast(castDecimal as varchar) as castVarchar"
                                        + ",cast(castDecimal as DECIMAL(4,2)) as castDecimal"
                                        + ", castTimestamp",
                                "col1 = '9'")
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",castInt"
                                        + ",castBoolean"
                                        + ",castTinyint"
                                        + ",castSmallint"
                                        + ",castBigint"
                                        + ",castFloat"
                                        + ",castDouble"
                                        + ",castChar"
                                        + ",cast(castTimestamp as varchar) as castVarchar"
                                        + ",castDecimal"
                                        + ",cast('1970-01-01T00:00:01.234' as TIMESTAMP(3)) as castTimestamp",
                                "col1 = '10'")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(CAST_TABLEID, CAST_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) CAST_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent1 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect1 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    new Integer(1),
                                    new Boolean(false),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1"),
                                    new BinaryStringData("1"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(new CreateTableEvent(CAST_TABLEID, CAST_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent1));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect1));
        DataChangeEvent insertEvent2 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("2"),
                                    new Integer(1),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect2 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("2"),
                                    new Integer(1),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1"),
                                    new BinaryStringData("1"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent2));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect2));
        DataChangeEvent insertEvent3 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("3"),
                                    null,
                                    new Boolean(true),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect3 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("3"),
                                    new Integer(1),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("true"),
                                    new BinaryStringData("true"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent3));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect3));
        DataChangeEvent insertEvent4 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("4"),
                                    null,
                                    null,
                                    new Byte("1"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect4 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("4"),
                                    new Integer(1),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1"),
                                    new BinaryStringData("1"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent4));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect4));
        DataChangeEvent insertEvent5 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("5"),
                                    null,
                                    null,
                                    null,
                                    new Short("1"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect5 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("5"),
                                    new Integer(1),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1"),
                                    new BinaryStringData("1"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent5));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect5));
        DataChangeEvent insertEvent6 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("6"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    new Long(1),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect6 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("6"),
                                    new Integer(1),
                                    new Boolean(true),
                                    new Byte("1"),
                                    new Short("1"),
                                    new Long(1),
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1"),
                                    new BinaryStringData("1"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent6));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect6));
        DataChangeEvent insertEvent7 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("7"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    new Float(1.0f),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect7 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("7"),
                                    null,
                                    new Boolean(true),
                                    null,
                                    null,
                                    null,
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1.0"),
                                    new BinaryStringData("1.0"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent7));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect7));
        DataChangeEvent insertEvent8 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("8"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    new Double(1.0d),
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        DataChangeEvent insertEventExpect8 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("8"),
                                    null,
                                    new Boolean(true),
                                    null,
                                    null,
                                    null,
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1.0"),
                                    new BinaryStringData("1.0"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent8));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect8));
        DataChangeEvent insertEvent9 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("9"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        DataChangeEvent insertEventExpect9 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("9"),
                                    null,
                                    new Boolean(true),
                                    null,
                                    null,
                                    null,
                                    new Float(1.0f),
                                    new Double(1.0d),
                                    new BinaryStringData("1.00"),
                                    new BinaryStringData("1.00"),
                                    DecimalData.fromBigDecimal(new BigDecimal(1.0), 4, 2),
                                    null
                                }));
        transform.processElement(new StreamRecord<>(insertEvent9));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect9));

        DataChangeEvent insertEvent10 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("10"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    TimestampData.fromMillis(1234, 0)
                                }));
        DataChangeEvent insertEventExpect10 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("10"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    new BinaryStringData("1970-01-01T00:00:01.234"),
                                    null,
                                    TimestampData.fromMillis(1234, 0)
                                }));
        transform.processElement(new StreamRecord<>(insertEvent10));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect10));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testCastErrorTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                CAST_TABLEID.identifier(),
                                "col1"
                                        + ",cast(castFloat as int) as castInt"
                                        + ",cast(castFloat as boolean) as castBoolean"
                                        + ",cast(castFloat as tinyint) as castTinyint"
                                        + ",cast(castFloat as smallint) as castSmallint"
                                        + ",cast(castFloat as bigint) as castBigint"
                                        + ",cast(castFloat as float) as castFloat"
                                        + ",cast(castFloat as double) as castDouble"
                                        + ",cast(castFloat as char) as castChar"
                                        + ",cast(castFloat as varchar) as castVarchar"
                                        + ",cast(castFloat as DECIMAL(4,2)) as castDecimal"
                                        + ",cast(castFloat as TIMESTAMP(3)) as castTimestamp",
                                "col1 = '1'")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(CAST_TABLEID, CAST_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) CAST_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent1 =
                DataChangeEvent.insertEvent(
                        CAST_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("1"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    new Float(1.0f),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                                }));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(new CreateTableEvent(CAST_TABLEID, CAST_SCHEMA)));
        Assertions.assertThatThrownBy(
                        () -> {
                            transform.processElement(new StreamRecord<>(insertEvent1));
                        })
                .isExactlyInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(DateTimeParseException.class)
                .hasRootCauseMessage("Text '1.0' could not be parsed at index 0");
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    void testBuildInFunctionTransform() throws Exception {
        testExpressionConditionTransform(
                "TO_TIMESTAMP('1970-01-01 00:00:00') = TO_TIMESTAMP('1970-01-01', 'yyyy-MM-dd')");
        testExpressionConditionTransform(
                "TIMESTAMP_DIFF('DAY', TO_TIMESTAMP('1970-01-01 00:00:00'), TO_TIMESTAMP('1970-01-02 00:00:00')) = 1");
        testExpressionConditionTransform("2 between 1 and 3");
        testExpressionConditionTransform("4 not between 1 and 3");
        testExpressionConditionTransform("2 in (1, 2, 3)");
        testExpressionConditionTransform("4 not in (1, 2, 3)");
        testExpressionConditionTransform("CHAR_LENGTH('abc') = 3");
        testExpressionConditionTransform("trim(' abc ') = 'abc'");
        testExpressionConditionTransform("REGEXP_REPLACE('123abc', '[a-zA-Z]', '') = '123'");
        testExpressionConditionTransform("concat('123', 'abc') = '123abc'");
        testExpressionConditionTransform("upper('abc') = 'ABC'");
        testExpressionConditionTransform("lower('ABC') = 'abc'");
        testExpressionConditionTransform("SUBSTR('ABC', -1) = 'C'");
        testExpressionConditionTransform("SUBSTR('ABC', -2, 2) = 'BC'");
        testExpressionConditionTransform("SUBSTR('ABC', 0) = 'ABC'");
        testExpressionConditionTransform("SUBSTR('ABC', 1) = 'ABC'");
        testExpressionConditionTransform("SUBSTR('ABC', 2, 2) = 'BC'");
        testExpressionConditionTransform("SUBSTR('ABC', 2, 100) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC', -1) = 'C'");
        testExpressionConditionTransform("SUBSTRING('ABC', -2, 2) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC', 0) = 'ABC'");
        testExpressionConditionTransform("SUBSTRING('ABC', 1) = 'ABC'");
        testExpressionConditionTransform("SUBSTRING('ABC', 2, 2) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC', 2, 100) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM -1) = 'C'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM -2 FOR 2) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM 0) = 'ABC'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM 1) = 'ABC'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM 2 FOR 2) = 'BC'");
        testExpressionConditionTransform("SUBSTRING('ABC' FROM 2 FOR 100) = 'BC'");
        testExpressionConditionTransform("'ABC' like '^[a-zA-Z]'");
        testExpressionConditionTransform("'123' not like '^[a-zA-Z]'");
        testExpressionConditionTransform("abs(2) = 2");
        testExpressionConditionTransform("ceil(2.4) = 3.0");
        testExpressionConditionTransform("floor(2.5) = 2.0");
        testExpressionConditionTransform("round(3.1415926,2) = 3.14");
        testExpressionConditionTransform("IF(2>0,1,0) = 1");
        testExpressionConditionTransform("COALESCE(null,1,2) = 1");
        testExpressionConditionTransform("1 + 1 = 2");
        testExpressionConditionTransform("1 - 1 = 0");
        testExpressionConditionTransform("1 * 1 = 1");
        testExpressionConditionTransform("3 % 2 = 1");
        testExpressionConditionTransform("1 < 2");
        testExpressionConditionTransform("1 <= 1");
        testExpressionConditionTransform("1 > 0");
        testExpressionConditionTransform("1 >= 1");
        testExpressionConditionTransform(
                "case 1 when 1 then 'a' when 2 then 'b' else 'c' end = 'a'");
        testExpressionConditionTransform("case col1 when '1' then true else false end");
        testExpressionConditionTransform("case when col1 = '1' then true else false end");
        testExpressionConditionTransform("cast(col1 as int) = 1");
        testExpressionConditionTransform("cast('true' as boolean)");
        testExpressionConditionTransform("cast(col1 as tinyint) = cast(1 as tinyint)");
        testExpressionConditionTransform("cast(col1 as smallint) = cast(1 as smallint)");
        testExpressionConditionTransform("cast(col1 as bigint) = cast(1 as bigint)");
        testExpressionConditionTransform("cast(col1 as float) = cast(1 as float)");
        testExpressionConditionTransform("cast(col1 as double) = cast(1 as double)");
        testExpressionConditionTransform("cast('1' as char) = '1'");
        testExpressionConditionTransform("cast(col1 as varchar) = '1'");
        testExpressionConditionTransform("cast(col1 as DECIMAL(4,2)) = cast(1.0 as DECIMAL(4,2))");
        testExpressionConditionTransform("cast(null as int) is null");
        testExpressionConditionTransform("cast(null as boolean) is null");
        testExpressionConditionTransform("cast(null as tinyint) is null");
        testExpressionConditionTransform("cast(null as smallint) is null");
        testExpressionConditionTransform("cast(null as bigint) is null");
        testExpressionConditionTransform("cast(null as float) is null");
        testExpressionConditionTransform("cast(null as double) is null");
        testExpressionConditionTransform("cast(null as char) is null");
        testExpressionConditionTransform("cast(null as varchar) is null");
        testExpressionConditionTransform("cast(null as DECIMAL(4,2)) is null");
        testExpressionConditionTransform("cast(null as TIMESTAMP(3)) is null");
    }

    private void testExpressionConditionTransform(String expression) throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                CONDITION_TABLEID.identifier(),
                                "col1, IF(" + expression + ", true, false) as condition_result",
                                expression)
                        .addTimezone("UTC")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent =
                new CreateTableEvent(CONDITION_TABLEID, CONDITION_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) CONDITION_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        CONDITION_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), null}));
        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        CONDITION_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {new BinaryStringData("1"), true}));
        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(CONDITION_TABLEID, CONDITION_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    public void testReduceSchemaTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                REDUCE_TABLEID.identifier(),
                                "id, upper(id) as uid, age + 1 as newage, lower(ref1) as ref1, 17 as seventeen",
                                "newage > 17 and ref2 > 17")
                        .addTimezone("GMT")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(REDUCE_TABLEID, REDUCE_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) REDUCE_SCHEMA.toRowDataType()));
        BinaryRecordDataGenerator expectedRecordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) EXPECTED_REDUCE_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        REDUCE_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Reference"),
                                    42
                                }));

        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        REDUCE_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    new BinaryStringData("ID001"),
                                    18,
                                    new BinaryStringData("reference"),
                                    17
                                }));

        // Update
        DataChangeEvent updateEvent =
                DataChangeEvent.updateEvent(
                        REDUCE_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Reference"),
                                    42
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    18,
                                    new BinaryStringData("UpdatedReference"),
                                    41
                                }));

        DataChangeEvent updateEventExpect =
                DataChangeEvent.updateEvent(
                        REDUCE_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    new BinaryStringData("ID001"),
                                    18,
                                    new BinaryStringData("reference"),
                                    17
                                }),
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    new BinaryStringData("ID001"),
                                    19,
                                    new BinaryStringData("updatedreference"),
                                    17
                                }));

        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(REDUCE_TABLEID, EXPECTED_REDUCE_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));

        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));

        transform.processElement(new StreamRecord<>(updateEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(updateEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }

    @Test
    public void testWildcardSchemaTransform() throws Exception {
        PostTransformOperator transform =
                PostTransformOperator.newBuilder()
                        .addTransform(
                                WILDCARD_TABLEID.identifier(),
                                "*, age + 1 as newage",
                                "newage > 17")
                        .addTimezone("GMT")
                        .build();
        RegularEventOperatorTestHarness<PostTransformOperator, Event>
                transformFunctionEventEventOperatorTestHarness =
                        RegularEventOperatorTestHarness.with(transform, 1);
        // Initialization
        transformFunctionEventEventOperatorTestHarness.open();
        // Create table
        CreateTableEvent createTableEvent = new CreateTableEvent(WILDCARD_TABLEID, WILDCARD_SCHEMA);
        BinaryRecordDataGenerator recordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) WILDCARD_SCHEMA.toRowDataType()));
        BinaryRecordDataGenerator expectedRecordDataGenerator =
                new BinaryRecordDataGenerator(((RowType) EXPECTED_WILDCARD_SCHEMA.toRowDataType()));
        // Insert
        DataChangeEvent insertEvent =
                DataChangeEvent.insertEvent(
                        WILDCARD_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Alice"),
                                }));

        DataChangeEvent insertEventExpect =
                DataChangeEvent.insertEvent(
                        WILDCARD_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Alice"),
                                    18
                                }));

        // Update
        DataChangeEvent updateEvent =
                DataChangeEvent.updateEvent(
                        WILDCARD_TABLEID,
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Alice"),
                                }),
                        recordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    18,
                                    new BinaryStringData("Arisu"),
                                }));

        DataChangeEvent updateEventExpect =
                DataChangeEvent.updateEvent(
                        WILDCARD_TABLEID,
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    17,
                                    new BinaryStringData("Alice"),
                                    18
                                }),
                        expectedRecordDataGenerator.generate(
                                new Object[] {
                                    new BinaryStringData("id001"),
                                    18,
                                    new BinaryStringData("Arisu"),
                                    19
                                }));

        transform.processElement(new StreamRecord<>(createTableEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(
                        new StreamRecord<>(
                                new CreateTableEvent(WILDCARD_TABLEID, EXPECTED_WILDCARD_SCHEMA)));
        transform.processElement(new StreamRecord<>(insertEvent));

        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(insertEventExpect));

        transform.processElement(new StreamRecord<>(updateEvent));
        Assertions.assertThat(
                        transformFunctionEventEventOperatorTestHarness.getOutputRecords().poll())
                .isEqualTo(new StreamRecord<>(updateEventExpect));
        transformFunctionEventEventOperatorTestHarness.close();
    }
}
