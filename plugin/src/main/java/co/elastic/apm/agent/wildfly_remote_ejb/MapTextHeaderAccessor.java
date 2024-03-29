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

import co.elastic.apm.api.HeaderExtractor;
import co.elastic.apm.api.HeaderInjector;

import java.util.Map;

public final class MapTextHeaderAccessor implements HeaderInjector, HeaderExtractor {

    private final Map<String, Object> carrier;

    public MapTextHeaderAccessor(Map<String, Object> carrier) {
        this.carrier = carrier;
    }

    @Override
    public String getFirstHeader(String headerName) {
        return (String) carrier.get(headerName);
    }

    @Override
    public void addHeader(String headerName, String headerValue) {
        carrier.put(headerName, headerValue);
    }
}
