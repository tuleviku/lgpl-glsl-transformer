package io.github.douira.glsl_transformer.ast;

import java.util.*;

public abstract class ProxyArrayList<T> extends ArrayList<T> {
  public ProxyArrayList() {
  }

  public ProxyArrayList(int initialCapacity) {
    super(initialCapacity);
  }

  public ProxyArrayList(Collection<? extends T> c) {
    super(c);
    notifyAdditionInternal(c);
  }

  protected abstract void notifyAddition(T added);

  void notifyAdditionInternal(T added) {
    if (added != null) {
      notifyAddition(added);
    }
  }

  void notifyAdditionInternal(Collection<? extends T> collection) {
    for (var element : collection) {
      notifyAdditionInternal(element);
    }
  }

  @Override
  public boolean add(T e) {
    var result = super.add(e);
    notifyAdditionInternal(e);
    return result;
  }

  @Override
  public void add(int index, T element) {
    super.add(index, element);
    notifyAdditionInternal(element);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    var result = super.addAll(c);
    if (result) {
      notifyAdditionInternal(c);
    }
    return result;
  }

  @Override
  public boolean addAll(int index, Collection<? extends T> c) {
    var result = super.addAll(index, c);
    if (result) {
      notifyAdditionInternal(c);
    }
    return result;
  }

  @Override
  public T set(int index, T element) {
    var prev = super.set(index, element);
    if (prev != element) {
      notifyAdditionInternal(element);
    }
    return prev;
  }
}
