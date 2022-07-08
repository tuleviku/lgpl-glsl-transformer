package io.github.douira.glsl_transformer.ast.node.statement.terminal;

import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.traversal.*;

public class ExpressionStatement extends SemiTerminalStatement {
  protected Expression expression;

  public ExpressionStatement(Expression expression) {
    this.expression = setup(expression);
  }

  public Expression getExpression() {
    return expression;
  }

  public void setExpression(Expression expression) {
    updateParents(this.expression, expression);
    this.expression = expression;
  }

  @Override
  public StatementType getStatementType() {
    return StatementType.EXPRESSION;
  }

  @Override
  public <R> R statementAccept(ASTVisitor<R> visitor) {
    return visitor.visitExpressionStatement(this);
  }

  @Override
  public void enterNode(ASTListener listener) {
    super.enterNode(listener);
    listener.enterExpressionStatement(this);
  }

  @Override
  public void exitNode(ASTListener listener) {
    super.exitNode(listener);
    listener.exitExpressionStatement(this);
  }
}
