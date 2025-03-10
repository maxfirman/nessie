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
package org.projectnessie.quarkus.providers.adapters;

import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.projectnessie.quarkus.config.VersionStoreConfig;
import org.projectnessie.versioned.persist.rocks.ImmutableRocksDbConfig;
import org.projectnessie.versioned.persist.rocks.RocksDbInstance;

@Singleton
public class RocksDbSource {
  @Inject VersionStoreConfig.RocksVersionStoreConfig rocksConfig;

  @Produces
  @Singleton
  RocksDbInstance createRocksDbInstance() {
    RocksDbInstance rocksDbInstance = new RocksDbInstance();
    rocksDbInstance.configure(
        ImmutableRocksDbConfig.builder().dbPath(rocksConfig.getDbPath()).build());
    rocksDbInstance.initialize();
    return rocksDbInstance;
  }

  void close(@Disposes RocksDbInstance rocksDbInstance) {
    rocksDbInstance.close();
  }
}
