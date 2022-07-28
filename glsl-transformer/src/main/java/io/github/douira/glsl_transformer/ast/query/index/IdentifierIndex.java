package io.github.douira.glsl_transformer.ast.query.index;

import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.collections4.trie.PatriciaTrie;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.basic.ASTNode;
import io.github.douira.glsl_transformer.ast.node.expression.ReferenceExpression;

/**
 * Indexes identifiers based on their content and enabled fast string queries.
 */
public class IdentifierIndex<I extends PatriciaTrie<Set<Identifier>>>
    implements Index<Identifier>, PrefixQueryable<Identifier> {
  public final I index;

  public IdentifierIndex(I index) {
    this.index = index;
  }

  @Override
  public void add(Identifier node) {
    var name = node.getName();
    var set = index.get(name);
    if (set == null) {
      set = new HashSet<>();
      index.put(name, set);
    }
    set.add(node);
  }

  @Override
  public void remove(Identifier node) {
    var name = node.getName();
    var set = index.get(name);
    if (set == null) {
      return;
    }
    set.remove(node);
    if (set.isEmpty()) {
      index.remove(name);
    }
  }

  public void merge(IdentifierIndex<?> other) {
    for (var entry : other.index.entrySet()) {
      var set = index.get(entry.getKey());
      if (set == null) {
        set = new HashSet<>();
        index.put(entry.getKey(), set);
      }
      set.addAll(entry.getValue());
    }
  }

  public Set<Identifier> get(String k) {
    var result = index.get(k);
    return result == null ? Collections.emptySet() : result;
  }

  public Stream<Identifier> getStream(String k) {
    var result = index.get(k);
    return result == null ? Stream.empty() : result.stream();
  }

  public <T extends ASTNode> Stream<T> getAncestors(String k, Class<T> clazz) {
    return getStream(k)
        .map(id -> id.getAncestor(clazz))
        .filter(Objects::nonNull);
  }

  public Stream<ReferenceExpression> getReferenceExpressions(String k) {
    return getStream(k)
        .map(id -> id.getAncestor(ReferenceExpression.class))
        .filter(Objects::nonNull);
  }

  public Identifier getOne(String k) {
    return index.get(k).iterator().next();
  }

  public boolean has(String k) {
    var result = index.get(k);
    return result != null && !result.isEmpty();
  }

  public SortedMap<String, Set<Identifier>> prefixMap(String key) {
    return index.prefixMap(key);
  }

  @Override
  public Stream<Set<Identifier>> prefixQuery(String key) {
    return index.prefixMap(key).values().stream();
  }

  @Override
  public Stream<Identifier> prefixQueryFlat(String key) {
    return prefixQuery(key).flatMap(Set::stream);
  }

  public static IdentifierIndex<PrefixTrie<Identifier>> withPrefix() {
    return new IdentifierIndex<>(new PrefixTrie<>());
  }

  public static IdentifierIndex<PrefixSuffixTrie<Identifier>> withPrefixSuffix() {
    return new IdentifierIndex<>(new PrefixSuffixTrie<>());
  }

  public static IdentifierIndex<PermutermTrie<Identifier>> withPermuterm() {
    return new IdentifierIndex<>(new PermutermTrie<>());
  }
}
