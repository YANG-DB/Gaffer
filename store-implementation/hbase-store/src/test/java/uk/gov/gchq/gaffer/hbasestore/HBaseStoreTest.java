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

package uk.gov.gchq.gaffer.hbasestore;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.graph.Graph;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.AddElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.GetAllElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.handler.GetElementsHandler;
import uk.gov.gchq.gaffer.hbasestore.operation.hdfs.handler.AddElementsFromHdfsHandler;
import uk.gov.gchq.gaffer.hbasestore.utils.TableUtils;
import uk.gov.gchq.gaffer.hdfs.operation.AddElementsFromHdfs;
import uk.gov.gchq.gaffer.operation.impl.Validate;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.generate.GenerateElements;
import uk.gov.gchq.gaffer.operation.impl.generate.GenerateObjects;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.StoreTrait;
import uk.gov.gchq.gaffer.store.operation.handler.OperationHandler;
import uk.gov.gchq.gaffer.store.operation.handler.generate.GenerateElementsHandler;
import uk.gov.gchq.gaffer.store.operation.handler.generate.GenerateObjectsHandler;
import uk.gov.gchq.gaffer.store.schema.Schema;

import java.io.IOException;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.gchq.gaffer.store.StoreTrait.INGEST_AGGREGATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.ORDERED;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.POST_TRANSFORMATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.PRE_AGGREGATION_FILTERING;
import static uk.gov.gchq.gaffer.store.StoreTrait.QUERY_AGGREGATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.STORE_VALIDATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.TRANSFORMATION;
import static uk.gov.gchq.gaffer.store.StoreTrait.VISIBILITY;

public class HBaseStoreTest {
    private static final Schema SCHEMA = Schema.fromJson(StreamUtil.schemas(HBaseStoreTest.class));
    private static final HBaseProperties PROPERTIES = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
    private static final String GRAPH_ID = "graphId";
    private static SingleUseMiniHBaseStore store;

    @BeforeAll
    public static void setup() throws StoreException, IOException {
        store = new SingleUseMiniHBaseStore();
        store.initialise(GRAPH_ID, SCHEMA, PROPERTIES);
    }

    @BeforeEach
    public void beforeMethod() throws StoreException, IOException {
        try (final Admin admin = store.getConnection().getAdmin()) {
            if (!admin.tableExists(store.getTableName())) {
                store.initialise(GRAPH_ID, SCHEMA, PROPERTIES);
            }
        }
    }

    @AfterAll
    public static void tearDown() {
        store = null;
    }

    @Test
    public void shouldCreateTableWhenInitialised() throws StoreException, IOException {
        final Connection connection = store.getConnection();
        final TableName tableName = store.getTableName();
        try (final Admin admin = connection.getAdmin()) {
            assertTrue(admin.tableExists(tableName));
        }
    }

    @Test
    public void shouldNotCreateTableWhenInitialisedWithGeneralInitialiseMethod() throws StoreException, IOException {
        final TableName tableName = store.getTableName();
        Connection connection = store.getConnection();

        TableUtils.dropTable(store);
        try (final Admin admin = connection.getAdmin()) {
            assertFalse(admin.tableExists(tableName));
        }

        store.preInitialise(GRAPH_ID, SCHEMA, PROPERTIES);
        connection = store.getConnection();
        try (final Admin admin = connection.getAdmin()) {
            assertFalse(admin.tableExists(tableName));
        }

        store.initialise(GRAPH_ID, SCHEMA, PROPERTIES);
        connection = store.getConnection();
        try (final Admin admin = connection.getAdmin()) {
            assertTrue(admin.tableExists(tableName));
        }
    }

    @Test
    public void shouldCreateAStoreUsingTableName() throws Exception {
        // Given
        final HBaseProperties properties = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
        properties.setTable("tableName");
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();

        // When
        store.initialise(null, SCHEMA, properties);

        // Then
        assertEquals("tableName", store.getGraphId());
        assertEquals("tableName", store.getTableName().getNameAsString());
    }

