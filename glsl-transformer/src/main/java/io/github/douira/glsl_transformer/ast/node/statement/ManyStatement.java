package io.github.douira.glsl_transformer.ast.node.statement;

import java.util.List;
import java.util.stream.Stream;

import io.github.douira.glsl_transformer.ast.*;
import io.github.douira.glsl_transformer.ast.node.basic.ListNode;
import io.github.douira.glsl_transformer.ast.traversal.*;

public abstract class ManyStatement extends Statement implements ListNode<Statement> {
	public final List<Statement> statements;

	public ManyStatement(List<Statement> statements) {
		this.statements = new ChildNodeList<>(statements, this);
	}

	public ManyStatement(Stream<Statement> statements) {
		this.statements = ChildNodeList.collect(statements, this);
	}

	@Override
	public List<Statement> getChildren() {
		return statements;
	}

	@Override
	public StructureType getStructureType() {
		return StructureType.MANY;
	}

	@Override
	public <R> R accept(ASTVisitor<R> visitor) {
		return visitor.aggregateResult(
				super.accept(visitor),
				visitor.visitManyStatement(this),
				statementAccept(visitor));
	}

	@Override
	public void enterNode(ASTListener listener) {
		listener.enterManyStatement(this);
	}

	@Override
	public void exitNode(ASTListener listener) {
		listener.exitManyStatement(this);
	}
}
