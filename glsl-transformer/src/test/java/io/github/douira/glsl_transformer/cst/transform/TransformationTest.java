package io.github.douira.glsl_transformer.cst.transform;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.douira.glsl_transformer.job_parameter.*;
import io.github.douira.glsl_transformer.test_util.*;

public class TransformationTest extends TestForExecutionOrder {
  @Test
  void testSingleContentConstructor() {
    var transformation = new Transformation<>(
        assertOrderPhase(1, "The phase passed in the constructor should run second."));
    transformation.chainDependency(assertOrderPhase(0, "The second chained phase should run first."));
    manager.addConcurrent(transformation);
    manager.transform("");
    assertEquals(2, nextIndex, "Both run phases should run.");
  }

  @Test
  void testRootAndEndNode() {
    var rootNode = transformation.getRootDepNode();
    var endNode = transformation.getEndDepNode();
    assertTrue(rootNode.getDependencies().contains(endNode),
        "The root node should depend on the end node.");
    assertTrue(endNode.getDependents().contains(rootNode),
        "The end node should have the root node as a dependent.");
  }

  @Test
  void testAddDependency() {
    transformation.addDependency(
        assertOrderPhase(1, "The dependent should run second."),
        assertOrderPhase(0, "The dependency should run first."));
    manager.transform("");
    assertEquals(2, nextIndex, "Both run phases should run.");
  }

  @Test
  void testAddDependent() {
    transformation.addDependent(
        assertOrderPhase(0, "The dependency should run first."),
        assertOrderPhase(1, "The dependent should run second."));
    manager.transform("");
    assertEquals(2, nextIndex, "Both run phases should run");
  }

  @Test
  void testChainDependencyDefault() {
    transformation.chainDependency(
        assertOrderPhase(1, "The first chained dependency should run second."));
    transformation.chainDependency(
        assertOrderPhase(0, "The second chained dependency should run first."));
    manager.transform("");
    assertEquals(2, nextIndex, "Both phases should run.");
  }

  @Test
  void testChainDependencyOnRoot() {
    transformation.addRootDependency(
        assertOrderPhase(2, "The root dependency should run third."));
    transformation.chainDependency(
        assertOrderPhase(1, "The first chained dependency should run second."));
    transformation.chainDependency(
        assertOrderPhase(0, "The second chained dependency should run first."));
    manager.transform("");
    assertEquals(3, nextIndex, "All three phases should run.");
  }

  @Test
  void testChainDependencyDefaultMany() {
    for (int i = 0; i < 10; i++) {
      var localIndex = i;
      transformation.chainDependency(
          assertOrderPhase(10 - localIndex - 1,
              "The dependency added with i=" + localIndex + "should run at index " + (10 - localIndex - 1) + "."));
    }
    manager.transform("");
    assertEquals(10, nextIndex, "All phases should run.");
  }

  @Test
  void testChainDependentDefault() {
    transformation.chainDependent(
        assertOrderPhase(0, "The first chained dependent should run first."));
    transformation.chainDependent(
        assertOrderPhase(1, "The second chained dependent should run second."));
    manager.transform("");
    assertEquals(2, nextIndex, "Both run phases should run.");
  }

  @Test
  void testChainDependentOnEnd() {
    transformation.addEndDependent(
        assertOrderPhase(0, "The end dependent should run first."));
    transformation.chainDependent(
        assertOrderPhase(1, "The first chained dependent should run second."));
    transformation.chainDependent(
        assertOrderPhase(2, "The second chained dependent should run third."));
    manager.planExecutionFor(NonFixedJobParameters.INSTANCE);
    manager.transform("");
    assertEquals(3, nextIndex, "All three run phases should run.");
  }

  @Test
  void testChainDependentDefaultMany() {
    for (int i = 0; i < 10; i++) {
      var localIndex = i;
      transformation.chainDependent(
          assertOrderPhase(localIndex,
              "The dependent added with i=" + localIndex + "should run at index " + localIndex + "."));
    }
    manager.transform("");
    assertEquals(10, nextIndex, "All phases should run.");
  }

