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
import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import okhttp3.Response
import org.springframework.http.HttpStatus

/**
 * Performs a recursive merge across responses.
 *
 * Elements inside of an array will not be recursed. If two responses have the same
 * key mapped to an array, the elements from the second response will be appended,
 * removing any duplicate objects, but there will be no recursion of the array
 * elements themselves.
 *
 * Conflict resolution is last-one-wins, where responses are ordered by the client.
 */
class DeepMergeResponseReducer : ResponseReducer {

  private val objectMapper = ObjectMapper()

  /**
   * TODO(rz): Handle errors
   */
  override fun reduce(responses: List<Response>): ReducedResponse {
    // TODO(rz): 404's, 429's... all of these things are legit to pass back to the client.
//    requireAllSuccessful(responses)

    val body = mergeResponseBodies(responses)

    return ReducedResponse(
      getResponseCode(responses),
      mapOf(), // TODO(rz): Not really sure what to do about headers at this point
      "application/json",
      "UTF-8",
      body?.toString(),
      hasErrors(responses)
    )
  }

  private fun mergeResponseBodies(responses: List<Response>): JsonNode? {
    val mainBody = responses.first().body()?.string() ?: return null
    val main = objectMapper.readTree(mainBody)
    if (responses.size == 1) {
      return main
    }

    // TODO(rz): This is bad.
    responses
      .asSequence()
      .filterNot { it == responses.first() }
      .map { it.body()?.string() }
      .filterNotNull()
      .toList()
      .forEach {
        mergeNodes(main, objectMapper.readTree(it))
      }

    return main
  }

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
          if (!valueToBeUpdated.contains(updatedChildNode)) {
            valueToBeUpdated.add(updatedChildNode)
          }
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

  /**
   * TODO(rz): The heuristics in this method don't seem totally sane.
   */
  private fun getResponseCode(responses: List<Response>): Int {
    if (hasErrors(responses)) {
      return HttpStatus.BAD_GATEWAY.value()
    }

    val distinctCodes = responses.asSequence().map { it.code() }.distinct().toList()
    if (distinctCodes.size == 1) {
      return distinctCodes[0]
    }
    if (distinctCodes.all { it in 200..299 }) {
      return HttpStatus.OK.value()
    }
    if (distinctCodes.any { it == 404 }) {
      return HttpStatus.NOT_FOUND.value()
    }
    if (distinctCodes.any { it == 429 }) {
      return HttpStatus.TOO_MANY_REQUESTS.value()
    }
    return HttpStatus.NON_AUTHORITATIVE_INFORMATION.value()
  }

  private fun hasErrors(responses: List<Response>): Boolean =
    responses.any { it.code() >= 500 }
}
