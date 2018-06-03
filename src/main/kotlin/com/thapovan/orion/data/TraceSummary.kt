/*
 * Copyright (c) 2018 Thapovan info Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thapovan.orion.data

import com.google.gson.annotations.Expose

data class TraceSummary(
    @Expose(serialize = true, deserialize = true)val traceId: String,
    @Expose(serialize = true, deserialize = true)var startTime: Long = 0,
    @Expose(serialize = true, deserialize = true)var endTime: Long = 0,
    @Expose(serialize = true, deserialize = true)var email: String? = null,
    @Expose(serialize = true, deserialize = true)var userId: String? = null,
    @Expose(serialize = true, deserialize = true)var serviceNames: MutableList<String> = ArrayList<String>(),
    @Expose(serialize = true, deserialize = true)var traceEventSummary: MutableMap<String,Int> = HashMap(),
    @Expose(serialize = true, deserialize = true)var country: String? = null,
    @Expose(serialize = true, deserialize = true)var ip: String? = null)