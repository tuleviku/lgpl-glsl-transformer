package io.github.douira.glsl_transformer.transformation;

import java.util.HashMap;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

import org.antlr.v4.runtime.tree.pattern.ParseTreePattern;

import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.GLSLParser.ExternalDeclarationContext;
import io.github.douira.glsl_transformer.GLSLParser.FunctionHeaderContext;
import io.github.douira.glsl_transformer.GLSLParser.TranslationUnitContext;
import io.github.douira.glsl_transformer.GLSLParser.VariableIdentifierContext;
import io.github.douira.glsl_transformer.transform.SemanticException;
import io.github.douira.glsl_transformer.transform.Transformation;
import io.github.douira.glsl_transformer.transform.WalkPhase;
import io.github.douira.glsl_transformer.tree.ExtendedContext;

//TODO: treat each found declaration with the same location=0 as the same declaration and replace all of them identically
/**
 * The declaration replacement finds layout declarations and replaces all
 * references to them with function calls and other code.
 */
public class ReplaceDeclarations extends Transformation {
  @Desugar
  private static record Declaration(String type, String name) {
  }

  private Map<String, Declaration> declarations;

  @Override
  protected void resetState() {
    declarations = new HashMap<>();
  }

  /**
   * Creates a new declaration replacement transformation with a walk phase for
   * finding declarations and one for inserting calls to the generated functions.
   */
  public ReplaceDeclarations() {
    addPhase(new WalkPhase() {
      ParseTreePattern declarationPattern;

      @Override
      protected void init() {
        declarationPattern = compilePattern("layout (location = 0) <type:storageQualifier> vec4 <name:IDENTIFIER>;",
            GLSLParser.RULE_externalDeclaration);
      }

      @Override
      public void enterExternalDeclaration(ExternalDeclarationContext ctx) {
        var match = declarationPattern.match(ctx);
        if (match.succeeded()) {
          // check for valid format and add to the list if it is valid
          var type = match.get("type").getText();
          var name = match.get("name").getText();

          if (name == "iris_Position") {
            throw new SemanticException(String.format("Disallowed GLSL declaration with the name \"{0}\"!", name), ctx);
          }

          if (type.equals("in") || type.equals("attribute")) {
            declarations.put(name, new Declaration(type, name));
            removeNode((ExtendedContext) match.getTree());
          }
        }
      }

      @Override
      public void enterFunctionHeader(FunctionHeaderContext ctx) {
        if (ctx.IDENTIFIER().getText().equals("iris_getModelSpaceVertexPosition")) {
          throw new SemanticException(
              String.format("Disallowed GLSL declaration with the name \"{0}\"!", "iris_getModelSpaceVertexPosition"),
              ctx);
        }
      }

      @Override
      protected boolean isActiveAfterWalk() {
        return !declarations.isEmpty();
      }

      @Override
      protected void afterWalk(TranslationUnitContext ctx) {
        // is only run if phase is found to be active
        // TODO: the function content and the new attribute declaration
        injectExternalDeclaration("void iris_getModelSpaceVertexPosition() { }", InjectionPoint.BEFORE_EOF);
        injectExternalDeclaration("layout (location = 0) attribute vec4 iris_Position;", InjectionPoint.BEFORE_FUNCTIONS);
      }
    });

    addPhase(new WalkPhase() {
      @Override
      protected boolean isActive() {
        return !declarations.isEmpty();
      }

      @Override
      public void enterVariableIdentifier(VariableIdentifierContext ctx) {
        // check for one of the identifiers we're looking for
        var identifier = ctx.IDENTIFIER();
        var matchingDeclaration = declarations.get(identifier.getText());
        if (matchingDeclaration != null) {
          // perform replacement of this reference
          replaceNode(ctx, "iris_getModelSpaceVertexPosition()", GLSLParser::expression);
        }
      }
    });
  }
}
