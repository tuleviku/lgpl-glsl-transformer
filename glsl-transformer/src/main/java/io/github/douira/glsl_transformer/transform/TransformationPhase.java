package io.github.douira.glsl_transformer.transform;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.pattern.ParseTreeMatch;
import org.antlr.v4.runtime.tree.pattern.ParseTreePattern;
import org.antlr.v4.runtime.tree.xpath.XPath;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.GLSLParser.DeclarationContext;
import io.github.douira.glsl_transformer.GLSLParser.ExtensionStatementContext;
import io.github.douira.glsl_transformer.GLSLParser.ExternalDeclarationContext;
import io.github.douira.glsl_transformer.GLSLParser.FunctionDefinitionContext;
import io.github.douira.glsl_transformer.GLSLParser.LayoutDefaultsContext;
import io.github.douira.glsl_transformer.GLSLParser.PragmaStatementContext;
import io.github.douira.glsl_transformer.GLSLParser.TranslationUnitContext;
import io.github.douira.glsl_transformer.GLSLParser.VersionStatementContext;
import io.github.douira.glsl_transformer.GLSLParserBaseListener;
import io.github.douira.glsl_transformer.generic.EmptyTerminalNode;
import io.github.douira.glsl_transformer.generic.ExtendedContext;

public abstract class TransformationPhase extends GLSLParserBaseListener {
  private PhaseCollector collector;

  void setParent(PhaseCollector parent) {
    this.collector = parent;
  }

  protected Parser getParser() {
    return collector.getParser();
  }

  protected TranslationUnitContext getRootNode() {
    return collector.getRootNode();
  }

  /**
   * Gets the sibling nodes of a given node. It looks up the parent and then
   * returns the parent's children.
   * 
   * @param node The node to get the siblings for
   * @return The siblings of the given node. {@code null} if the node has no
   *         parent.
   */
  protected static List<ParseTree> getSiblings(ExtendedContext node) {
    var parent = node.getParent();
    return parent == null ? null : parent.children;
  }

  /**
   * Replaces the given node in its parent with a new node generated by parsing
   * the given string with the given method of the parser. See
   * {@link #createLocalRoot(String, ExtendedContext, Function)} for details of
   * creating parsed
   * nodes.
   * 
   * @param node        The node to be replaced
   * @param newContents The string from which a new node is generated
   * @param parseMethod The method with which the string will be parsed
   */
  protected void replaceNode(ExtendedContext node, String newContents,
      Function<GLSLParser, ExtendedContext> parseMethod) {
    var removedIndex = removeNode(node);
    getSiblings(node).add(
        removedIndex, createLocalRoot(newContents, node.getParent(), parseMethod));
  }

  /**
   * Removes the given node from its parent's child list.
   * 
   * @implNote The empty space is filled with an empty terminal node that keeps a
   *           reference to the removed node.
   * @param removeNode The node to remove
   * @return the index of the removed node
   */
  protected int removeNode(ExtendedContext removeNode) {
    // the node needs to be replaced with something to preserve the containing
    // array's length or there's a NullPointerException in the walker
    var children = getSiblings(removeNode);
    var index = children.indexOf(removeNode);
    children.set(index, new EmptyTerminalNode(removeNode));
    removeNode.omitTokens();
    return index;
  }

  protected XPath compilePath(String xpath) {
    return new XPath(getParser(), xpath);
  }

  protected ParseTreePattern compilePattern(String pattern, int rootRule) {
    return getParser().compileParseTreePattern(pattern, rootRule, collector.getLexer());
  }

  /**
   * This method uses a statically constructed xpath so it doesn't need to be
   * repeatedly constructed. The subtrees yielded by the xpath need to start with
   * the rule that the pattern was constructed with or nothing will match.
   * 
   * Adapted from ANTLR's implementation of
   * {@link org.antlr.v4.runtime.tree.pattern.ParseTreePattern#findAll(ParseTree, String)}.
   * 
   * @param tree    The parse tree to find and match in
   * @param xpath   The xpath that leads to a subtree for matching
   * @param pattern The pattern that tests the subtrees for matches
   * @return A list of all matches resulting from the subtrees
   */
  public List<ParseTreeMatch> findAndMatch(ParseTree tree, XPath xpath, ParseTreePattern pattern) {
    var subtrees = xpath.evaluate(tree);
    var matches = new ArrayList<ParseTreeMatch>();
    for (ParseTree sub : subtrees) {
      ParseTreeMatch match = pattern.match(sub);
      if (match.succeeded()) {
        matches.add(match);
      }
    }
    return matches;
  }

