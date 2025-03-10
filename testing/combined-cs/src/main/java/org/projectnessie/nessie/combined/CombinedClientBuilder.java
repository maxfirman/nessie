/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.nessie.combined;

import java.security.Principal;
import java.util.function.Supplier;
import org.projectnessie.client.NessieClientBuilder;
import org.projectnessie.client.api.NessieApi;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.services.authz.AbstractBatchAccessChecker;
import org.projectnessie.services.authz.Authorizer;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.services.impl.ConfigApiImpl;
import org.projectnessie.services.impl.ContentApiImpl;
import org.projectnessie.services.impl.DiffApiImpl;
import org.projectnessie.services.impl.TreeApiImpl;
import org.projectnessie.services.rest.RestV2ConfigResource;
import org.projectnessie.services.rest.RestV2TreeResource;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.versionstore.VersionStoreImpl;

/**
 * Builder for a {@link NessieApiV2} client that directly accesses the {@linkplain
 * #withPersist(Persist) provided <code>Persist</code>} instance.
 */
public class CombinedClientBuilder extends NessieClientBuilder.AbstractNessieClientBuilder {
  private Persist persist;

  public CombinedClientBuilder() {}

  @Override
  public String name() {
    return "Combined";
  }

  @Override
  public int priority() {
    return 50;
  }

  public NessieClientBuilder withPersist(Persist persist) {
    this.persist = persist;
    return this;
  }

  @Override
  public <API extends NessieApi> API build(Class<API> apiContract) {
    ServerConfig serverConfig =
        new ServerConfig() {
          @Override
          public String getDefaultBranch() {
            return "main";
          }

          @Override
          public boolean sendStacktraceToClient() {
            return true;
          }
        };

    VersionStore versionStore = new VersionStoreImpl(persist);
    Authorizer authorizer = c -> AbstractBatchAccessChecker.NOOP_ACCESS_CHECKER;
    Supplier<Principal> principalSupplier = () -> null;

    ConfigApiImpl configService =
        new ConfigApiImpl(serverConfig, versionStore, authorizer, principalSupplier, 2);
    TreeApiImpl treeService =
        new TreeApiImpl(serverConfig, versionStore, authorizer, principalSupplier);
    ContentApiImpl contentService =
        new ContentApiImpl(serverConfig, versionStore, authorizer, principalSupplier);
    DiffApiImpl diffService =
        new DiffApiImpl(serverConfig, versionStore, authorizer, principalSupplier);

    RestV2ConfigResource configResource =
        new RestV2ConfigResource(serverConfig, versionStore, authorizer, principalSupplier);
    RestV2TreeResource treeResource =
        new RestV2TreeResource(configService, treeService, contentService, diffService);

    // Optimistic cast...
    @SuppressWarnings("unchecked")
    API r = (API) new CombinedClientImpl(configResource, treeResource);
    return r;
  }
}
