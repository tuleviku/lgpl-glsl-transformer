package me.douira.glsl_transformer.iris;

import java.util.LinkedList;
import java.util.List;

import me.douira.glsl_transformer.GLSLParser.DeclarationContext;
import me.douira.glsl_transformer.GLSLParser.ExternalDeclarationContext;
import me.douira.glsl_transformer.GLSLParser.TranslationUnitContext;
import me.douira.glsl_transformer.generic.StringNode;
import me.douira.glsl_transformer.transform.Phase;
import me.douira.glsl_transformer.transform.Transformation;

public class DeclarationReplacement extends Transformation {
  private List<String> declarationNames = new LinkedList<>();

  protected void init() {
    addPhase(new Phase() {
      @Override
      public void enterExternalDeclaration(ExternalDeclarationContext ctx) {
      }
    });

    addPhase(new Phase() {
      @Override
      public void beforeWalk(TranslationUnitContext ctx) {
        ctx.children.add(new StringNode("foo"));
      }

      @Override
      public void enterDeclaration(DeclarationContext ctx) {
        replaceNode(ctx, "bar");

        // doesn't work because there are multiple type qualifiers
        // grammar needs further improvement
        // declarationName = ctx.typeQualifier();
      }
    });
  }
}
