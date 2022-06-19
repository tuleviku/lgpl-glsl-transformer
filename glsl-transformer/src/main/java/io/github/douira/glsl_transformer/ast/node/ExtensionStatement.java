package io.github.douira.glsl_transformer.ast.node;

import org.antlr.v4.runtime.Token;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.GLSLParser.ExtensionStatementContext;
import io.github.douira.glsl_transformer.ast.*;
import io.github.douira.glsl_transformer.ast.node.PragmaStatement.*;
import io.github.douira.glsl_transformer.ast.traversal.ASTVisitor;

public class ExtensionStatement extends ExternalDeclaration {
  public String name;
  public ExtensionBehavior behavior;

  public enum ExtensionBehavior implements TokenAssociatedEnum {
    DEBUG(GLSLLexer.NR_REQUIRE),
    ENABLE(GLSLLexer.NR_ENABLE),
    WARN(GLSLLexer.NR_WARN),
    DISABLE(GLSLLexer.NR_DISABLE);

    public int tokenType;

    private ExtensionBehavior(int tokenType) {
      this.tokenType = tokenType;
    }

    @Override
    public int getTokenType() {
      return tokenType;
    }

    public static ExtensionBehavior fromToken(Token token) {
      return TypeUtil.enumFromToken(ExtensionBehavior.values(), token);
    }
  }

  public ExtensionStatement(String name, ExtensionBehavior behavior) {
    this.name = name;
    this.behavior = behavior;
  }

  public static ExtensionStatement from(ExtensionStatementContext ctx) {
    return new ExtensionStatement(
        ctx.extensionName.getText(),
        ExtensionBehavior.fromToken(ctx.extensionBehavior));
  }

  @Override
  public <R> R accept(ASTVisitor<R> visitor) {
    return visitor.visitExtensionStatement(this);
  }
}
