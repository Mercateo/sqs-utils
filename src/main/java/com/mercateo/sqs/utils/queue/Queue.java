/**
 * Copyright © 2017 Mercateo AG (http://www.mercateo.com)
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
package com.mercateo.sqs.utils.queue;

import java.time.Duration;
import java.util.Map;

import lombok.Data;
import lombok.NonNull;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Data
public class Queue {

    @NonNull
    private final QueueName name;

    @NonNull
    private final String url;

    @NonNull
    private final Map<QueueAttributeName, String> queueAttributes;

    public Duration getDefaultVisibilityTimeout() {
        return Duration.ofSeconds(Integer.parseInt(queueAttributes.get(QueueAttributeName.VISIBILITY_TIMEOUT)));
    }
}