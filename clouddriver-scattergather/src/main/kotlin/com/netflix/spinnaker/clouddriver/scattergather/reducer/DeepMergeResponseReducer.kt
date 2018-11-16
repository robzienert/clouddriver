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
package com.netflix.spinnaker.clouddriver.scattergather.reducer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.base.Splitter
import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import okhttp3.Response

/**
 * Performs a recursive merge across responses.
 *
 * Conflict resolution is last-one-wins, where responses are ordered by the client.
 */
class DeepMergeResponseReducer : ResponseReducer {

  private val splitter = Splitter.on(",")
  private val objectMapper = ObjectMapper()

  /**
   * TODO(rz): Handle errors
   */
  override fun reduce(responses: List<Response>): ReducedResponse {
    requireAllSuccessful(responses)

    val headers = mergeHeaders(responses)
    val contentType = headers.getOrDefault("Content-Type", "application/json")
    val characterEncoding = "UTF-8" // TODO(rz): It just is, let's say.

    // TODO(rz): Handle 204 requests and that sort of thing; stuff that won't have bodies

    val body = mergeResponseBodies(responses)

    return ReducedResponse(
      responses.first().code(),
      mergeHeaders(responses),
      contentType,
      characterEncoding,
      body.toString(),
      false
    )
  }

  private fun mergeResponseBodies(responses: List<Response>): JsonNode {
    val main = objectMapper.readTree(responses.first().body()?.string())
    if (responses.size == 1) {
      return main
    }

    // TODO(rz): This is bad.
    responses.filterNot { it == responses.first() }.forEach {
      mergeNodes(main, objectMapper.readTree(it.body()?.string()))
    }
    return main
  }

  // This code was lifted from stack overflow...
  private fun mergeNodes(mainNode: JsonNode, updateNode: JsonNode?): JsonNode {
    if (updateNode == null) {
      return mainNode
    }

    val fieldNames = updateNode.fieldNames()
    while (fieldNames.hasNext()) {
      val updatedFieldName = fieldNames.next()
      val valueToBeUpdated = mainNode.get(updatedFieldName)
      val updatedValue = updateNode.get(updatedFieldName)

      // If the node is an @ArrayNode
      if (valueToBeUpdated != null && valueToBeUpdated is ArrayNode && updatedValue.isArray) {
        updatedValue.forEachIndexed { index, updatedChildNode ->
          // Create a new Node in the node that should be updated, if there was no corresponding node in it
          // Use case: Where the updateNode will have a new element in its Array
          if (valueToBeUpdated.size() <= index) {
            valueToBeUpdated.add(updatedChildNode)
          }
          // getting reference for the node to be updated
          val childNodeToBeUpdated = valueToBeUpdated.get(index)
          mergeNodes(childNodeToBeUpdated, updatedChildNode)
        }
      } else if (valueToBeUpdated != null && valueToBeUpdated.isObject) {
        mergeNodes(valueToBeUpdated, updatedValue)
      } else {
        // TODO(rz): Replace is not correct behavior
        if (mainNode is ObjectNode) {
          mainNode.replace(updatedFieldName, updatedValue)
        }
      }
    }
    return mainNode
  }

  private fun requireAllSuccessful(responses: List<Response>) {
    val failed = responses.filter { !it.isSuccessful }
    if (failed.isNotEmpty()) {
      // TODO(rz): Need a whole lot better diagnostics here.
      // TODO(rz): Should really return a ReducedResponse where the isError flag is true
      throw RuntimeException("Failed requests from shards")
    }
  }

  /**
   * TODO(rz): Merging headers in the way that I'm doing here is probably wildly incorrect. Many of these will not
   * merge well (take for example, timestamps and the like).
   */
  private fun mergeHeaders(responses: List<Response>): Map<String, String> {

    // TODO(rz): A whole lot of duplicates that don't need to be duplicates.
    return mapOf<String, String>()

//    responses.forEach { response ->
//      response.headers().toMultimap().forEach { (key, values) ->
//        if (headers.containsKey(key)) {
//          if (values is Collection<*>) {
//            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
//            headers[key] = splitter.splitToList(headers[key])
//              .also { it.addAll(values) }
//              .asSequence()
//              .distinct()
//              .joinToString(",")
//          }
//        } else {
//          headers[key] = values.joinToString(",")
//        }
//      }
//    }
//    return headers
  }
}
