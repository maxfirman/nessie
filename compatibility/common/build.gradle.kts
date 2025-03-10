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

plugins {
  id("nessie-conventions-server")
  id("nessie-jacoco")
}

extra["maven.name"] = "Nessie - Backward Compatibility - Common"

dependencies {
  api(project(":nessie-client"))
  api(project(":nessie-compatibility-jersey"))
  api(project(":nessie-multi-env-test-engine"))
  implementation(project(":nessie-services"))
  implementation(project(":nessie-versioned-storage-common"))
  implementation(project(":nessie-versioned-storage-store"))
  compileOnly(project(":nessie-versioned-storage-mongodb"))
  compileOnly(project(":nessie-versioned-storage-testextension"))
  compileOnly(libs.mongodb.driver.sync)

  implementation(platform(libs.jersey.bom))
  api(libs.slf4j.api)
  api(libs.logback.classic)
  implementation(libs.maven.core)
  implementation(libs.maven.resolver.provider)
  implementation(libs.maven.resolver.connector.basic)
  implementation(libs.maven.resolver.transport.file)
  implementation(libs.maven.resolver.transport.http)
  implementation(libs.guava)

  // javax/jakarta
  compileOnly(libs.jakarta.enterprise.cdi.api)
  compileOnly(libs.javax.enterprise.cdi.api)

  compileOnly(libs.microprofile.openapi)

  implementation(platform(libs.jackson.bom))
  implementation("com.fasterxml.jackson.core:jackson-annotations")

  api(platform(libs.junit.bom))
  api("org.junit.jupiter:junit-jupiter-api")
  compileOnly("org.junit.jupiter:junit-jupiter-engine")

  testImplementation(libs.mockito.core)
  testImplementation(libs.guava)
  implementation(project(":nessie-versioned-storage-inmemory"))
  implementation(project(":nessie-versioned-storage-rocksdb"))

  testCompileOnly(libs.microprofile.openapi)

  testImplementation("org.junit.platform:junit-platform-testkit")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
  filter {
    // Exclude test-classes for the tests
    excludeTestsMatching("TestNessieCompatibilityExtensions\$*")
  }
}
