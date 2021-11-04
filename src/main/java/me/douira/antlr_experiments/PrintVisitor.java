package me.douira.antlr_experiments;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The print visitor visits the parse tree and generates a list of tokens. These
 * tokens include those contributed by ReplacementNodes that were inserted for
 * program transformation. This is not a listener because we want to explicitly
 * trigger the visits to subtrees.
 */
public class PrintVisitor extends AbstractParseTreeVisitor<Void> {

  private BufferedTokenStream tokenStream;
  private LinkedList<TokenOrInterval> tokenIntervals = new LinkedList<>();

  private PrintVisitor(BufferedTokenStream tokenStream) {
    this.tokenStream = tokenStream;
  }

  public static String printTree(BufferedTokenStream tokenStream, ParseTree tree) {
    return new PrintVisitor(tokenStream).visitAndJoin(tree);
  }

  public String visitAndJoin(ParseTree rootNode) {
    // add the tokens before the root node too
    var rootInterval = rootNode.getSourceInterval();
    addInterval(0, rootInterval.a - 1);

    // visit the whole tree and accumulate tokens and intervals
    visit(rootNode);

    // and also the tokens after the root node
    addInterval(rootInterval.b + 1, tokenStream.size() - 1);

    // convert the list of tokens and intervals into just tokens,
    // and then into their strings
    var builder = new StringBuilder(512); // guessing
    for (var tokenOrInterval : tokenIntervals) {
      for (var token : tokenOrInterval) {
        if (token.getType() != Lexer.EOF) {
          builder.append(token.getText());
          // builder.append(',');
        }
      }
    }
    return builder.toString();
  }

  private void addInterval(int a, int b) {
    if (a > b || a < 0 || b < 0) {
      return;
    }

    Interval interval = Interval.of(a, b);
    addInterval(interval);
  }

  private void addInterval(Interval interval) {
    if (interval.length() == 0) {
      return;
    }
    if (tokenIntervals.isEmpty() || !tokenIntervals.getLast().tryAddInterval(interval)) {
      tokenIntervals.add(new TokenOrInterval(interval));
    }
  }

  @Override
  public Void visitChildren(RuleNode node) {
    final var context = (ParserRuleContext) node.getRuleContext();

    // get the token interval for this node (token indexes)
    final var superInterval = context.getSourceInterval();

    // the index of the token that needs to be fetched next,
    // either by looking at a child or getting the token directly
    var fetchNext = superInterval.a;
    if (context.children != null) {
      for (var child : context.children) {
        // fetch the tokens between the last child (or the start) and this child
        var childInterval = child.getSourceInterval();
        addInterval(fetchNext, childInterval.a - 1);

        // add the tokens from the child's processing
        child.accept(this);

        // replacement nodes have -1,-1 intervals that will mess this up
        if (childInterval.b >= 0) {
          fetchNext = childInterval.b + 1;
        }
      }
    }

    // fetch all remaining tokens
    addInterval(fetchNext, superInterval.b);
    return null;
  }

  @Override
  public Void visitTerminal(TerminalNode node) {
    if (node instanceof StringNode) {
      tokenIntervals.add(new TokenOrInterval(node.getSymbol()));
    } else {
      addInterval(node.getSourceInterval());
    }

    return null;
  }

  private class TokenOrInterval implements Iterable<Token> {
    private Token token;
    private Interval interval;

    public TokenOrInterval(Token token) {
      this.token = token;
    }

    public TokenOrInterval(Interval interval) {
      this.interval = interval;
    }

    public boolean tryAddInterval(Interval other) {
      if (interval == null || interval.disjoint(other) && !interval.adjacent(other)) {
        return false;
      }

      interval = interval.union(other);
      return true;
    }

    public Iterator<Token> iterator() {
      if (interval != null) {
        return tokenStream.getTokens(interval.a, interval.b).iterator();
      } else {
        return List.of(token).iterator();
      }
    }
  }
}
