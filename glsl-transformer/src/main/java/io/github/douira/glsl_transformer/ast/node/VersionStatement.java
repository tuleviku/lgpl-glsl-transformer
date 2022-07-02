package io.github.douira.glsl_transformer.ast.node;

import org.antlr.v4.runtime.Token;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.GLSLParser.VersionStatementContext;
import io.github.douira.glsl_transformer.ast.*;
import io.github.douira.glsl_transformer.ast.node.basic.ASTNode;
import io.github.douira.glsl_transformer.ast.traversal.ASTVisitor;

public class VersionStatement extends ASTNode {
  public int version;
  public Profile profile; //TODO: nullable

  public enum Profile implements TokenAssociatedEnum {
    CORE(GLSLLexer.NR_CORE),
    COMPATIBILITY(GLSLLexer.NR_COMPATIBILITY),
    ES(GLSLLexer.NR_ES);

    public int tokenType;

    private Profile(int tokenType) {
      this.tokenType = tokenType;
    }

    @Override
    public int getTokenType() {
      return tokenType;
    }

    public static Profile fromToken(Token token) {
      return TypeUtil.enumFromToken(Profile.values(), token);
    }
  }

  public VersionStatement(int version, Profile profile) {
    this.version = version;
    this.profile = profile;
  }

  public VersionStatement(int version) {
    this(version, Profile.CORE);
  }

  public static VersionStatement from(VersionStatementContext ctx) {
    var version = Integer.parseInt(ctx.version.getText());
    return ctx.profile == null
        ? new VersionStatement(version)
        : new VersionStatement(version, Profile.fromToken(ctx.profile));
  }

  @Override
  public <R> R accept(ASTVisitor<R> visitor) {
    return visitor.visitVersionStatement(this);
  }
}
