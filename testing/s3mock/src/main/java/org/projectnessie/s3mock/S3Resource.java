/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.s3mock;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static javax.ws.rs.core.HttpHeaders.IF_MODIFIED_SINCE;
import static javax.ws.rs.core.HttpHeaders.IF_NONE_MATCH;
import static javax.ws.rs.core.HttpHeaders.IF_UNMODIFIED_SINCE;
import static org.projectnessie.s3mock.util.S3Constants.CONTINUATION_TOKEN;
import static org.projectnessie.s3mock.util.S3Constants.ENCODING_TYPE;
import static org.projectnessie.s3mock.util.S3Constants.LIST_TYPE;
import static org.projectnessie.s3mock.util.S3Constants.MAX_KEYS;
import static org.projectnessie.s3mock.util.S3Constants.RANGE;
import static org.projectnessie.s3mock.util.S3Constants.START_AFTER;
import static org.projectnessie.s3mock.util.S3Constants.X_AMZ_REQUEST_ID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.projectnessie.s3mock.S3Bucket.ListElement;
import org.projectnessie.s3mock.data.BatchDeleteRequest;
import org.projectnessie.s3mock.data.ErrorResponse;
import org.projectnessie.s3mock.data.ImmutableBatchDeleteResponse;
import org.projectnessie.s3mock.data.ImmutableBucket;
import org.projectnessie.s3mock.data.ImmutableBuckets;
import org.projectnessie.s3mock.data.ImmutableDeletedS3Object;
import org.projectnessie.s3mock.data.ImmutableErrorObj;
import org.projectnessie.s3mock.data.ImmutableListAllMyBucketsResult;
import org.projectnessie.s3mock.data.ImmutableS3Object;
import org.projectnessie.s3mock.data.ListAllMyBucketsResult;
import org.projectnessie.s3mock.data.ListBucketResult;
import org.projectnessie.s3mock.data.ListBucketResultBase;
import org.projectnessie.s3mock.data.ListBucketResultV2;
import org.projectnessie.s3mock.data.Owner;
import org.projectnessie.s3mock.data.Prefix;
import org.projectnessie.s3mock.data.Range;
import org.projectnessie.s3mock.data.S3ObjectIdentifier;
import org.projectnessie.s3mock.util.Holder;
import org.projectnessie.s3mock.util.PrefixSpliterator;
import org.projectnessie.s3mock.util.StartAfterSpliterator;

@Path("/")
@jakarta.ws.rs.Path("/")
@Produces(MediaType.APPLICATION_XML)
@Consumes(MediaType.APPLICATION_XML)
public class S3Resource {
  @Inject @jakarta.inject.Inject IcebergS3Mock mockServer;

  private static final Owner TEST_OWNER = Owner.of(42, "nessie-iceberg-s3-mock");

