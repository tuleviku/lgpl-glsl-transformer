package io.github.douira.glsl_transformer.ast.node.external_declaration;

import org.antlr.v4.runtime.Token;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.data.*;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.traversal.*;

public class PragmaStatement extends ExternalDeclaration {
  public enum PragmaType implements TokenTyped {
    DEBUG(GLSLLexer.NR_PRAGMA_DEBUG),
    OPTIMIZE(GLSLLexer.NR_PRAGMA_OPTIMIZE),
    INVARIANT(GLSLLexer.NR_PRAGMA_INVARIANT),
    CUSTOM(GLSLLexer.NR_IDENTIFIER);

    public final int tokenType;

    private PragmaType(int tokenType) {
      this.tokenType = tokenType;
    }

    @Override
    public int getTokenType() {
      return tokenType;
    }

    public static PragmaType fromToken(Token token) {
      return TypeUtil.enumFromToken(PragmaType.values(), token);
    }
  }

  public enum PragmaState implements TokenTyped {
    ON(GLSLLexer.NR_ON),
    OFF(GLSLLexer.NR_OFF),
    ALL(GLSLLexer.NR_ALL);

    public final int tokenType;

    private PragmaState(int tokenType) {
      this.tokenType = tokenType;
    }

    @Override
    public int getTokenType() {
      return tokenType;
    }

    public static PragmaState fromToken(Token token) {
      return TypeUtil.enumFromToken(PragmaState.values(), token);
    }
  }

  public boolean stdGL;
  public PragmaType type;
  public String customName;
  public PragmaState state;

  private PragmaStatement(boolean stdGL, PragmaType type, String customName, PragmaState state) {
    this.stdGL = stdGL;
    this.type = type;
    this.customName = customName;
    this.state = state;
  }

  public PragmaStatement(boolean stdGL, String customPragmaName) {
    this.stdGL = stdGL;
    this.type = PragmaType.CUSTOM;
    this.customName = customPragmaName;
  }

  public PragmaStatement(boolean stdGL, PragmaType type, PragmaState state) {
    this.stdGL = stdGL;
    this.type = type;
    this.state = state;
  }

  @Override
  public ExternalDeclarationType getExternalDeclarationType() {
    return ExternalDeclarationType.PRAGMA_STATEMENT;
  }

  @Override
  public <R> R externalDeclarationAccept(ASTVisitor<R> visitor) {
    return visitor.visitPragmaStatement(this);
  }

  @Override
  public void enterNode(ASTListener listener) {
    super.enterNode(listener);
    // terminal nodes have no children
  }

  @Override
  public void exitNode(ASTListener listener) {
    super.enterNode(listener);
    // terminal nodes have no children
  }

  @Override
  public PragmaStatement clone() {
    return new PragmaStatement(stdGL, type, customName, state);
  }

  @Override
  public PragmaStatement cloneInto(Root root) {
    return (PragmaStatement) super.cloneInto(root);
  }

  @Override
  public PragmaStatement cloneSeparate() {
    return (PragmaStatement) super.cloneSeparate();
  }
}
