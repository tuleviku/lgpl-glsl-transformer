package io.github.douira.glsl_transformer.ast;

import java.util.List;

public abstract class ListASTNode<Child extends ASTNode>
    extends InnerASTNode
    implements ListNode<Child> {
  public List<Child> children = new ChildNodeList<>(this);

  public ListASTNode(List<Child> children) {
    this.children = children;
  }

  @Override
  public List<Child> getChildren() {
    return children;
  }
}
