/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.core.exception;

import lombok.Getter;

/**
 * A {@link RuntimeException} thrown when data integrity problems happen due to state modifying actions.
 */
@Getter
public class PlatformDataIntegrityException extends AbstractPlatformException {

    private final String parameterName;

    public PlatformDataIntegrityException(final String globalisationMessageCode, final String defaultUserMessage,
                                          final Object... defaultUserMessageArgs) {
        super(globalisationMessageCode, defaultUserMessage, defaultUserMessageArgs);
        this.parameterName = null;
    }

    public PlatformDataIntegrityException(final String globalisationMessageCode, final String defaultUserMessage,
                                          final String parameterName, final Object... defaultUserMessageArgs) {
        super(globalisationMessageCode, defaultUserMessage, defaultUserMessageArgs);
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return this.parameterName;
    }
}