  @Test
  void testAddRootAndEndDependency() {
    transformation.addRootDependency(
        assertResetPhase(1, 2, "The root dependency should run in the second level."));
    transformation.addEndDependent(
        assertResetPhase(1, 2, "The end dependent should run in the second level."));
    transformation.appendDependent(incrementRunPhase());
    manager.transform("");
    assertEquals(3, nextIndex, "All phases should run.");
  }

  @Test
  void testAppendDependent() {
    transformation.addEndDependent(incrementRunPhase());
    transformation.addRootDependency(incrementRunPhase());
    transformation.chainDependency(incrementRunPhase());
    transformation.appendDependent(
        assertOrderPhase(0, "The appended phase should run before all other phases."));
    manager.transform("");
    assertEquals(4, nextIndex, "All phases should run.");
  }

  @Test
  void testAppendDependentChaining() {
    transformation.addEndDependent(incrementRunPhase());
    transformation.appendDependent(incrementRunPhase());
    transformation.appendDependent(incrementRunPhase());
    transformation.chainConcurrentDependency(
        assertOrderPhase(0, "The chained concurrent dependency should run before all other phases."));
    manager.transform("");
    assertEquals(4, nextIndex, "All phases should run.");
  }

  @Test
  void testPrependDependency() {
    transformation.addRootDependency(incrementRunPhase());
    transformation.addEndDependent(incrementRunPhase());
    transformation.chainDependent(incrementRunPhase());
    transformation.prependDependency(
        assertOrderPhase(3, "The prepended phase should run after all other phases."));
    manager.transform("");
    assertEquals(4, nextIndex, "All phases should run.");
  }

  @Test
  void testPrependDependencyChaining() {
    transformation.addRootDependency(incrementRunPhase());
    transformation.prependDependency(incrementRunPhase());
    transformation.prependDependency(incrementRunPhase());
    transformation.chainConcurrentDependent(
        assertOrderPhase(3, "The chained concurrent dependent should run after all other phases."));
    manager.transform("");
    assertEquals(4, nextIndex, "All phases should run.");
  }

  @Test
  void testChainConcurrentDependency() {
    transformation.addDependency(
        assertOrderPhase(2, "The dependent should run third."),
        assertResetPhase(0, 1, "The dependency should run in the first level"));
    transformation.chainConcurrentDependency(
        assertResetPhase(0, 1, "The chained concurrent dependency should also run in the first level"));
    manager.transform("");
    assertEquals(3, nextIndex, "All three phases should run.");
  }

  @Test
  void testChainConcurrentDependent() {
    transformation.addDependent(
        assertOrderPhase(0, "The dependency should run first."),
        assertResetPhase(1, 2, "The dependent should run in the second level"));
    transformation.chainConcurrentDependent(
        assertResetPhase(1, 2, "The chained concurrent dependency should also run in the second level"));
    manager.transform("");
    assertEquals(3, nextIndex, "All three phases should run.");
  }

  @Test
  void testChainConcurrentSibling() {
    transformation.addDependency(
        assertOrderPhase(2, "The dependent should run third."),
        assertOrderPhase(0, "The dependency should run first."));
    transformation.chainConcurrentSibling(
        assertOrderPhase(1, "The chained sibling should run second, between the two other phases."));
    manager.transform("");
    assertEquals(3, nextIndex, "All three phases should run.");
  }

  @Test
  void testBadDependency() {
    assertThrows(Error.class, () -> {
      transformation.addEndDependent(RunPhase.withRun(null));
      transformation.chainDependency(RunPhase.withRun(null));
    }, "It should not allow making the end node a dependent");
    assertThrows(Error.class, () -> {
      transformation.addRootDependency(RunPhase.withRun(null));
      transformation.chainDependent(RunPhase.withRun(null));
    }, "It should not allow making the root node a dependency");
  }

