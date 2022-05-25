package io.github.douira.glsl_transformer.transform;

import static io.github.douira.glsl_transformer.util.ConfigUtil.*;

import java.util.*;
import java.util.function.*;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.pattern.*;
import org.antlr.v4.runtime.tree.xpath.XPath;

import io.github.douira.glsl_transformer.*;
import io.github.douira.glsl_transformer.GLSLParser.*;
import io.github.douira.glsl_transformer.ast.Directive;
import io.github.douira.glsl_transformer.ast.Directive.Type;
import io.github.douira.glsl_transformer.print.EmptyTerminalNode;
import io.github.douira.glsl_transformer.tree.*;

/**
 * The transformation phase base class already contains most of the
 * functionality of a regular transformation phase but can't be used as an item
 * in the execution planner. This class contains all the basic functionality for
 * transforming the parse tree but without the interface that the execution
 * planner uses.
 * 
 * @see TransformationPhase
 */
public abstract class TransformationPhaseBase<T extends JobParameters> extends GLSLParserBaseListener
    implements ActivatableLifecycleUser<T> {
  private ExecutionPlanner<T> planner;
  private Supplier<Boolean> activation;
  private boolean initialized = false;

  @Override
  public TransformationPhaseBase<T> activation(Supplier<Boolean> activation) {
    this.activation = activation;
    return this;
  }

  /**
   * Overwrite this method to add a check of if this phase should be run at all.
   * Especially for WalkPhase this is important since it reduces the number of
   * listeners that need to be processed.
   * 
   * @return If the phase should run. {@code true} by default.
   */
  @Override
  public boolean isActive() {
    return withDefault(activation, true);
  }

  @Override
  public ExecutionPlanner<T> getPlanner() {
    return planner;
  }

  /**
   * This must be called before executing this phase in the context of a specific
   * parse tree.
   * 
   * {@inheritDoc}
   */
  @Override
  public void setPlanner(ExecutionPlanner<T> parent) {
    this.planner = parent;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public void setInitialized() {
    initialized = true;
  }

  /**
   * Gets the sibling nodes of a given node. It looks up the parent and then
   * returns the parent's children.
   * 
   * @param node The node to get the siblings for
   * @return The siblings of the given node. {@code null} if the node has no
   *         parent.
   */
  protected static List<ParseTree> getSiblings(TreeMember node) {
    var parent = node.getParent();
    return parent == null ? null : parent.children;
  }

  /**
   * Replaces the given node in its parent with a new node generated by parsing
   * the given string with the given method of the parser. See
   * {@link #createLocalRoot(String, ExtendedContext, Function)} for details of
   * creating parsed nodes.
   * 
   * @param removeNode  The node to be replaced
   * @param newContent  The string from which a new node is generated
   * @param parseMethod The method with which the string will be parsed
   */
  protected static void replaceNode(TreeMember removeNode, String newContent,
      Function<GLSLParser, ExtendedContext> parseMethod) {
    replaceNode(removeNode,
        createLocalRoot(newContent, removeNode.getParent(), parseMethod));
  }

  /**
   * Replaces the given node in its parent with a new given node. The new node
   * should either be already set up as a local root or be a terminal node tree
   * member.
   * 
   * @param removeNode The node to be removed
   * @param newNode    The new node to take its place
   * @return The index of the removed and new node
   */
  protected static int replaceNode(TreeMember removeNode, TreeMember newNode) {
    var parent = removeNode.getParent();
    if (parent == null) {
      throw new IllegalArgumentException("The root node may not be removed!");
    }

    /*
     * tell the node being replaced which node preceded it, this is to ensure that
     * the dynamic parse tree walker knows where to continue walking if some
     * operation during the walk modifies the array structurally (though it can only
     * be modified in such a way that the item the walker currently is looking at
     * moves further to the end of the array.)
     */
    newNode.setPreviousNode(removeNode);

    var children = parent.children;
    var index = children.indexOf(removeNode);
    newNode.setParent(parent);
    children.set(index, newNode);
    removeNode.processRemoval();
    return index;
  }

  /**
   * Removes the given node from its parent's child list.
   * 
   * @implNote The empty space is filled with an empty terminal node that keeps a
   *           reference to the removed node.
   * @param removeNode The node to remove
   * @return the index of the removed node
   */
  protected static int removeNode(TreeMember removeNode) {
    // the node needs to be replaced with something to preserve the containing
    // array's length or there's a NullPointerException in the walker
    return replaceNode(removeNode, new EmptyTerminalNode());
  }

  /**
   * Compiles the given string as an xpath with the parser.
   * 
   * This method is meant to be used in {@link #init()} for initializing
   * (effectively) final but phase-specific fields.
   * 
   * @param xpath The string to compile as an xpath
   * @return The compiled xpath
   */
  protected XPath compilePath(String xpath) {
    return new XPath(getParser(), xpath);
  }

  /**
   * Compiles the given string as a parse tree matching pattern what starts
   * matching at the given parser rule. The pattern will not compile or function
   * correctly if the pattern can't be compiled in the context of the given parser
   * rule. See ANTLR's documentation on how tree matching patterns work. (there is
   * special syntax that should be used for extracting)
   * 
   * The resulting pattern will need to be applied to nodes that exactly match the
   * given root rule of the pattern. For finding nodes at any depth and then
   * matching,
   * {@link #findAndMatch(ParseTree, XPath, ParseTreePattern)} can be used.
   * 
   * This method is meant to be used in {@link #init()} for initializing
   * (effectively) final but phase-specific fields.
   * 
   * @param pattern  The string to compile as a tree matching pattern.
   * @param rootRule The parser rule to compile the pattern as
   * @return The compiled pattern
   */
  protected ParseTreePattern compilePattern(String pattern, int rootRule) {
    return getParser().compileParseTreePattern(pattern, rootRule, getLexer());
  }

  /**
   * This method uses a statically constructed xpath, so it doesn't need to be
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
  protected static List<ParseTreeMatch> findAndMatch(ParseTree tree, XPath xpath, ParseTreePattern pattern) {
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
   * parse tree. Using the wrong parse method often doesn't matter, but it can
   * cause tree matchers to not find the node if they are, for example, looking
   * for an {@code expression} specifically.
   * 
   * All nodes inserted into the parse tree must have properly configured parent
   * references or looking up a node's local root won't work. Other things in
   * ANTLR may also break if non-root nodes are missing their parent references.
   * 
   * @param <RuleType>  The type of the resulting parsed node
   * @param str         The string to be parsed
   * @param parent      The parent to be set on the node. All nodes will
   *                    eventually end up in the a main tree so some parent will
   *                    be available. Getting the siblings of the new node will
   *                    not work if no parent is set.
   * @param parseMethod The parser method with which the string is parsed
   * @return The resulting parsed node
   */
  protected static <RuleType extends ExtendedContext> RuleType createLocalRoot(
      String str, ExtendedContext parent,
      Function<GLSLParser, RuleType> parseMethod) {
    var node = TransformationManager.INTERNAL.parse(str, parent, parseMethod);
    node.makeLocalRoot(TransformationManager.INTERNAL.tokenStream);
    return node;
  }

  private int getInjectionIndex(InjectionPoint location) {
    var rootNode = getRootNode();
    var injectIndex = -1;

    if (location == InjectionPoint.BEFORE_VERSION) {
      injectIndex = rootNode.getChildIndexLike(VersionStatementContext.class);
      if (injectIndex == rootNode.getChildCount()) {
        injectIndex = 0;
      }
    } else if (location == InjectionPoint.BEFORE_EOF) {
      injectIndex = rootNode.getChildCount();
    } else {
      do {
        injectIndex++;
        if (rootNode.getChild(injectIndex) instanceof ExternalDeclarationContext externalDeclaration
            && externalDeclaration.getChild(0) instanceof ExtendedContext eChild
            && location.checkChildRelevant(eChild.getClass())) {
          break;
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
   * The {@code addChild} method sets the parent on the added node.
   * 
   * @implNote Since ANTLR's rule context stores children in an {@link ArrayList},
   *           this operation runs in linear time O(n) with respect to the the
   *           number n of external declarations in the root node.
   * 
   * @param location The injection point at which the new node is inserted
   * @param newNode  The new node to be inserted
   */
  protected void injectNode(InjectionPoint location, ParseTree newNode) {
    getRootNode().addChild(getInjectionIndex(location), newNode);
  }

  /**
   * Injects a list of nodes into the translation unit context node. Does the same
   * thing as {@link #injectNode(InjectionPoint, ParseTree)} but with a list of
   * nodes.
   * 
   * @param location The injection point at which the new nodes are inserted
   * @param newNodes The list of nodes to be inserted
   */
  protected void injectNodes(InjectionPoint location, Deque<ParseTree> newNodes) {
    var injectIndex = getInjectionIndex(location);
    var rootNode = getRootNode();
    newNodes.descendingIterator()
        .forEachRemaining(newNode -> rootNode.addChild(injectIndex, newNode));
  }

  /**
   * Injects an array of nodes at an injection location.
   * 
   * @see #injectNodes(InjectionPoint, Deque)
   * @param location The injection point at which the new nodes are inserted
   * @param newNodes The list of nodes to be inserted
   */
  protected void injectNodes(InjectionPoint location, ParseTree... newNodes) {
    var injectIndex = getInjectionIndex(location);
    var rootNode = getRootNode();
    for (var i = newNodes.length - 1; i >= 0; i--) {
      rootNode.addChild(injectIndex, newNodes[i]);
    }
  }

  /**
   * Injects the given string parsed as an external declaration. This is a
   * convenience method since most of the time injected nodes are external
   * declarations.
   * 
   * @see #injectNode(InjectionPoint, ParseTree)
   * @param location The injection point at which the new node is inserted
   * @param str      The code fragment to be parsed as an external declaration and
   *                 inserted at the given injection point
   */
  protected void injectExternalDeclaration(InjectionPoint location, String str) {
    injectNode(location, createLocalRoot(str, getRootNode(), GLSLParser::externalDeclaration));
  }

  /**
   * Injects multiple strings parsed as individual external declarations.
   * 
   * @see #injectNode(InjectionPoint, ParseTree)
   * @param location The injection point at which the new nodes are inserted
   * @param str      The strings to parse as external declarations and then insert
   */
  protected void injectExternalDeclarations(InjectionPoint location, String... str) {
    var nodes = new ParseTree[str.length];
    var rootNode = getRootNode();
    for (var i = 0; i < str.length; i++) {
      nodes[i] = createLocalRoot(str[i], rootNode, GLSLParser::externalDeclaration);
    }
    injectNodes(location, nodes);
  }

  /**
   * Injects a new {@code #define} statement at the specified location. This
   * method is for convenience since injecting defines is a common operation. For
   * other directives the {@link io.github.douira.glsl_transformer.ast.Directive }
   * class should be used.
   * 
   * @apiNote This method should be avoided if a direct replacement of identifiers
   *          using the appropriate core transformations is possible.
   * 
   * @param location The injection point at which the new node is inserted
   * @param content  The content after the #define prefix
   */
  protected void injectDefine(InjectionPoint location, String content) {
    injectNode(location, new Directive(Type.DEFINE, content));
  }
}
