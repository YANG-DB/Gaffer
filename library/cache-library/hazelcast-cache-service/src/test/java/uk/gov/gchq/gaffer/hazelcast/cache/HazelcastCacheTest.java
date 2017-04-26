package uk.gov.gchq.gaffer.hazelcast.cache;


import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import uk.gov.gchq.gaffer.cache.exception.CacheOperationException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;

public class HazelcastCacheTest {


    private static HazelcastCache<String, Integer> cache;

    @BeforeClass
    public static void setUp() {
        HazelcastInstance instance = Hazelcast.newHazelcastInstance();
        IMap<String, Integer> map = instance.getMap("test");

        cache = new HazelcastCache<>(map);
    }

    @Before
    public void before() throws CacheOperationException {
        cache.clear();
    }

    @Test
    public void shouldThrowAnExceptionIfEntryAlreadyExistsWhenUsingPutSafe(){
        try {
            cache.put("test", 1);
        } catch (CacheOperationException e) {
            fail("Did not expect Exception to occur here");
        }
        try {
            cache.putSafe("test", 1);
            fail();
        } catch (CacheOperationException e) {
            assertEquals("Entry for key test already exists", e.getMessage());
        }
    }

    @Test
    public void shouldThrowExceptionWhenAddingNullKeyToCache() {
        try {
            cache.put(null, 2);
            fail("Expected an exception");
        } catch (CacheOperationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void shouldThrowExceptionIfAddingNullValue() {
        try {
            cache.put("test", null);
            fail("Expected an exception");
        } catch (CacheOperationException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void shouldAddToCache() throws CacheOperationException {

        // when
        cache.put("key", 1);

        // then
        Assert.assertEquals(1, cache.size());
    }

    @Test
    public void shouldReadFromCache() throws CacheOperationException {

        // when
        cache.put("key", 2);

        // then
        Assert.assertEquals(new Integer(2), cache.get("key"));
    }

    @Test
    public void shouldDeleteCachedEntries() throws CacheOperationException {

        // given
        cache.put("key", 3);

        // when
        cache.remove("key");

        // then
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void shouldUpdateCachedEntries() throws CacheOperationException {

        // given
        cache.put("key", 4);

        // when
        cache.put("key", 5);

        // then
        Assert.assertEquals(1, cache.size());
        Assert.assertEquals(new Integer(5), cache.get("key"));
    }

    @Test
    public void shouldRemoveAllEntries() throws CacheOperationException {

        // given
        cache.put("key1", 1);
        cache.put("key2", 2);
        cache.put("key3", 3);

        // when
        cache.clear();

        // then
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void shouldGetAllKeys() throws CacheOperationException {
        cache.put("test1", 1);
        cache.put("test2", 2);
        cache.put("test3", 3);

        assertEquals(3, cache.size());
        assertThat(cache.getAllKeys(), IsCollectionContaining.hasItems("test1", "test2", "test3"));
    }

    @Test
    public void shouldGetAllValues() throws CacheOperationException {
        cache.put("test1", 1);
        cache.put("test2", 2);
        cache.put("test3", 3);
        cache.put("duplicate", 3);

        assertEquals(4, cache.size());
        assertEquals(4, cache.getAllValues().size());

        assertThat(cache.getAllValues(), IsCollectionContaining.hasItems(1, 2, 3));
    }

}
