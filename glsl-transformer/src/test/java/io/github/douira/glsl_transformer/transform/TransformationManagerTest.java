package io.github.douira.glsl_transformer.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.annotations.SnapshotName;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import io.github.douira.glsl_transformer.GLSLParser.TranslationUnitContext;
import io.github.douira.glsl_transformer.SnapshotUtil;
import io.github.douira.glsl_transformer.TestResourceManager;
import io.github.douira.glsl_transformer.TestResourceManager.DirectoryLocation;
import io.github.douira.glsl_transformer.TestWithTransformationManager;

@ExtendWith({ SnapshotExtension.class })
public class TransformationManagerTest extends TestWithTransformationManager<NonFixedJobParameters> {
  private Expect expect;
  private Exception storeException;

  @BeforeEach
  void setup() {
    manager = new TransformationManager<>();
    manager.addConcurrent(new Transformation<>(new RunPhase<>() {
      @Override
      protected void run(TranslationUnitContext ctx) {
        injectExternalDeclaration(InjectionPoint.BEFORE_VERSION, "f;");
      }
    }));
  }

  @Test
  void testGetLexer() {
    assertNotNull(manager.getParser(), "It should have a parser");
  }

  @Test
  void testGetParser() {
    assertNotNull(manager.getParser(), "It should have a lexer");
  }

  void assertParseErrorType(Class<? extends RecognitionException> type, Executable executable, String message) {
    assertThrows(ParseCancellationException.class, () -> {
      try {
        executable.execute();
      } catch (ParseCancellationException exception) {
        storeException = exception;
        throw exception;
      }
    });
    assertSame(type, storeException.getCause().getClass(),
        "It should throw a ParseCancellationException with the cause " + type.getSimpleName());
  }

  @Test
  void testTransform() {
    assertEquals(
        "f;a;//present\nb;c;d;",
        manager.transform("a;//present\nb;c;d;"));

    assertParseErrorType(
        InputMismatchException.class, () -> manager.transform(
            "//present"),
        "It should throw on an incomplete input");
    assertParseErrorType(
        NoViableAltException.class, () -> manager.transform(
            "foo"),
        "It should throw when there is no viable alternative while parsing the input");
    assertParseErrorType(
        LexerNoViableAltException.class, () -> manager.transform(
            "§"),
        "It should throw when there is no viable alternative while tokenizing the input");

    // FailedPredicateException is difficult to test and may never actually occur
  }

  @Test
  void testTransformStream() {
    assertEquals(
        "f;a;//present\nb;c;d;",
        manager.transformStream(CharStreams.fromString("a;//present\nb;c;d;")));
  }

  @Test
  @SnapshotName("testGlslangErrors")
  void testGlslangErrors() {
    class CollectingErrorListener extends BaseErrorListener {
      private List<String> errors = new ArrayList<>();

      @Override
      public void syntaxError(
          Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
          String msg, RecognitionException e) throws ParseCancellationException {
        errors.add(
            line + ":" + charPositionInLine + "; " +
                (offendingSymbol == null
                    ? "<no symbol>"
                    : offendingSymbol instanceof CommonToken token
                        ? token.toString(recognizer)
                        : offendingSymbol.toString())
                + "; " + msg + "; " +
                (e == null
                    ? "<no exception>"
                    : e.getClass().getSimpleName() + ":" + e.getMessage()));
      }
    }

    TestResourceManager
        .getDirectoryResources(DirectoryLocation.GLSLANG_TESTS)
        .forEach(resource -> {
          manager = new TransformationManager<>(false);
          var collectingListener = new CollectingErrorListener();
          manager.getLexer().addErrorListener(collectingListener);
          manager.getParser().addErrorListener(collectingListener);

          var content = resource.content();
          var expectScenario = expect.scenario(resource.getScenarioName());

          var result = manager.transform(content);

          if (collectingListener.errors.isEmpty()) {
            assertEquals(content, result, "It should re-print the same string it parsed if there were no errors");
          }

          expectScenario.toMatchSnapshot(
              SnapshotUtil.inputOutputSnapshot(
                  content, String.join("\n", collectingListener.errors)));

        });
  }

  @Test
  void testWithJobParameters() {
    TransformationManager<NonFixedJobParameters> man = new TransformationManager<>();
    assertNull(man.getJobParameters(), "It should start with no job parameters");
    var parameters = new NonFixedJobParameters();
    man.withJobParameters(parameters,
        () -> assertSame(parameters, man.getJobParameters(), "It should contain the job parameters"));
    assertNull(man.getJobParameters(), "It should delete the job parameters after use");
    var result = new NonFixedJobParameters();
    assertSame(result, man.withJobParameters(parameters,
        () -> {
          assertSame(parameters, man.getJobParameters(), "It should contain the job parameters again");
          return result;
        }), "It should return the value of the supplier function");
  }

  /**
   * Example transformation that is tested here. Requirements (not fully
   * specified):
   * - remove existing uniform declarations
   * - add const declarations
   * - add uniform blocks
   */
  @Test
  @SnapshotName("testUniformTransform")
  void testUniformTransform() {
    // TODO
  }
}
