package io.github.douira.glsl_transformer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.douira.glsl_transformer.util.CompatUtil;

/**
 * Knows about the various code resource files and can load them.
 */
public class TestResourceManager {
  private static final Map<Path, Resource> RESOURCE_CACHE = new HashMap<>();

  public static enum FileLocation {
    TINY("/tiny.glsl"),
    DIRECTIVE_TEST("/directiveTest.glsl"),
    SHADER("/shader.glsl"),
    EXTERNAL_DECLARATIONS("/externalDeclarations.glsl"),
    KAPPA("/unlicensed/composite3.glsl"),
    BENCHMARK1("/unlicensed/benchmark1.glsl"),
    BENCHMARK2("/unlicensed/benchmark2.glsl"),
    TEST("/unlicensed/test.glsl"),
    TYPE_TEST("/typeTest.glsl"),
    UNIFORM_TEST("/uniformTest.glsl"),
    MATRIX_PARSE_TEST("/glslang-test/matrix.frag"),
    DEEP_EXPRESSION_TEST("/deepExpressionTest.glsl"),
    GRAMMAR_DEBUG("/grammarDebug.glsl");

    Path path;

    private FileLocation(String path) {
      this.path = Paths.get(path);
    }
  }

  public static enum DirectoryLocation {
    GLSLANG_TESTS("/glslang-test", CompatUtil.setOf("ray", "preprocessor"));

    Path path;
    Set<String> excludeInFiles;

    private static final Set<String> GLOBAL_EXCLUDE = CompatUtil.setOf(".ds_store", "disable_");

    private DirectoryLocation(String path, Set<String> excludeInFiles) {
      this.path = Paths.get(path);
      this.excludeInFiles = excludeInFiles;
    }

    private static boolean checkFileNameWith(String fileName, Set<String> excludes) {
      for (var excluded : excludes) {
        if (fileName.contains(excluded)) {
          return false;
        }
      }
      return true;
    }

    public boolean allowPath(Path path) {
      var fileName = path.getFileName().toString().toLowerCase();
      return checkFileNameWith(fileName, GLOBAL_EXCLUDE)
          && checkFileNameWith(fileName, excludeInFiles);
    }
  }

  public static record Resource(Path path, String content) {
    public String getScenarioName() {
      return path().getFileName().toString();
    }
  }

  private TestResourceManager() {
  }

  public static Resource getResource(FileLocation location) {
    return getPathResource(location.path);
  }

  public static Stream<Resource> getDirectoryResources(DirectoryLocation location) {
    var resourcePath = getResourcePath(location.path);
    return assertDoesNotThrow(
        () -> Files.walk(resourcePath),
        "The resource directory at " + resourcePath + " could not be enumerated.")
        .filter(path -> {
          if (!location.allowPath(path)) {
            return false;
          }

          var file = path.toFile();
          if (file.isDirectory()) {
            return false;
          }

          return true;
        })
        .map(TestResourceManager::getPathResource)
        .filter(resource -> resource.content() != null);
  }

  private static Resource getPathResource(Path path) {
    return Optional
        .ofNullable(RESOURCE_CACHE.get(path))
        .orElseGet(() -> new Resource(path, getFileContent(path)));
  }

  private static String getFileContent(Path path) {
    var resourcePath = getResourcePath(path);
    return assertDoesNotThrow(
        () -> {
          try {
            return Files.readString(resourcePath, StandardCharsets.UTF_8);
          } catch (MalformedInputException e) {
            return null;
          }
        },
        "The file at " + resourcePath.toString() + " could not be read.");
  }

  private static Path getResourcePath(Path resource) {
    return getResourcePath(resource.toString());
  }

  private static Path getResourcePath(String resource) {
    try {
      return Paths.get(TestResourceManager.class.getResource(resource).toURI());
    } catch (Exception e) {
      return Paths.get(resource);
    }
  }
}
