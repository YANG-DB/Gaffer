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

package uk.gov.gchq.gaffer.store.operation.handler.named.cache;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.access.predicate.NoAccessPredicate;
import uk.gov.gchq.gaffer.cache.CacheServiceLoader;
import uk.gov.gchq.gaffer.cache.impl.HashMapCacheService;
import uk.gov.gchq.gaffer.cache.util.CacheProperties;
import uk.gov.gchq.gaffer.commonutil.exception.OverwritingException;
import uk.gov.gchq.gaffer.commonutil.iterable.CloseableIterable;
import uk.gov.gchq.gaffer.named.operation.NamedOperationDetail;
import uk.gov.gchq.gaffer.named.operation.cache.exception.CacheOperationFailedException;
import uk.gov.gchq.gaffer.operation.OperationChain;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class NamedOperationCacheTest {

    private static NamedOperationCache cache;
    private static final String GAFFER_USER = "gaffer user";
    private static final String ADVANCED_GAFFER_USER = "advanced gaffer user";
    private static final String ADMIN_AUTH = "admin auth";
    private static final String EMPTY_ADMIN_AUTH = "";
    private List<String> readers = Collections.singletonList(GAFFER_USER);
    private List<String> writers = Collections.singletonList(ADVANCED_GAFFER_USER);
    private User standardUser = new User.Builder().opAuths(GAFFER_USER).userId("123").build();
    private User userWithAdminAuth = new User.Builder().opAuths(ADMIN_AUTH).userId("adminUser").build();
    private User advancedUser = new User.Builder().opAuths(GAFFER_USER, ADVANCED_GAFFER_USER).userId("456").build();
    private OperationChain standardOpChain = new OperationChain.Builder().first(new AddElements()).build();
    private OperationChain alternativeOpChain = new OperationChain.Builder()
            .first(new GetElements.Builder().build())
            .build();
    private static final String OPERATION_NAME = "New operation";

    private NamedOperationDetail standard = new NamedOperationDetail.Builder()
            .operationName(OPERATION_NAME)
            .description("standard operation")
            .creatorId(standardUser.getUserId())
            .readers(readers)
            .writers(writers)
            .operationChain(standardOpChain)
            .build();


    private NamedOperationDetail alternative = new NamedOperationDetail.Builder()
            .operationName(OPERATION_NAME)
            .description("alternative operation")
            .creatorId(advancedUser.getUserId())
            .readers(readers)
            .writers(writers)
            .operationChain(alternativeOpChain)
            .build();


    @BeforeAll
    public static void setUp() {
        Properties properties = new Properties();
        properties.setProperty(CacheProperties.CACHE_SERVICE_CLASS, HashMapCacheService.class.getName());
        CacheServiceLoader.initialise(properties);
        cache = new NamedOperationCache();
    }

    @BeforeEach
    public void beforeEach() throws CacheOperationFailedException {
        cache.clear();
    }

    @Test
    public void shouldAddNamedOperation() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        NamedOperationDetail namedOperation = cache.getNamedOperation(OPERATION_NAME, standardUser);

        assertEquals(standard, namedOperation);

    }

    @Test
    public void shouldThrowExceptionIfNamedOperationAlreadyExists() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        assertThatExceptionOfType(OverwritingException.class).isThrownBy(() -> cache.addNamedOperation(alternative, false, advancedUser));
    }

    @Test
    public void shouldThrowExceptionWhenDeletingIfKeyIsNull() throws CacheOperationFailedException { // needs work
        cache.addNamedOperation(standard, false, standardUser);
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.deleteNamedOperation(null, advancedUser));
    }

    @Test
    public void shouldThrowExceptionWhenGettingIfKeyIsNull() throws CacheOperationFailedException {
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.getNamedOperation(null, advancedUser));
    }

    @Test
    public void shouldThrowExceptionIfNamedOperationIsNull() throws CacheOperationFailedException {
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.addNamedOperation(null, false, standardUser));
    }

    @Test
    public void shouldThrowExceptionIfUnauthorisedUserTriesToReadOperation() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.getNamedOperation(OPERATION_NAME, new User()));
    }

    @Test
    public void shouldAllowUsersWithCorrectOpAuthsReadAccessToTheOperationChain() throws CacheOperationFailedException { // see if this works with standard user - it should do
        cache.addNamedOperation(standard, false, standardUser);
        assertEquals(standard, cache.getNamedOperation(OPERATION_NAME, advancedUser));
    }

    @Test
    public void shouldAllowUsersReadAccessToTheirOwnNamedOperations() throws CacheOperationFailedException {
        NamedOperationDetail op = new NamedOperationDetail.Builder()
                .operationName(OPERATION_NAME)
                .creatorId(standardUser.getUserId())
                .operationChain(standardOpChain)
                .readers(new ArrayList<>())
                .writers(writers)
                .build();

        cache.addNamedOperation(op, false, standardUser);
        assertEquals(op, cache.getNamedOperation(OPERATION_NAME, standardUser));
    }

    @Test
    public void shouldAllowUsersWriteAccessToTheirOwnOperations() throws CacheOperationFailedException {
        NamedOperationDetail op = new NamedOperationDetail.Builder()
                .operationName(OPERATION_NAME)
                .creatorId(standardUser.getUserId())
                .operationChain(standardOpChain)
                .readers(readers)
                .writers(new ArrayList<>())
                .build();

        cache.addNamedOperation(op, false, standardUser);
        cache.addNamedOperation(standard, true, standardUser);

        assertEquals(standard, cache.getNamedOperation(OPERATION_NAME, standardUser));
    }

    @Test
    public void shouldThrowExceptionIfUnauthorisedUserTriesToOverwriteOperation() throws CacheOperationFailedException {
        cache.addNamedOperation(alternative, false, advancedUser);
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.addNamedOperation(standard, true, standardUser));
    }

    @Test
    public void shouldAllowOverWriteIfFlagIsSetAndUserIsAuthorised() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        cache.addNamedOperation(alternative, true, advancedUser);

        assertEquals(alternative, cache.getNamedOperation(OPERATION_NAME, standardUser));
    }

    @Test
    public void shouldThrowExceptionIfUnauthorisedUserTriesToDeleteOperation() throws CacheOperationFailedException {
        cache.addNamedOperation(alternative, false, advancedUser);
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.deleteNamedOperation(OPERATION_NAME, standardUser));
    }

    @Test
    public void shouldThrowExceptionTryingToDeleteOperationConfiguredWithWriteNoAccessPredicate() throws CacheOperationFailedException {
        final NamedOperationDetail noWriteAccess = new NamedOperationDetail.Builder()
                .creatorId(standardUser.getUserId())
                .description("an operation that does no allow read access")
                .operationName("test")
                .readers(readers)
                .operationChain(standardOpChain)
                .writeAccessPredicate(new NoAccessPredicate())
                .build();
        cache.addNamedOperation(noWriteAccess, false, standardUser);
        assertThatExceptionOfType(CacheOperationFailedException.class).isThrownBy(() -> cache.deleteNamedOperation("test", standardUser));
    }

    @Test
    public void shouldReturnEmptySetIfThereAreNoOperationsInTheCache() {
        CloseableIterable<NamedOperationDetail> ops = cache.getAllNamedOperations(standardUser);
        assert Iterables.size(ops) == 0;
    }

    @Test
    public void shouldReturnSetOfNamedOperationsThatAUserCanExecute() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        NamedOperationDetail alt = new NamedOperationDetail.Builder()
                .operationName("different operation")
                .description("alt")
                .creatorId(advancedUser.getUserId())
                .readers(readers)
                .writers(writers)
                .operationChain(alternativeOpChain)
                .build();

        cache.addNamedOperation(alt, false, advancedUser);

        Set<NamedOperationDetail> actual = Sets.newHashSet(cache.getAllNamedOperations(standardUser));

        assert actual.contains(standard);
        assert actual.contains(alt);
        assert actual.size() == 2;
    }

    @Test
    public void shouldNotReturnANamedOperationThatAUserCannotExecute() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);

        NamedOperationDetail noReadAccess = new NamedOperationDetail.Builder()
                .creatorId(advancedUser.getUserId())
                .description("an operation that a standard user cannot execute")
                .operationName("test")
                .readers(writers)
                .writers(writers)
                .operationChain(standardOpChain)
                .build();
        cache.addNamedOperation(noReadAccess, false, advancedUser);

        Set<NamedOperationDetail> actual = Sets.newHashSet(cache.getAllNamedOperations(standardUser));

        assert actual.contains(standard);
        assert actual.size() == 1;
    }

    @Test
    public void shouldNotReturnNamedOperationConfiguredWithReadNoAccessPredicate() throws CacheOperationFailedException {
        final NamedOperationDetail noReadAccess = new NamedOperationDetail.Builder()
                .creatorId(standardUser.getUserId())
                .description("an operation that does no allow read access")
                .operationName("test")
                .writers(writers)
                .operationChain(standardOpChain)
                .readAccessPredicate(new NoAccessPredicate())
                .build();
        cache.addNamedOperation(noReadAccess, false, standardUser);

        assertFalse(cache.getAllNamedOperations(standardUser).iterator().hasNext());
    }

    @Test
    public void shouldBeAbleToReturnFullExtendedOperationChain() throws CacheOperationFailedException {
        cache.addNamedOperation(standard, false, standardUser);
        NamedOperationDetail alt = new NamedOperationDetail.Builder()
                .operationName("different")
                .description("alt")
                .creatorId(advancedUser.getUserId())
                .readers(readers)
                .writers(writers)
                .operationChain(alternativeOpChain)
                .build();

        cache.addNamedOperation(alt, false, advancedUser);

        Set<NamedOperationDetail> actual = Sets.newHashSet(cache.getAllNamedOperations(standardUser));
        assert (actual.contains(standard));
        assert (actual.contains(alt));
        assert (actual.size() == 2);
    }

    @Test
    public void shouldAllowAddingWhenUserHasAdminAuth() throws CacheOperationFailedException {
        cache.addNamedOperation(alternative, false, advancedUser, EMPTY_ADMIN_AUTH);
        NamedOperationDetail alt = new NamedOperationDetail.Builder()
                .operationName(alternative.getOperationName())
                .description("alt")
                .creatorId(standardUser.getUserId())
                .operationChain(alternativeOpChain)
                .build();

        cache.addNamedOperation(alt, true, userWithAdminAuth, ADMIN_AUTH);
    }
}
