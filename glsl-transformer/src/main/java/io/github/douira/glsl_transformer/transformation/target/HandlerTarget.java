package io.github.douira.glsl_transformer.transformation.target;

import io.github.douira.glsl_transformer.generic.TreeMember;
import io.github.douira.glsl_transformer.transform.PhaseCollector;
import io.github.douira.glsl_transformer.transform.TransformationPhase;

/**
 * A handler target contains a string to search for and a method that is called
 * to handle finding the string in a parse tree.
 */
public abstract class HandlerTarget extends TransformationPhase {
  private String needle;

  /**
   * Creates a new handler target with the given search string
   * 
   * @param needle The search string
   */
  public HandlerTarget(String needle) {
    this.needle = needle;
  }

  /**
   * Returns the string to search for. This method should be fast as it will be
   * called often.
   * 
   * @return The string to search for
   */
  public String getNeedle() {
    return needle;
  }

  @Override
  public void setCollector(PhaseCollector parent) {
    super.setCollector(parent);
  }

  /**
   * Handles the containing node and token that the string was found in.
   * 
   * @param node  The node that contains the token
   * @param match The identifier that contains the needle
   */
  public abstract void handleResult(TreeMember node, String match);
}
