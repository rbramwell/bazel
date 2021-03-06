// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.skyframe;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.util.Preconditions;
import com.google.devtools.build.skyframe.QueryableGraph.Reason;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/**
 * {@link WalkableGraph} that looks nodes up in a {@link QueryableGraph}.
 */
public class DelegatingWalkableGraph implements WalkableGraph {
  private final QueryableGraph graph;

  public DelegatingWalkableGraph(QueryableGraph graph) {
    this.graph = graph;
  }

  private NodeEntry getEntryForValue(SkyKey key) throws InterruptedException {
    NodeEntry entry =
        Preconditions.checkNotNull(
            graph.getBatch(null, Reason.WALKABLE_GRAPH_VALUE, ImmutableList.of(key)).get(key),
            key);
    Preconditions.checkState(entry.isDone(), "%s %s", key, entry);
    return entry;
  }

  @Override
  public boolean exists(SkyKey key) throws InterruptedException {
    NodeEntry entry =
        graph.getBatch(null, Reason.EXISTENCE_CHECKING, ImmutableList.of(key)).get(key);
    return entry != null && entry.isDone();
  }

  @Nullable
  @Override
  public SkyValue getValue(SkyKey key) throws InterruptedException {
    return getEntryForValue(key).getValue();
  }

  private static SkyValue getValue(NodeEntry entry) throws InterruptedException {
    return entry.isDone() ? entry.getValue() : null;
  }

  @Override
  public Map<SkyKey, SkyValue> getSuccessfulValues(Iterable<SkyKey> keys)
      throws InterruptedException {
    Map<SkyKey, ? extends NodeEntry> batchGet =
        graph.getBatch(null, Reason.WALKABLE_GRAPH_VALUE, keys);
    Map<SkyKey, SkyValue> result = Maps.newHashMapWithExpectedSize(batchGet.size());
    for (Entry<SkyKey, ? extends NodeEntry> entryPair : batchGet.entrySet()) {
      SkyValue value = getValue(entryPair.getValue());
      if (value != null) {
        result.put(entryPair.getKey(), value);
      }
    }
    return result;
  }

  @Override
  public Map<SkyKey, Exception> getMissingAndExceptions(Iterable<SkyKey> keys)
      throws InterruptedException {
    Map<SkyKey, Exception> result = new HashMap<>();
    Map<SkyKey, ? extends NodeEntry> graphResult =
        graph.getBatch(null, Reason.WALKABLE_GRAPH_VALUE, keys);
    for (SkyKey key : keys) {
      NodeEntry nodeEntry = graphResult.get(key);
      if (nodeEntry == null || !nodeEntry.isDone()) {
        result.put(key, null);
      } else {
        ErrorInfo errorInfo = nodeEntry.getErrorInfo();
        if (errorInfo != null) {
          result.put(key, errorInfo.getException());
        }
      }
    }
    return result;
  }

  @Nullable
  @Override
  public Exception getException(SkyKey key) throws InterruptedException {
    ErrorInfo errorInfo = getEntryForValue(key).getErrorInfo();
    return errorInfo == null ? null : errorInfo.getException();
  }

  @Override
  public Map<SkyKey, Iterable<SkyKey>> getDirectDeps(Iterable<SkyKey> keys)
      throws InterruptedException {
    Map<SkyKey, ? extends NodeEntry> entries =
        graph.getBatch(null, Reason.WALKABLE_GRAPH_DEPS, keys);
    Map<SkyKey, Iterable<SkyKey>> result = new HashMap<>(entries.size());
    for (Entry<SkyKey, ? extends NodeEntry> entry : entries.entrySet()) {
      Preconditions.checkState(entry.getValue().isDone(), entry);
      result.put(entry.getKey(), entry.getValue().getDirectDeps());
    }
    return result;
  }

  @Override
  public Map<SkyKey, Iterable<SkyKey>> getReverseDeps(Iterable<SkyKey> keys)
      throws InterruptedException {
    Map<SkyKey, ? extends NodeEntry> entries =
        graph.getBatch(null, Reason.WALKABLE_GRAPH_RDEPS, keys);
    Map<SkyKey, Iterable<SkyKey>> result = new HashMap<>(entries.size());
    for (Entry<SkyKey, ? extends NodeEntry> entry : entries.entrySet()) {
      Preconditions.checkState(entry.getValue().isDone(), entry);
      result.put(entry.getKey(), entry.getValue().getReverseDeps());
    }
    return result;
  }

}
