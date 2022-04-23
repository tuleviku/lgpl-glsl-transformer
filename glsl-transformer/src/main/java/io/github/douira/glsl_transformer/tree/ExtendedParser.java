package io.github.douira.glsl_transformer.tree;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * This class is used as the base parser class in code generated by ANTLR. It
 * overrides the terminal node creation method in order to create extended
 * terminal nodes that have additional functionality.
 */
public abstract class ExtendedParser extends Parser {
  /**
   * Creates a new extended parser. This is simply to fulfill the expected
   * constructor signature.
   * 
   * @param input The input token stream to parse
   */
  public ExtendedParser(TokenStream input) {
    super(input);
  }

  @Override
  public TerminalNode createTerminalNode(ParserRuleContext parent, Token token) {
    return new ExtendedTerminalNode(parent, token);
  }
}
