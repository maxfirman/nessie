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
package org.projectnessie.client.api.ns;

import static java.lang.String.format;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.projectnessie.client.api.GetContentBuilder;
import org.projectnessie.client.api.GetEntriesBuilder;
import org.projectnessie.client.api.GetMultipleNamespacesBuilder;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.client.builder.BaseGetMultipleNamespacesBuilder;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.error.NessieReferenceNotFoundException;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.GetMultipleContentsResponse;
import org.projectnessie.model.GetNamespacesResponse;
import org.projectnessie.model.ImmutableGetNamespacesResponse;
import org.projectnessie.model.Namespace;

/**
 * Supports previous "get multiple namespaces" functionality of the java client over Nessie API v2.
 *
 * <p>API v2 does not have methods dedicated to manging namespaces. Namespaces are expected to be
 * managed as ordinary content objects.
 */
public final class ClientSideGetMultipleNamespaces extends BaseGetMultipleNamespacesBuilder {
  private final NessieApiV2 api;
  private boolean onlyDirectChildren;

  public ClientSideGetMultipleNamespaces(NessieApiV2 api) {
    this.api = api;
  }

  @Override
  public GetMultipleNamespacesBuilder onlyDirectChildren(boolean onlyDirectChildren) {
    this.onlyDirectChildren = onlyDirectChildren;
    return this;
  }

  @Override
  public GetNamespacesResponse get() throws NessieReferenceNotFoundException {
    List<ContentKey> entries;
    try {
      GetEntriesBuilder getEntries = api.getEntries().refName(refName).hashOnRef(hashOnRef);

      String filter = null;
      if (namespace != null && !namespace.isEmpty()) {
        String nsName = namespace.name();
        filter =
            onlyDirectChildren
                ? format(
                    "size(entry.keyElements) == %d && entry.encodedKey.startsWith('%s.')",
                    namespace.getElementCount() + 1, nsName)
                : format(
                    "entry.encodedKey == '%s' || entry.encodedKey.startsWith('%s.')",
                    nsName, nsName);
      } else if (onlyDirectChildren) {
        filter = "size(entry.keyElements) == 1";
      }
      if (filter != null) {
        getEntries.filter(filter);
      }

      entries =
          getEntries.stream()
              .filter(e -> Content.Type.NAMESPACE.equals(e.getType()))
              .map(EntriesResponse.Entry::getName)
              .filter(
                  name ->
                      namespace == null
                          || Namespace.of(name.getElements()).isSameOrSubElementOf(namespace))
              .collect(Collectors.toList());
    } catch (NessieNotFoundException e) {
      throw new NessieReferenceNotFoundException(e.getMessage(), e);
    }

    if (entries.isEmpty()) {
      return GetNamespacesResponse.builder().build();
    }

    GetMultipleContentsResponse content;
    try {
      GetContentBuilder getContent = api.getContent().refName(refName).hashOnRef(hashOnRef);
      entries.forEach(getContent::key);
      content = getContent.getWithResponse();
    } catch (NessieNotFoundException e) {
      throw new NessieReferenceNotFoundException(e.getMessage(), e);
    }

    ImmutableGetNamespacesResponse.Builder builder =
        GetNamespacesResponse.builder().effectiveReference(content.getEffectiveReference());
    content.toContentsMap().values().stream()
        .map(v -> v.unwrap(Namespace.class))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(builder::addNamespaces);

    return builder.build();
  }
}