  @Test
  void testPreferStaticGraph() {
    var t = new CSTTransformer<>();
    t.addConcurrent(new Transformation<>() {
      {
        chainDependent(incrementRunPhase());
      }

      @Override
      protected void setupGraph() {
        throw new IllegalStateException(
            "setupGraph should not be called if there are static dependencies in the graph.");
      }
    });

    t.transform("", new FullyFixedJobParameters());
    assertEquals(1, nextIndex, "It should run the static dependency.");
  }

  @Test
  void testGraphResetConditional() {
    var t = new CSTTransformer<>();
    t.addConcurrent(new Transformation<>() {
      @Override
      protected void setupGraph() {
        chainDependent(incrementRunPhase());
      }
    });

    t.transform("", new FullyFixedJobParameters());
    assertEquals(1, nextIndex,
        "It should run the conditional dependency once.");

    nextIndex = 0;
    t.transform("", new FullyFixedJobParameters());
    assertEquals(1, nextIndex,
        "It should run the conditional dependency once.");
  }

  @Test
  void testGraphResetStatic() {
    var t = new CSTTransformer<>();
    t.addConcurrent(new Transformation<>() {
      {
        chainDependent(incrementRunPhase());
      }
    });

    t.transform("", new FullyFixedJobParameters());
    assertEquals(1, nextIndex,
        "It should run the static dependency once.");

    nextIndex = 0;
    t.transform("", new FullyFixedJobParameters());
    assertEquals(1, nextIndex,
        "It should run the static dependency once.");
  }

  @Test
  void testConditionalDependency() {
    var a = new Object();
    var b = new Object();
    var t = new CSTTransformer<FixedWrappedParameters<Object>>();
    t.addConcurrent(new Transformation<>() {
      @Override
      protected void setupGraph() {
        chainDependent(incrementRunPhase());
        if (getJobParameters().getContents() == a) {
          chainDependent(incrementRunPhase());
          chainDependent(RunPhase.withRun(() -> nextIndex *= 3));
        } else {
          chainDependent(RunPhase.withRun(() -> nextIndex *= 3));
          chainDependent(incrementRunPhase());
        }
      }
    });

    t.transform("", new FixedWrappedParameters<>(a));
    assertEquals(6, nextIndex,
        "It should run the conditional dependencies in the right order.");

    nextIndex = 0;
    t.transform("", new FixedWrappedParameters<>(b));
    assertEquals(4, nextIndex,
        "It should run the conditional dependencies in the right order.");
  }

  @Test
  void testConditionalNesting() {
    var a = new Object();
    var b = new Object();
    var man = new CSTTransformer<FixedWrappedParameters<Object>>();
    man.addConcurrent(new Transformation<>() {
      @Override
      protected void setupGraph() {
        nextIndex++;
        if (getJobParameters().getContents() == a) {
          chainDependent(new Transformation<>() {
            @Override
            protected void setupGraph() {
              nextIndex++;
            }
          });
        }
      }
    });

    man.planExecutionFor(new FixedWrappedParameters<>(a));
    assertEquals(2, nextIndex,
        "It should do graph setup on the nested transformation.");

    nextIndex = 0;
    man.planExecutionFor(new FixedWrappedParameters<>(b));
    assertEquals(1, nextIndex,
        "It should not do graph setup on the nested transformation.");
  }

  @Test
  void testGraphSetupDeduplication() {
    var t = new Transformation<NonFixedJobParameters>() {
      @Override
      protected void setupGraph() {
        nextIndex++;
      }
    };
    manager.addConcurrent(new Transformation<>() {
      {
        chainDependent(t);
      }
    });
    manager.addConcurrent(new Transformation<>() {
      {
        chainDependent(t);
      }
    });
    manager.planExecutionFor(null);
    assertEquals(1, nextIndex,
        "It should only do graph setup once.");
  }
}