    @Test
    public void shouldBuildGraphAndGetGraphIdFromTableName() throws Exception {
        // Given
        final HBaseProperties properties = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
        properties.setTable("tableName");

        // When
        final Graph graph = new Graph.Builder()
                .addSchemas(StreamUtil.schemas(getClass()))
                .storeProperties(properties)
                .build();

        // Then
        assertEquals("tableName", graph.getGraphId());
    }

    @Test
    public void shouldCreateAStoreUsingGraphIdIfItIsEqualToTableName() throws Exception {
        // Given
        final HBaseProperties properties = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
        properties.setTable("tableName");
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();

        // When
        store.initialise("tableName", SCHEMA, properties);

        // Then
        assertEquals("tableName", store.getGraphId());
    }

    @Test
    public void shouldThrowExceptionIfGraphIdAndTableNameAreProvidedAndDifferent() throws Exception {
        // Given
        final HBaseProperties properties = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
        properties.setTable("tableName");
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();

        // When / Then
        assertThatIllegalArgumentException().isThrownBy(() -> store.initialise("graphId", SCHEMA, properties)).extracting("message").isNotNull();
    }

    @Test
    public void shouldCreateAStoreUsingGraphId() throws Exception {
        // Given
        final HBaseProperties properties = HBaseProperties.loadStoreProperties(StreamUtil.storeProps(HBaseStoreTest.class));
        final SingleUseMiniHBaseStore store = new SingleUseMiniHBaseStore();

        // When
        store.initialise("graphId", SCHEMA, properties);

        // Then
        assertEquals("graphId", store.getGraphId());
    }

    @Test
    public void shouldBeAnOrderedStore() {
        assertTrue(store.hasTrait(StoreTrait.ORDERED));
    }

    @Test
    public void testStoreReturnsHandlersForRegisteredOperations() throws StoreException {
        // Then
        assertNotNull(store.getOperationHandlerExposed(Validate.class));
        assertTrue(store.getOperationHandlerExposed(GetElements.class) instanceof GetElementsHandler);
        assertTrue(store.getOperationHandlerExposed(GetAllElements.class) instanceof GetAllElementsHandler);
        assertTrue(store.getOperationHandlerExposed(AddElements.class) instanceof AddElementsHandler);
        assertTrue(store.getOperationHandlerExposed(AddElementsFromHdfs.class) instanceof AddElementsFromHdfsHandler);
        assertTrue(store.getOperationHandlerExposed(GenerateElements.class) instanceof GenerateElementsHandler);
        assertTrue(store.getOperationHandlerExposed(GenerateObjects.class) instanceof GenerateObjectsHandler);
    }

    @Test
    public void testRequestForNullHandlerManaged() {
        final OperationHandler returnedHandler = store.getOperationHandlerExposed(null);
        assertNull(returnedHandler);
    }

    @Test
    public void testStoreTraits() {
        final Collection<StoreTrait> traits = store.getTraits();
        assertNotNull(traits);
        assertEquals(traits.size(), 10, "Collection size should be 10");
        assertTrue(traits.contains(INGEST_AGGREGATION),
                "Collection should contain INGEST_AGGREGATION trait");
        assertTrue(traits.contains(QUERY_AGGREGATION),
                "Collection should contain QUERY_AGGREGATION trait");
        assertTrue(traits.contains(PRE_AGGREGATION_FILTERING),
                "Collection should contain PRE_AGGREGATION_FILTERING trait");
        assertTrue(traits.contains(POST_AGGREGATION_FILTERING),
                "Collection should contain POST_AGGREGATION_FILTERING trait");
        assertTrue(traits.contains(TRANSFORMATION),
                "Collection should contain TRANSFORMATION trait");
        assertTrue(traits.contains(POST_TRANSFORMATION_FILTERING),
                "Collection should contain POST_TRANSFORMATION_FILTERING trait");
        assertTrue(traits.contains(STORE_VALIDATION),
                "Collection should contain STORE_VALIDATION trait");
        assertTrue(traits.contains(ORDERED),
                "Collection should contain ORDERED trait");
        assertTrue(traits.contains(VISIBILITY),
                "Collection should contain VISIBILITY trait");
    }

}
