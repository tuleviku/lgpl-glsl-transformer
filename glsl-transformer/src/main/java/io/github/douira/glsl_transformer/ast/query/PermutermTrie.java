package io.github.douira.glsl_transformer.ast.query;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This permuterm trie supports prefix, suffix, infix and inverted infix (suffix
 * + prefix) queries and works by inserting all rotations of the key into the
 * underlying trie. The number of bits used to index an entry is quadratic in
 * the size of the key.
 */
public class PermutermTrie<E> extends DuplicatorTrie<E> {
  public PermutermTrie() {
  }

  public PermutermTrie(Map<? extends String, ? extends E> m) {
    super(m);
  }

  public PermutermTrie(char marker) {
    super(marker);
  }

  public PermutermTrie(Map<? extends String, ? extends E> m, char marker) {
    super(m, marker);
  }

  @Override
  protected void iterateKeyVariations(String key, Consumer<String> consumer) {
    var length = key.length();
    for (var i = 0; i <= length; i++) {
      consumer.accept(key.substring(i) + marker + key.substring(0, i));
    }
  }

  @Override
  protected String prepareKey(Object k) {
    return super.prepareKey(k) + marker;
  }

  /**
   * Returns a stream of all the elements that have a given prefix.
   * 
   * @param prefix the prefix to search for
   * @return the elements that have the prefix
   */
  public Stream<E> prefixQuery(String prefix) {
    return distinctPrefixQuery(marker + sanitizeKey(prefix));
  }

  /**
   * Returns a stream of all the elements that have a given suffix.
   * 
   * @param suffix the suffix to search for
   * @return the elements that have the suffix
   */
  public Stream<E> suffixQuery(String suffix) {
    return distinctPrefixQuery(sanitizeKey(suffix) + marker);
  }

  /**
   * Returns a stream of all the elements that have a given infix (substring).
   * 
   * @param infix the infix to search for
   * @return the elements that have the infix
   */
  public Stream<E> infixQuery(String infix) {
    return distinctPrefixQuery(sanitizeKey(infix));
  }

  /**
   * Returns a stream of all the elements that have a given prefix and suffix.
   * 
   * @param prefix the prefix to search require
   * @param suffix the suffix to search require
   * @return the elements that have the prefix and suffix
   */
  public Stream<E> suffixPrefixQuery(String prefix, String suffix) {
    return distinctPrefixQuery(sanitizeKey(suffix) + marker + sanitizeKey(prefix));
  }
}
