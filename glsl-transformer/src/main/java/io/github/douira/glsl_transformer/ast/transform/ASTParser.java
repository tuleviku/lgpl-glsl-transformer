package io.github.douira.glsl_transformer.ast.transform;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;

import io.github.douira.glsl_transformer.*;
import io.github.douira.glsl_transformer.GLSLParser.*;
import io.github.douira.glsl_transformer.ast.data.TypedTreeCache;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.external_declaration.ExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.statement.Statement;
import io.github.douira.glsl_transformer.ast.query.*;
import io.github.douira.glsl_transformer.parser.*;
import io.github.douira.glsl_transformer.parser.EnhancedParser.ParsingStrategy;
import io.github.douira.glsl_transformer.token_filter.TokenFilter;
import io.github.douira.glsl_transformer.util.ParseShape;

public class ASTParser implements ParserInterface {
  private static ASTParser INSTANCE;

  public static ASTParser getInternalInstance() {
    if (INSTANCE == null) {
      INSTANCE = new ASTParser();
    }
    return INSTANCE;
  }

  private EnhancedParser parser = new CachingParser();
  private TypedTreeCache<ASTNode> buildCache = new TypedTreeCache<>();
  private ASTCacheStrategy astCacheStrategy = ASTCacheStrategy.ALL_EXCLUDING_TRANSLATION_UNIT;
  private boolean parseLineDirectives = false;

  public enum ASTCacheStrategy {
    ALL,
    ALL_EXCLUDING_TRANSLATION_UNIT,
    NONE
  }

  public enum ParsingCacheStrategy {
    ALL,
    NONE
  }

  public void setBuildCacheSizeAndClear(int size) {
    buildCache = new TypedTreeCache<>(size);
  }

  public void setParseCacheSizeAndClear(int size) {
    if (parser instanceof CachingParser cachingParser) {
      cachingParser.setParseCacheSizeAndClear(size);
    }
  }

  /**
   * Sets the AST cache strategy. If set to ALL, the parser will cache all
   * generated ASTs. If set to ALL_EXCLUDING_TRANSLATION_UNIT, the parser will
   * cache all generated ASTs except for the TranslationUnit. If set to NONE, the
   * parser will cache nothing.
   * 
   * @param astCacheStrategy the AST cache strategy
   */
  public void setASTCacheStrategy(ASTCacheStrategy astCacheStrategy) {
    this.astCacheStrategy = astCacheStrategy;
  }

  /**
   * Sets the parsing cache strategy. If set to ALL, the parser will cache all
   * parsed strings. If set to NONE, the parser will cache nothing. Only
   * influences how the CST is parsed from the input and not the AST.
   * 
   * @param parsingCacheStrategy the parsing cache strategy
   */
  public void setParsingCacheStrategy(ParsingCacheStrategy parsingCacheStrategy) {
    parser = parsingCacheStrategy == ParsingCacheStrategy.ALL ? new CachingParser() : new EnhancedParser();
  }

  /**
   * Sets whether the AST parser should handle line directives. If set to true,
   * the parser will parse line directives and add them to the AST. If set to
   * false, the parser will ignore line directives and not add them to the AST.
   * 
   * @param parseLineDirectives whether the parser should parse line directives
   */
  public void setParseLineDirectives(boolean parseLineDirectives) {
    this.parseLineDirectives = parseLineDirectives;
  }

  @Override
  public GLSLLexer getLexer() {
    return parser.getLexer();
  }

  @Override
  public GLSLParser getParser() {
    return parser.getParser();
  }

  @Override
  public void setThrowParseErrors(boolean throwParseErrors) {
    parser.setThrowParseErrors(throwParseErrors);
  }

  @Override
  public void setParsingStrategy(ParsingStrategy parsingStrategy) {
    parser.setParsingStrategy(parsingStrategy);
  }

  @Override
  public void setSLLOnly() {
    parser.setSLLOnly();
  }

  @Override
  public void setLLOnly() {
    parser.setLLOnly();
  }

  @Override
  public void setTokenFilter(TokenFilter<?> setTokenFilter) {
    parser.setTokenFilter(setTokenFilter);
  }

  private void setBuilderTokenStream() {
    if (parseLineDirectives) {
      ASTBuilder.setTokenStream(parser.getTokenStream());
    }
  }

