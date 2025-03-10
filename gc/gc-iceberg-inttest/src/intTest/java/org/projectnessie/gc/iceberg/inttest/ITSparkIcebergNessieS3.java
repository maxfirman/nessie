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
package org.projectnessie.gc.iceberg.inttest;

import static org.projectnessie.gc.iceberg.inttest.ITSparkIcebergNessieS3.Step.dml;
import static org.projectnessie.gc.iceberg.inttest.ITSparkIcebergNessieS3.Step.expiredDdl;
import static org.projectnessie.gc.iceberg.inttest.ITSparkIcebergNessieS3.Step.expiredDml;
import static org.projectnessie.gc.iceberg.inttest.ITSparkIcebergNessieS3.TestCase.testCase;
import static org.projectnessie.gc.iceberg.inttest.Util.expire;
import static org.projectnessie.gc.iceberg.inttest.Util.identifyLiveContents;
import static org.projectnessie.gc.identify.CutoffPolicy.numCommits;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.iceberg.CatalogProperties;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.gc.contents.LiveContentSet;
import org.projectnessie.gc.contents.inmem.InMemoryPersistenceSpi;
import org.projectnessie.gc.files.DeleteSummary;
import org.projectnessie.gc.files.FileReference;
import org.projectnessie.gc.files.NessieFileIOException;
import org.projectnessie.gc.iceberg.files.IcebergFiles;
import org.projectnessie.gc.identify.CutoffPolicy;
import org.projectnessie.gc.identify.PerRefCutoffPolicySupplier;
import org.projectnessie.gc.repository.NessieRepositoryConnector;
import org.projectnessie.minio.Minio;
import org.projectnessie.minio.MinioAccess;
import org.projectnessie.minio.MinioExtension;
import org.projectnessie.model.FetchOption;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.LogResponse;
import org.projectnessie.model.LogResponse.LogEntry;
import org.projectnessie.model.Operation;
import org.projectnessie.spark.extensions.NessieSparkSessionExtensions;
import org.projectnessie.spark.extensions.SparkSqlTestBase;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@ExtendWith({MinioExtension.class, SoftAssertionsExtension.class})
public class ITSparkIcebergNessieS3 extends SparkSqlTestBase {

  public static final String S3_BUCKET_URI = "/my/prefix";
  public static final String S3_KEY_PREFIX = S3_BUCKET_URI.substring(1);

  @Minio static MinioAccess minio;

  @InjectSoftAssertions private SoftAssertions soft;

  @BeforeAll
  protected static void useNessieExtensions() {
    conf.set("spark.sql.extensions", NessieSparkSessionExtensions.class.getCanonicalName());
  }

  @Override
  protected String warehouseURI() {
    return minio.s3BucketUri(S3_BUCKET_URI).toString();
  }

