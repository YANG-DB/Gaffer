/*
 * Copyright 2017-2021 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.hbasestore.util;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.commonutil.JsonAssert;
import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.commonutil.TestGroups;
import uk.gov.gchq.gaffer.commonutil.pair.Pair;
import uk.gov.gchq.gaffer.hbasestore.HBaseProperties;
import uk.gov.gchq.gaffer.hbasestore.SingleUseMiniHBaseStore;
import uk.gov.gchq.gaffer.hbasestore.coprocessor.GafferCoprocessor;
import uk.gov.gchq.gaffer.hbasestore.utils.TableUtils;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.TestTypes;
import uk.gov.gchq.gaffer.store.library.FileGraphLibrary;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.store.schema.SchemaEdgeDefinition;
import uk.gov.gchq.gaffer.store.schema.TypeDefinition;
import uk.gov.gchq.koryphe.impl.binaryoperator.StringConcat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TableUtilsTest {
    private static final String GRAPH_ID = "graphId";
    private static final String SCHEMA_DIR = "src/test/resources/schema";
    private static final String SCHEMA_2_DIR = "src/test/resources/schema2";
    private static final String STORE_PROPS_PATH = "src/test/resources/store.properties";
    private static final String STORE_PROPS_2_PATH = "src/test/resources/store2.properties";
    private static final String FILE_GRAPH_LIBRARY_TEST_PATH = "target/graphLibrary";

    @BeforeEach
    @AfterEach
    public void cleanUp() throws IOException {
        if (new File(FILE_GRAPH_LIBRARY_TEST_PATH).exists()) {
            FileUtils.forceDelete(new File(FILE_GRAPH_LIBRARY_TEST_PATH));
        }
    }

    @Test
    public void shouldCreateTableAndValidateIt() throws Exception {
        // Given
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();
        final Schema schema = new Schema.Builder()
                .type(TestTypes.ID_STRING, new TypeDefinition.Builder()
                        .aggregateFunction(new StringConcat())
                        .clazz(String.class)
                        .build())
                .type(TestTypes.DIRECTED_TRUE, Boolean.class)
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .source(TestTypes.ID_STRING)
                        .destination(TestTypes.ID_STRING)
                        .directed(TestTypes.DIRECTED_TRUE)
                        .build())
                .build();

        final HBaseProperties props = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(TableUtilsTest.class));
        store.initialise(GRAPH_ID, schema, props);

        // When
        TableUtils.createTable(store);
        TableUtils.ensureTableExists(store);

        // Then - no exceptions
    }

    @Test
    public void shouldFailTableValidationWhenTableDoesntHaveCoprocessor() throws Exception {
        // Given
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();
        final Schema schema = new Schema.Builder()
                .type(TestTypes.ID_STRING, new TypeDefinition.Builder()
                        .aggregateFunction(new StringConcat())
                        .clazz(String.class)
                        .build())
                .type(TestTypes.DIRECTED_TRUE, Boolean.class)
                .edge(TestGroups.EDGE, new SchemaEdgeDefinition.Builder()
                        .source(TestTypes.ID_STRING)
                        .destination(TestTypes.ID_STRING)
                        .directed(TestTypes.DIRECTED_TRUE)
                        .build())
                .build();

        final HBaseProperties props = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(TableUtilsTest.class));
        store.initialise(GRAPH_ID, schema, props);

        // Remove coprocessor
        final TableName tableName = store.getTableName();
        try (final Admin admin = store.getConnection().getAdmin()) {
            final HTableDescriptor descriptor = admin.getTableDescriptor(tableName);
            descriptor.removeCoprocessor(GafferCoprocessor.class.getName());
            admin.modifyTable(tableName, descriptor);
        } catch (final StoreException | IOException e) {
            throw new RuntimeException(e);
        }

        // When / Then
        assertThatExceptionOfType(StoreException.class).isThrownBy(() -> TableUtils.ensureTableExists(store)).extracting("message").isNotNull();
    }

    @Test
    public void shouldRunMainWithFileGraphLibrary() throws Exception {
        // Given
        final String[] args = {GRAPH_ID, SCHEMA_DIR, STORE_PROPS_PATH, FILE_GRAPH_LIBRARY_TEST_PATH};

        // When
        TableUtils.main(args);


        // Then
        final Pair<Schema, StoreProperties> pair = new FileGraphLibrary(FILE_GRAPH_LIBRARY_TEST_PATH).get(GRAPH_ID);
        assertNotNull(pair, "Graph for " + GRAPH_ID + " was not found");
        assertNotNull(pair.getFirst(), "Schema not found");
        assertNotNull(pair.getSecond(), "Store properties not found");
        JsonAssert.assertEquals(Schema.fromJson(Paths.get(SCHEMA_DIR)).toJson(false), pair.getFirst().toJson(false));
        assertEquals(HBaseProperties.loadStoreProperties(STORE_PROPS_PATH).getProperties(), pair.getSecond().getProperties());
    }

    @Test
    public void shouldOverrideExistingGraphInGraphLibrary() throws Exception {
        // Given
        shouldRunMainWithFileGraphLibrary(); // load version graph version 1 into the library.
        final String[] args = {GRAPH_ID, SCHEMA_2_DIR, STORE_PROPS_2_PATH, FILE_GRAPH_LIBRARY_TEST_PATH};

        // When
        TableUtils.main(args);

        // Then
        final Pair<Schema, StoreProperties> pair = new FileGraphLibrary(FILE_GRAPH_LIBRARY_TEST_PATH).get(GRAPH_ID);
        assertNotNull(pair, "Graph for " + GRAPH_ID + " was not found");
        assertNotNull(pair.getFirst(), "Schema not found");
        assertNotNull(pair.getSecond(), "Store properties not found");
        JsonAssert.assertEquals(Schema.fromJson(Paths.get(SCHEMA_2_DIR)).toJson(false), pair.getFirst().toJson(false));
        assertEquals(HBaseProperties.loadStoreProperties(STORE_PROPS_2_PATH).getProperties(), pair.getSecond().getProperties());
    }

    @Test
    public void shouldRunMainWithNoGraphLibrary() throws Exception {
        // Given
        final String[] args = {GRAPH_ID, SCHEMA_DIR, STORE_PROPS_PATH};

        // When
        TableUtils.main(args);

        // Then - no exceptions
        final Pair<Schema, StoreProperties> pair = new FileGraphLibrary(FILE_GRAPH_LIBRARY_TEST_PATH).get(GRAPH_ID);
        assertNull(pair, "Graph should not have been stored");
    }
}