  private void unsetBuilderTokenStream() {
    if (parseLineDirectives) {
      ASTBuilder.unsetTokenStream();
    }
  }

  @SuppressWarnings("unchecked")
  private <C extends ParserRuleContext, N extends ASTNode> N parseNodeCachedUncloned(
      String input, ParseShape<C, N> parseShape) {
    return (N) buildCache.cachedGet(input,
        parseShape.ruleType,
        () -> {
          try {
            setBuilderTokenStream();
            return ASTBuilder.build(
                new EmptyRoot(), parser.parse(input, parseShape), parseShape.visitMethod);
          } finally {
            unsetBuilderTokenStream();
          }
        });
  }

  @SuppressWarnings("unchecked") // consistent use of the cache results in the same type
  public <C extends ParserRuleContext, N extends ASTNode> N parseNode(
      String input,
      Root rootInstance,
      ParseShape<C, N> parseShape) {
    if (parseShape.ruleType == TranslationUnitContext.class) {
      throw new IllegalArgumentException("Translation units may not be parsed into another node, that makes no sense.");
    }

    if (astCacheStrategy == ASTCacheStrategy.NONE) {
      try {
        setBuilderTokenStream();
        return ASTBuilder.buildSubtree(
            rootInstance, parser.parse(input, parseShape), parseShape.visitMethod);
      } finally {
        unsetBuilderTokenStream();
      }
    } else {
      // cache and possibly build, always clone to return new trees
      return (N) parseNodeCachedUncloned(input, parseShape).cloneInto(rootInstance);
    }
  }

  @SuppressWarnings("unchecked") // consistent use of the cache results in the same type
  public <C extends ParserRuleContext, N extends ASTNode> N parseNodeSeparate(
      String input,
      ParseShape<C, N> parseShape) {
    if (astCacheStrategy == ASTCacheStrategy.NONE
        || astCacheStrategy == ASTCacheStrategy.ALL_EXCLUDING_TRANSLATION_UNIT
            && parseShape.ruleType == TranslationUnitContext.class) {
      try {
        setBuilderTokenStream();
        return ASTBuilder.build(parser.parse(input, parseShape), parseShape.visitMethod);
      } finally {
        unsetBuilderTokenStream();
      }
    } else {
      return (N) parseNodeCachedUncloned(input, parseShape).cloneSeparate();
    }
  }

  public TranslationUnit parseTranslationUnit(String input) {
    return parseNodeSeparate(input, ParseShape.TRANSLATION_UNIT);
  }

  public ExternalDeclaration parseExternalDeclaration(Root rootInstance, String input) {
    return parseNode(input, rootInstance, ParseShape.EXTERNAL_DECLARATION);
  }

  public Expression parseExpression(Root rootInstance, String input) {
    return parseNode(input, rootInstance, ParseShape.EXPRESSION);
  }

  public Statement parseStatement(Root rootInstance, String input) {
    return parseNode(input, rootInstance, ParseShape.STATEMENT);
  }

  public ExternalDeclaration parseSeparateExternalDeclaration(String input) {
    return parseNodeSeparate(input, ParseShape.EXTERNAL_DECLARATION);
  }

  public Expression parseSeparateExpression(String input) {
    return parseNodeSeparate(input, ParseShape.EXPRESSION);
  }

  public Statement parseSeparateStatement(String input) {
    return parseNodeSeparate(input, ParseShape.STATEMENT);
  }

  public List<ExternalDeclaration> parseExternalDeclarations(Root rootInstance, String... inputs) {
    var nodes = new ArrayList<ExternalDeclaration>(inputs.length);
    for (var input : inputs) {
      nodes.add(parseExternalDeclaration(rootInstance, input));
    }
    return nodes;
  }

  public List<Expression> parseExpression(Root rootInstance, String... inputs) {
    var nodes = new ArrayList<Expression>(inputs.length);
    for (var input : inputs) {
      nodes.add(parseExpression(rootInstance, input));
    }
    return nodes;
  }

  public List<Statement> parseStatements(Root rootInstance, String... inputs) {
    var nodes = new ArrayList<Statement>(inputs.length);
    for (var input : inputs) {
      nodes.add(parseStatement(rootInstance, input));
    }
    return nodes;
  }
}