  @Path("ready")
  @jakarta.ws.rs.Path("ready")
  @GET
  @jakarta.ws.rs.GET
  @Produces(MediaType.APPLICATION_JSON)
  @jakarta.ws.rs.Produces(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
  public JsonNode ready() {
    ObjectNode node = new ObjectNode(JsonNodeFactory.instance);
    node.put("ready", true);
    return node;
  }

  @GET
  @jakarta.ws.rs.GET
  public ListAllMyBucketsResult listBuckets() {
    ImmutableBuckets.Builder buckets = ImmutableBuckets.builder();
    mockServer
        .buckets()
        .forEach(
            (name, bucket) ->
                buckets.addBuckets(
                    ImmutableBucket.builder()
                        .name(name)
                        .creationDate(bucket.creationDate())
                        .build()));
    return ImmutableListAllMyBucketsResult.builder()
        .owner(TEST_OWNER)
        .buckets(buckets.build())
        .build();
  }

  @PUT
  @jakarta.ws.rs.PUT
  @Path("/{bucketName:[a-z0-9.-]+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}")
  public Response createBucket(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName) {
    return notImplemented();
  }

  @HEAD
  @jakarta.ws.rs.HEAD
  @Path("/{bucketName:[a-z0-9.-]+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}")
  public Response headBucket(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName) {
    return withBucket(bucketName, b -> Response.ok().build());
  }

  @DELETE
  @jakarta.ws.rs.DELETE
  @Path("/{bucketName:[a-z0-9.-]+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}")
  public Response deleteBucket(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName) {
    return notImplemented();
  }

  @GET
  @jakarta.ws.rs.GET
  @Path("/{bucketName:[a-z0-9.-]+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}")
  // TODO IF   params =  !UPLOADS
  public Response listObjectsInsideBucket(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName,
      @QueryParam("prefix") @jakarta.ws.rs.QueryParam("prefix") String prefix,
      @QueryParam("delimiter")
          @jakarta.ws.rs.QueryParam("delimiter")
          @DefaultValue("/")
          @jakarta.ws.rs.DefaultValue("/")
          String delimiter,
      @QueryParam("marker") @jakarta.ws.rs.QueryParam("marker") String marker,
      @QueryParam(ENCODING_TYPE) @jakarta.ws.rs.QueryParam(ENCODING_TYPE) String encodingType,
      @QueryParam(MAX_KEYS)
          @jakarta.ws.rs.QueryParam(MAX_KEYS)
          @DefaultValue("1000")
          @jakarta.ws.rs.DefaultValue("1000")
          int maxKeys,
      // V2 follows
      @QueryParam(LIST_TYPE)
          @jakarta.ws.rs.QueryParam(LIST_TYPE)
          @DefaultValue("1")
          @jakarta.ws.rs.DefaultValue("1")
          int listType,
      @QueryParam(CONTINUATION_TOKEN) @jakarta.ws.rs.QueryParam(CONTINUATION_TOKEN)
          String continuationToken,
      @QueryParam(START_AFTER) @jakarta.ws.rs.QueryParam(START_AFTER) String startAfter,
      @HeaderParam(X_AMZ_REQUEST_ID) @jakarta.ws.rs.HeaderParam(X_AMZ_REQUEST_ID)
          String requestId) {
    return withBucket(
        bucketName,
        b -> {
          try (Stream<ListElement> listStream = b.lister().list(prefix)) {

            ListBucketResultBase.Builder<?> base;
            ListBucketResult.Builder v1 = null;
            ListBucketResultV2.Builder v2 = null;

            switch (listType) {
              case 1:
                base = v1 = ListBucketResult.builder();
                break;
              case 2:
                base = v2 = ListBucketResultV2.builder();
                break;
              default:
                return Response.status(Status.BAD_REQUEST).build();
            }

            boolean truncated = false;
            String nextMarker = null;
            String nextContinuationToken = null;
            int keyCount = 0;
            Set<String> prefixes = new HashSet<>();
            String lastKey = null;

            Function<String, List<String>> keyElements = key -> Arrays.asList(key.split(delimiter));

            Spliterator<ListElement> split = listStream.spliterator();
            if (continuationToken != null) {
              split = new StartAfterSpliterator<>(split, e -> e.key().equals(continuationToken));
            } else if (startAfter != null) {
              split = new StartAfterSpliterator<>(split, e -> e.key().compareTo(startAfter) >= 0);
            }

            if (prefix != null && !prefix.isEmpty()) {
              List<String> prefixElements = keyElements.apply(prefix);
              Predicate<String> prefixMatch =
                  key -> {
                    List<String> k = keyElements.apply(key);
                    return k.size() >= prefixElements.size()
                        && prefixElements.equals(k.subList(0, prefixElements.size()));
                  };
              split = new PrefixSpliterator<>(split, e -> prefixMatch.test(e.key()));
            }

            Holder<ListElement> current = new Holder<>();
            while (split.tryAdvance(current::set)) {
              if (keyCount == maxKeys) {
                truncated = true;
                nextMarker = lastKey;
                nextContinuationToken = lastKey;
                break;
              }

              String key = current.get().key();
              List<String> elems = keyElements.apply(key);
              String pre = String.join(delimiter, elems.subList(0, elems.size() - 1));

              MockObject obj = current.get().object();
              base.addContents(
                  ImmutableS3Object.builder()
                      .etag(obj.etag())
                      .key(key)
                      .owner(Owner.of(42L, "nobody"))
                      .size(Long.toString(obj.contentLength()))
                      .lastModified(
                          ISO_OFFSET_DATE_TIME.format(
                              ZonedDateTime.ofInstant(
                                  Instant.ofEpochMilli(obj.lastModified()), ZoneId.of("UTC"))))
                      .storageClass(obj.storageClass())
                      .build());
              keyCount++;
              lastKey = key;
              if (prefixes.add(pre)) {
                base.addCommonPrefixes(Prefix.of(pre));
              }
            }

            base.isTruncated(truncated)
                .encodingType(encodingType)
                .maxKeys(maxKeys)
                .name(bucketName);
            ListBucketResultBase result = null;
            switch (listType) {
              case 1:
                result = v1.marker(marker).nextMarker(nextMarker).build();
                break;
              case 2:
                result =
                    v2.keyCount(keyCount)
                        .continuationToken(continuationToken)
                        .nextContinuationToken(nextContinuationToken)
                        .startAfter(startAfter)
                        .build();
                break;
              default:
                throw new IllegalArgumentException();
            }

            return Response.ok(result).build();
          }
        });
  }

  @POST
  @jakarta.ws.rs.POST
  @Path("/{bucketName:[a-z0-9.-]+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}")
  public Response batchDeleteObjects(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName,
      @QueryParam("delete")
          @jakarta.ws.rs.QueryParam("delete")
          @Nullable
          @jakarta.annotation.Nullable
          String deleteMarker,
      @RequestBody BatchDeleteRequest body) {
    return withBucket(
        bucketName,
        b -> {
          ImmutableBatchDeleteResponse.Builder response = ImmutableBatchDeleteResponse.builder();
          for (S3ObjectIdentifier s3ObjectIdentifier : body.objectsToDelete()) {
            if (b.deleter().delete(s3ObjectIdentifier)) {
              response.addDeletedObjects(
                  ImmutableDeletedS3Object.builder()
                      .key(s3ObjectIdentifier.key())
                      .versionId(s3ObjectIdentifier.versionId())
                      .build());
            } else {
              response.addErrors(
                  ImmutableErrorObj.builder()
                      .key(s3ObjectIdentifier.key())
                      .versionId(s3ObjectIdentifier.versionId())
                      .code("NoSuchKey")
                      .build());
            }
          }
          return Response.ok(response.build()).build();
        });
  }

  @HEAD
  @jakarta.ws.rs.HEAD
  @Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  @SuppressWarnings("JavaUtilDate")
  public Response headObject(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName,
      @PathParam("object") @jakarta.ws.rs.PathParam("object") String objectName) {
    return withBucketObject(
        bucketName,
        objectName,
        obj ->
            Response.ok()
                .tag(obj.etag())
                .type(obj.contentType())
                .header("Content-Length", Long.toString(obj.contentLength()))
                .lastModified(new Date(obj.lastModified()))
                .build());
  }

  @DELETE
  @jakarta.ws.rs.DELETE
  @Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  public Response deleteObject(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName,
      @PathParam("object") @jakarta.ws.rs.PathParam("object") String objectName) {
    return withBucket(
        bucketName,
        b -> {
          b.deleter().delete(S3ObjectIdentifier.of(objectName));
          return noContent();
        });
  }

  @GET
  @jakarta.ws.rs.GET
  @Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  @jakarta.ws.rs.Path("/{bucketName:[a-z0-9.-]+}/{object:.+}")
  @Produces(MediaType.WILDCARD)
  // TODO NOT_UPLOADS,
  // TODO NOT_UPLOAD_ID,
  // TODO NOT_TAGGING
  @SuppressWarnings("JavaUtilDate")
  public Response getObject(
      @PathParam("bucketName") @jakarta.ws.rs.PathParam("bucketName") String bucketName,
      @PathParam("object") @jakarta.ws.rs.PathParam("object") String objectName,
      @HeaderParam(RANGE) @jakarta.ws.rs.HeaderParam(RANGE) Range range,
      @HeaderParam(IF_MATCH) @jakarta.ws.rs.HeaderParam(IF_MATCH) List<String> match,
      @HeaderParam(IF_NONE_MATCH) @jakarta.ws.rs.HeaderParam(IF_NONE_MATCH) List<String> noneMatch,
      @HeaderParam(IF_MODIFIED_SINCE) @jakarta.ws.rs.HeaderParam(IF_MODIFIED_SINCE)
          Date modifiedSince,
      @HeaderParam(IF_UNMODIFIED_SINCE) @jakarta.ws.rs.HeaderParam(IF_UNMODIFIED_SINCE)
          Date unmodifiedSince) {
    if (range != null) {
      // TODO Iceberg does this :(    return notImplemented();
    }

    return withBucketObject(
        bucketName,
        objectName,
        obj -> {
          if (unmodifiedSince != null && unmodifiedSince.getTime() > obj.lastModified()) {
            return preconditionFailed();
          }
          if (modifiedSince != null && modifiedSince.getTime() > obj.lastModified()) {
            return notModified(obj.etag());
          }
          if (!match.isEmpty() && !match.contains(obj.etag())) {
            return preconditionFailed();
          }
          if (!noneMatch.isEmpty() && noneMatch.contains(obj.etag())) {
            return notModified(obj.etag());
          }
          StreamingOutput stream = output -> obj.writer().write(range, output);
          return Response.ok(stream)
              .tag(obj.etag())
              .type(obj.contentType())
              .header("Content-Length", Long.toString(obj.contentLength()))
              .lastModified(new Date(obj.lastModified()))
              .build();
        });
  }

  private static Response preconditionFailed() {
    return Response.status(Status.PRECONDITION_FAILED)
        .type(MediaType.APPLICATION_XML_TYPE)
        .entity(ErrorResponse.of("PreconditionFailed", "Precondition Failed"))
        .build();
  }

  private static Response notModified(String etag) {
    // Hint: HTTP/304 MUST NOT contain a message body (as per HTTP RFCs)
    return Response.notModified(etag).build();
  }

  private static Response noContent() {
    return Response.status(Status.NO_CONTENT).build();
  }

  private static Response bucketNotFound() {
    return Response.status(Status.NOT_FOUND)
        .type(MediaType.APPLICATION_XML_TYPE)
        .entity(ErrorResponse.of("NoSuchBucket", "The specified bucket does not exist."))
        .build();
  }

  private static Response keyNotFound() {
    return Response.status(Status.NOT_FOUND)
        .type(MediaType.APPLICATION_XML_TYPE)
        .entity(ErrorResponse.of("NoSuchKey", "The specified key does not exist."))
        .build();
  }

  private static Response notImplemented() {
    return Response.status(Status.NOT_IMPLEMENTED).build();
  }

  private Response withBucket(String bucketName, Function<S3Bucket, Response> worker) {
    S3Bucket bucket = mockServer.buckets().get(bucketName);
    if (bucket == null) {
      return bucketNotFound();
    }
    return worker.apply(bucket);
  }

  private Response withBucketObject(
      String bucketName, String objectName, Function<MockObject, Response> worker) {
    return withBucket(
        bucketName,
        bucket -> {
          MockObject o = bucket.object().retrieve(objectName);
          if (o == null) {
            return keyNotFound();
          }
          return worker.apply(o);
        });
  }
}