  @Override
  protected Map<String, String> sparkHadoop() {
    Map<String, String> r = new HashMap<>();
    r.put("fs.s3.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
    r.put("fs.s3a.access.key", minio.accessKey());
    r.put("fs.s3a.secret.key", minio.secretKey());
    r.put("fs.s3a.endpoint", minio.s3endpoint());
    return r;
  }

  @Override
  protected Map<String, String> nessieParams() {
    Map<String, String> r = new HashMap<>(super.nessieParams());
    r.put(CatalogProperties.FILE_IO_IMPL, "org.apache.iceberg.aws.s3.S3FileIO");
    r.putAll(minio.icebergProperties());

    System.setProperty("aws.region", "us-east-1");
    System.setProperty("aws.s3.endpoint", minio.s3endpoint());
    System.setProperty("aws.s3.accessKey", minio.accessKey());
    System.setProperty("aws.s3.secretAccessKey", minio.secretKey());

    return r;
  }

  @AfterEach
  void purgeS3() {
    ListObjectsV2Request request =
        ListObjectsV2Request.builder().bucket(minio.bucket()).prefix(S3_KEY_PREFIX).build();
    minio.s3Client().listObjectsV2Paginator(request).stream()
        .map(ListObjectsV2Response::contents)
        .filter(contents -> !contents.isEmpty())
        .map(
            contents ->
                contents.stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .collect(Collectors.toList()))
        .forEach(
            keys ->
                minio
                    .s3Client()
                    .deleteObjects(
                        DeleteObjectsRequest.builder()
                            .bucket(minio.bucket())
                            .delete(Delete.builder().objects(keys).build())
                            .build()));
  }

  @Value.Immutable
  interface Step {
    static Step dml(String sql) {
      return dml(null, sql);
    }

    static Step expiredDdl(String sql) {
      return expiredDdl(null, sql);
    }

    static Step expiredDml(String sql) {
      return expiredDml(null, sql);
    }

    static Step dml(String branch, String sql) {
      return ImmutableStep.of(branch, sql, false, false);
    }

    static Step expiredDdl(String branch, String sql) {
      return ImmutableStep.of(branch, sql, true, false);
    }

    static Step expiredDml(String branch, String sql) {
      return ImmutableStep.of(branch, sql, true, true);
    }

    @Value.Parameter(order = 1)
    @Nullable
    @jakarta.annotation.Nullable
    String branch();

    @Value.Parameter(order = 2)
    String sql();

    @Value.Parameter(order = 3)
    boolean expired();

    @Value.Parameter(order = 4)
    boolean expireManifestList();
  }

  @Value.Immutable
  interface TestCase {
    static ImmutableTestCase.Builder testCase() {
      return ImmutableTestCase.builder();
    }

    List<Step> steps();

    Map<String, CutoffPolicy> policies();

    String namespace();
  }

  static Stream<TestCase> testCases() {
    return Stream.of(
        // 1
        testCase()
            .namespace("tc_1")
            .addSteps(expiredDdl("CREATE TABLE nessie.tc_1.tbl_a (id int, name string)"))
            .addSteps(expiredDml("INSERT INTO nessie.tc_1.tbl_a select 23, \"test\""))
            .addSteps(dml("INSERT INTO nessie.tc_1.tbl_a select 24, \"case\""))
            .addSteps(expiredDdl("CREATE TABLE nessie.tc_1.tbl_b (id int, name string)"))
            .addSteps(dml("INSERT INTO nessie.tc_1.tbl_b select 42, \"foo\""))
            .putPolicies("main", numCommits(1))
            .build(),
        // 2
        testCase()
            .namespace("tc_2")
            .addSteps(expiredDdl("CREATE TABLE nessie.tc_2.tbl_a (id int, name string)"))
            .addSteps(dml("INSERT INTO nessie.tc_2.tbl_a select 23, \"test\""))
            // on branch "branch_tc_2"
            .addSteps(
                expiredDdl("branch_tc_2", "CREATE TABLE nessie.tc_2.tbl_b (id int, name string)"))
            .addSteps(dml("branch_tc_2", "INSERT INTO nessie.tc_2.tbl_b select 42, \"foo\""))
            // back on "main"
            .addSteps(dml("INSERT INTO nessie.tc_2.tbl_a select 24, \"case\""))
            .putPolicies("main", numCommits(1))
            .putPolicies("branch_tc_2", numCommits(1))
            .build());
  }

  @ParameterizedTest
  @MethodSource("testCases")
  public void roundTrips(TestCase testCase) throws Exception {

    api.createNamespace()
        .namespace(testCase.namespace())
        .refName(api.getConfig().getDefaultBranch())
        .create();

    try (IcebergFiles icebergFiles =
        IcebergFiles.builder()
            .properties(minio.icebergProperties())
            .hadoopConfiguration(minio.hadoopConfiguration())
            .build()) {

      // Tweak Hadoop... s3 vs s3a... oh my...
      spark
          .sparkContext()
          .hadoopConfiguration()
          .set("fs.s3.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");

      Set<URI> expiredSnapshots = new HashSet<>();
      Set<URI> survivingSnapshots = new HashSet<>();
      int expectedDeletes = 0;

      Set<String> branches = new HashSet<>();
      String currentBranch = initialDefaultBranch.getName();
      branches.add(currentBranch);
      for (Step step : testCase.steps()) {
        String branch = step.branch();
        if (branch == null) {
          branch = initialDefaultBranch.getName();
        }
        if (branches.add(branch)) {
          sql("CREATE BRANCH %s IN nessie", branch);
        }
        if (!branch.equals(currentBranch)) {
          sql("USE REFERENCE %s IN nessie", branch);
          currentBranch = branch;
        }

        sql("%s", step.sql());

        IcebergTable icebergTable = icebergTableFromLastCommit(branch);
        (step.expired() ? expiredSnapshots : survivingSnapshots)
            .add(URI.create(icebergTable.getMetadataLocation()));
        if (step.expired()) {
          expectedDeletes++;
        }
        if (step.expireManifestList()) {
          expectedDeletes++;
        }
      }

      Set<URI> filesBefore = allFiles(icebergFiles);

      PerRefCutoffPolicySupplier cutOffPolicySupplier =
          ref -> testCase.policies().getOrDefault(ref.getName(), CutoffPolicy.NONE);

      Instant maxFileModificationTime = Instant.now();

      // Mark...
      LiveContentSet liveContentSet =
          identifyLiveContents(
              new InMemoryPersistenceSpi(),
              cutOffPolicySupplier,
              NessieRepositoryConnector.nessie(api));
      // ... and sweep
      DeleteSummary deleteSummary = expire(icebergFiles, liveContentSet, maxFileModificationTime);

      Set<URI> filesAfter = allFiles(icebergFiles);
      Set<URI> removedFiles = new TreeSet<>(filesBefore);
      removedFiles.removeAll(filesAfter);

      soft.assertThat(removedFiles).hasSize(expectedDeletes).containsAll(expiredSnapshots);

      soft.assertThat(filesAfter)
          .hasSize(filesBefore.size() - expectedDeletes)
          .containsAll(survivingSnapshots)
          .doesNotContainAnyElementsOf(expiredSnapshots);

      soft.assertThat(deleteSummary)
          .extracting(DeleteSummary::deleted, DeleteSummary::failures)
          .containsExactly((long) expectedDeletes, 0L);

      // Run GC another time, but this time assuming that all commits are live. This triggers a
      // read-attempt against a previously deleted table-metadata, which is equal to "no files
      // from this snapshot". Note: this depends on the cutoff-policies from the test-case.

      // Mark...
      liveContentSet =
          identifyLiveContents(
              new InMemoryPersistenceSpi(),
              ref -> CutoffPolicy.NONE,
              NessieRepositoryConnector.nessie(api));
      // ... and sweep
      deleteSummary = expire(icebergFiles, liveContentSet, maxFileModificationTime);

      soft.assertThat(deleteSummary)
          .extracting(DeleteSummary::deleted, DeleteSummary::failures)
          .containsExactly(0L, 0L);
    }
  }

  private IcebergTable icebergTableFromLastCommit(String branch) throws NessieNotFoundException {
    LogResponse log = api.getCommitLog().refName(branch).maxRecords(1).fetch(FetchOption.ALL).get();
    LogEntry entry = log.getLogEntries().get(0);
    Operation.Put put = (Operation.Put) entry.getOperations().get(0);
    return (IcebergTable) put.getContent();
  }

  private Set<URI> allFiles(IcebergFiles icebergFiles) throws NessieFileIOException {
    try (Stream<FileReference> list =
        icebergFiles.listRecursively(minio.s3BucketUri(S3_BUCKET_URI))) {
      return list.map(FileReference::absolutePath).collect(Collectors.toCollection(TreeSet::new));
    }
  }
}