  /**
   * Overwrite this method to add a check of if this phase should be run at all.
   * Especially for WalkPhase this is important since it reduces the number of
   * listeners that need to be processed.
   * 
   * @return If the phase should run. {@code true} by default.
   */
  protected boolean isActive() {
    return true;
  }

  /**
   * This method is called right after this phase is collected by the phase
   * collector. It can be used to compile xpaths and pattern matchers.
   * 
   * This method should not be used to initialize state specific to a specific
   * transformation job. That is handled by
   * {@link io.github.douira.glsl_transformer.transform.Transformation}.
   */
  protected void init() {
  }

  /**
   * Parses the given string using the given parser method. Since the parser
   * doesn't know which part of the parse tree any string would be part of, we
   * need to tell it. In many cases multiple methods would produce a correct
   * result. However, this can lead to a truncated parse tree when the resulting
   * node is inserted into a bigger parse tree. The parsing method should be
   * chosen such that when the resulting node is inserted into a parse tree, the
   * tree has the same structure as if it had been parsed as one piece.
   * 
   * For example, the code fragment {@code foo()} could be parsed as a
   * {@code functionCall}, a {@code primaryExpression}, an {@code expression} or
   * other enclosing parse rules. If it's inserted into an expression, it should
   * be parsed as an {@code expression} so that this rule isn't missing from the
   * parse tree. Using the wrong parse method often doesn't matter but it can
   * cause tree matchers to not find the node if they are, for example, looking
   * for an {@code expression} specifically.
   * 
   * All nodes inserted into the parse tree must have properly configured parent
   * references or looking up a node's local root won't work. Other things in
   * ANTLR may also break if non-root nodes are missing their parent references.
   * 
   * @param str         The string to be parsed
   * @param parent      The parent to be set on the node. All nodes will
   *                    eventually end up in the a main tree so some parent will
   *                    be available. Getting the siblings of the new node will
   *                    not work if no parent is set.
   * @param parseMethod The parser method with which the string is parsed
   * @return The resulting parsed node
   */
  public <RuleType extends ExtendedContext> RuleType createLocalRoot(String str, ExtendedContext parent,
      Function<GLSLParser, RuleType> parseMethod) {
    var commonTokenStream = new CommonTokenStream(new GLSLLexer(CharStreams.fromString(str)));
    var node = parseMethod.apply(new GLSLParser(commonTokenStream));
    node.setParent(parent);
    node.makeLocalRoot(commonTokenStream);
    return node;
  }

  /**
   * Shader code is expected to be roughly structured as follows:
   * version, extensions, other directives (#define, #pragma etc.), declarations
   * (layout etc.), functions (void main etc.).
   * 
   * These injection points can be used to insert nodes into the translation
   * unit's child list. An injection will happen before the syntax feature it
   * describes and any that follow it in the list.
   * 
   * @implNote AFTER versions of these points would be the same as the next BEFORE
   *           point in the list.
   */
  public enum InjectionPoint {
    /**
     * Before the #version statement (and all other syntax features by necessity)
     */
    BEFORE_VERSION,

    /**
     * Before the #extension statement, before other directives, declarations and
     * function definitions
     */
    BEFORE_EXTENSIONS,

    /**
     * Before non-extension parsed #-directives such as #pragma, before
     * declarations and function definitions. (after extension statements if they
     * aren't mixed with other directives and directly follow the #version)
     * 
     * TODO: describe what happens to unparsed tokens that are in the stream
     * 
     * @apiNote This is semantically equivalent to AFTER_VERSION if unparsed tokens
     *          are disregarded.
     */
    BEFORE_DIRECTIVES,

    /**
     * Before declarations like layout and struct, before function definitions
     */
    BEFORE_DECLARATIONS,

    /**
     * Before function definitions
     */
    BEFORE_FUNCTIONS,

