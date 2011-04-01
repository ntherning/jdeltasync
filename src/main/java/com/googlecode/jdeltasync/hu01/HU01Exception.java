/*
 * Copyright (c) 2011, the JDeltaSync project. All Rights Reserved.
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
package com.googlecode.jdeltasync.hu01;

import com.googlecode.jdeltasync.DeltaSyncException;

/**
 * Exception thrown by {@link HU01Decompressor} when an error occurs in the 
 * decompression.
 */
@SuppressWarnings("serial")
public class HU01Exception extends DeltaSyncException {

    public HU01Exception() {
        super();
    }

    public HU01Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public HU01Exception(String message) {
        super(message);
    }

    public HU01Exception(Throwable cause) {
        super(cause);
    }

}
