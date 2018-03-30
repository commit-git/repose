/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.nodeservice.distributed.servlet

import java.net.InetAddress
import java.util.Collections
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse._

import io.opentracing.mock.MockTracer
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.openrepose.core.services.datastore.distributed.ClusterConfiguration
import org.openrepose.core.services.datastore.distributed.config._
import org.openrepose.core.services.datastore.impl.distributed.CacheRequest.CACHE_URI_PATH
import org.openrepose.core.services.datastore.{Datastore, DatastoreAccessControl, DatastoreService}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FunSpec, Matchers}
import org.springframework.mock.web.{MockHttpServletRequest, MockHttpServletResponse}


@RunWith(classOf[JUnitRunner])
class DistributedDatastoreServletTest extends FunSpec with BeforeAndAfterEach with Matchers with MockitoSugar {

  var mockDatastoreService: DatastoreService = _
  var mockDatastore: Datastore = _
  var distributedDatastoreConfiguration: DistributedDatastoreConfiguration = _
  var mockTracer: MockTracer = _
  var distributedDatastoreServlet: DistributedDatastoreServlet = _
  var servletRequest: MockHttpServletRequest = _
  var servletResponse: HttpServletResponse = _

  override def beforeEach(): Unit = {
    mockDatastoreService = mock[DatastoreService]
    mockDatastore = mock[Datastore]
    Mockito.when(mockDatastoreService.getDefaultDatastore).thenReturn(mockDatastore)
    distributedDatastoreConfiguration = mock[DistributedDatastoreConfiguration]
    mockTracer = new MockTracer
    distributedDatastoreServlet = new DistributedDatastoreServlet(
      mockDatastoreService,
      mock[ClusterConfiguration],
      new DatastoreAccessControl(Collections.emptyList[InetAddress], true),
      distributedDatastoreConfiguration,
      mockTracer,
      "1.zero.V"
    )
    servletRequest = new MockHttpServletRequest
    servletRequest.setRequestURI(CACHE_URI_PATH)
    servletRequest.setProtocol("1.1")
    servletResponse = new MockHttpServletResponse
  }

  describe("Distributed Datastore calls without a Cache Key") {
    val methodsResults = Map(
      // @formatter:off
      "GET"     -> SC_NOT_FOUND,
      "HEAD"    -> SC_NOT_FOUND,   // Head calls Get
      "PUT"     -> SC_BAD_REQUEST, // No data sent
      "PATCH"   -> SC_BAD_REQUEST, // No data sent
      "POST"    -> SC_METHOD_NOT_ALLOWED,
      "TRACE"   -> SC_METHOD_NOT_ALLOWED,
      "CUSTOM"  -> SC_NOT_IMPLEMENTED,
      "BOGUS"   -> SC_NOT_IMPLEMENTED,
      "OPTIONS" -> SC_OK) // This is handled by the parent HTTP Servlet
      // @formatter:on

    methodsResults foreach { case (method, result) =>
      it(s"should build an OpenTracing span for HTTP method $method") {
        servletRequest.setMethod(method)
        distributedDatastoreServlet.service(servletRequest, servletResponse)
        mockTracer.finishedSpans.size shouldEqual 1
        mockTracer.finishedSpans.get(0).operationName() shouldEqual s"$method $CACHE_URI_PATH"
      }

      it(s"should return $result for HTTP method $method") {
        servletRequest.setMethod(method)
        distributedDatastoreServlet.service(servletRequest, servletResponse)
        servletResponse.getStatus shouldBe result
      }
    }
  }
}