    /**
     * Before the end of the file, basically the last possible location
     */
    BEFORE_EOF;

    /**
     * A set of the rule contexts that can make up a external declaration that each
     * injection point needs to inject before.
     */
    public Set<Class<? extends ParseTree>> EDBeforeTypes;

    static {
      // builds the injections points' before type sets from the weakest to the
      // strongest (strongest having the most inject-before conditions)
      // BEFORE_VERSION and BEFORE_EOF are handled as special cases

      BEFORE_FUNCTIONS.EDBeforeTypes = Set.of(FunctionDefinitionContext.class);

      BEFORE_DECLARATIONS.EDBeforeTypes = new HashSet<>(BEFORE_FUNCTIONS.EDBeforeTypes);
      BEFORE_DECLARATIONS.EDBeforeTypes.add(LayoutDefaultsContext.class);
      BEFORE_DECLARATIONS.EDBeforeTypes.add(DeclarationContext.class);

      BEFORE_DIRECTIVES.EDBeforeTypes = new HashSet<>(BEFORE_DECLARATIONS.EDBeforeTypes);
      BEFORE_DIRECTIVES.EDBeforeTypes.add(PragmaStatementContext.class);

      BEFORE_EXTENSIONS.EDBeforeTypes = new HashSet<>(BEFORE_DIRECTIVES.EDBeforeTypes);
      BEFORE_EXTENSIONS.EDBeforeTypes.add(ExtensionStatementContext.class);
    }
  }

  private int getInjectionIndex(InjectionPoint location) {
    var rootNode = getRootNode();
    var injectIndex = -1;

    if (location == InjectionPoint.BEFORE_VERSION) {
      injectIndex = rootNode.getChildIndexLike(VersionStatementContext.class);
    } else if (location == InjectionPoint.BEFORE_EOF) {
      injectIndex = rootNode.getChildCount();
    } else {
      var beforeTypes = location.EDBeforeTypes;
      if (beforeTypes == null) {
        throw new Error("A non-special injection point is missing its EDBeforeTypes!");
      }
      do {
        injectIndex++;
        if (rootNode.getChild(injectIndex) instanceof ExternalDeclarationContext externalDeclaration) {
          var child = externalDeclaration.getChild(0);
          if (child instanceof ExtendedContext && beforeTypes.contains(child.getClass())) {
            break;
          }
        }
      } while (injectIndex < rootNode.getChildCount());
    }
    return injectIndex;
  }

  /**
   * Injects the given node into the translation unit context root node at the
   * given injection point. Note that this may break things if used improperly (if
   * breaking the grammar's rules for example).
   * 
   * @implNote Since ANTLR's rule context stores children in an {@link ArrayList},
   *           this operation runs in linear time O(n) with respect to the the
   *           number n of external declarations in the root node.
   * 
   * @param newNode  The new node to be inserted
   * @param location The injection point at which the new node is inserted
   */
  public void injectNode(ParseTree newNode, InjectionPoint location) {
    getRootNode().addChild(getInjectionIndex(location), newNode);
  }

  /**
   * Injects a list of nodes into the translation unit context node. Does the same
   * thing as {@link #injectNode(ParseTree, InjectionPoint)} but with a list of
   * nodes.
   * 
   * @param newNodes The list of nodes to be inserted
   * @param location The injection point at which the new nodes are inserted
   */
  public void injectNodes(Deque<ParseTree> newNodes, InjectionPoint location) {
    var injectIndex = getInjectionIndex(location);
    var rootNode = getRootNode();
    newNodes.descendingIterator()
        .forEachRemaining(newNode -> rootNode.addChild(injectIndex, newNode));
  }

  /**
   * Injects the given string parsed as an external declaration. This is a
   * convenience method since most of the time injected nodes are external
   * declarations.
   * 
   * @see #injectNode(ParseTree, InjectionPoint)
   * @param str      The code fragment to be parsed as an external declaration and
   *                 inserted at the given injection point
   * @param location The injection point at which the new node is inserted
   */
  public void injectExternalDeclaration(String str, InjectionPoint location) {
    injectNode(createLocalRoot(str, getRootNode(), GLSLParser::externalDeclaration), location);
  }
}
