/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather.coroutine

import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import com.netflix.spinnaker.clouddriver.scattergather.ScatterGather
import com.netflix.spinnaker.clouddriver.scattergather.ServletScatterGatherRequest
import com.netflix.spinnaker.clouddriver.scattergather.client.ScatteredOkHttpCallFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoroutineScatterGather(
  private val callFactory: ScatteredOkHttpCallFactory
) : ScatterGather {

  private val log = LoggerFactory.getLogger(CoroutineScatterGather::class.java)

  init {
    log.info("Using Coroutine strategy")
  }

  override fun request(request: ServletScatterGatherRequest, reducer: ResponseReducer): ReducedResponse {
    val responses = performScatter(
      callFactory.createCalls(
        UUID.randomUUID().toString(),
        request.targets,
        request.original
      )
    ) ?: throw RuntimeException("Scatter failed to complete all requests")

    return reducer.reduce(responses)
  }

  private fun performScatter(calls: Collection<Call>): List<Response>? {
    return runBlocking(Dispatchers.IO) {
      // TODO(rz): The [ServletScatterGatherRequest] should include config on how long shards have to respond
      withTimeoutOrNull(Duration.ofSeconds(60).toMillis()) {
        calls
          .map { call ->
            async { call.await() }
          }
          .map { it.await() }
      }
    }
  }
}

private suspend fun Call.await(): Response {
  return suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
      cancel()
    }

    enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resume(response)
      }
    })
  }
}
