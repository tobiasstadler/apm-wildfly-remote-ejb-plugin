/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.wildfly_remote_ejb;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MapTextHeaderAccessorTest {

    public static final String HEADER_NAME = "headerName";
    public static final String HEADER_VALUE = "headerValue";

    @Test
    void testGetFirstHeaderWithEmptyMap() {
        MapTextHeaderAccessor cut = new MapTextHeaderAccessor(emptyMap());

        assertEquals(null, cut.getFirstHeader(HEADER_NAME));
    }

    @Test
    void testGetFirstHeaderWithSingleEntry() {
        MapTextHeaderAccessor cut = new MapTextHeaderAccessor(singletonMap(HEADER_NAME, HEADER_VALUE));

        assertEquals(HEADER_VALUE, cut.getFirstHeader(HEADER_NAME));
    }

    @Test
    void testGetFirstHeaderWithWithMultipleEntries() {
        Map<String, Object> carrier = new HashMap<>();
        carrier.put("foo", "bar");
        carrier.put(HEADER_NAME, HEADER_VALUE);
        carrier.put("bar", "foo");

        MapTextHeaderAccessor cut = new MapTextHeaderAccessor(carrier);

        assertEquals(HEADER_VALUE, cut.getFirstHeader(HEADER_NAME));
    }

    @Test
    void testAddHeader() {
        Map<String, Object> carrier = new HashMap<>();

        MapTextHeaderAccessor cut = new MapTextHeaderAccessor(carrier);
        cut.addHeader(HEADER_NAME, HEADER_VALUE);

        assertEquals(HEADER_VALUE, carrier.get(HEADER_NAME));
    }
}