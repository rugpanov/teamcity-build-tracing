package jetbrains.buildServer.tracing;

import com.intellij.openapi.util.Pair;
import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.Scope;
import io.opentracing.Span;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.tracing.common.Constants;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static io.jaegertracing.Configuration.SenderConfiguration;

public class ServerTracing extends BuildServerAdapter {
  private static final String DEFAULT_REPORTER_URL = "localhost:5778";
  private final String BUILD_STEP_PREFIX = "buildStageDuration:buildStep";
  private Map<String, JaegerTracer> allTracers = new HashMap<>();

  @NotNull
  private JaegerTracer getTracerForBuild(@NotNull SBuild build) {
    Collection<SBuildFeatureDescriptor> descriptors = build.getBuildFeaturesOfType(TracingDescriptor.BUILD_FEATURE_TYPE);

    if (descriptors.isEmpty()) return getTracer(null, build.getBuildTypeExternalId());
    return getTracer(descriptors.iterator().next().getParameters().get(Constants.REPORTER_URL), build.getBuildTypeExternalId());
  }

  private JaegerTracer getTracer(@Nullable String reporterUrl, @NotNull String configName) {
    reporterUrl = reporterUrl != null ? reporterUrl : DEFAULT_REPORTER_URL;
    final JaegerTracer jaegerTracer = allTracers.get(reporterUrl);
    if (jaegerTracer == null) {
      allTracers.put(reporterUrl, initTracer(reporterUrl, configName));
    }

    return allTracers.get(reporterUrl);
  }

  public ServerTracing(@NotNull final EventDispatcher<BuildServerListener> dispatcher) {
    dispatcher.addListener(this);
  }

  private static JaegerTracer initTracer(@NotNull String reporterUrl, @NotNull String configName) {

    SamplerConfiguration samplerConfig = new SamplerConfiguration().withType(ConstSampler.TYPE)
            .withParam(1)
            .withManagerHostPort(reporterUrl);
//    new SenderConfiguration().withEndpoint(reporterUrl);
    ReporterConfiguration reporterConfig = new ReporterConfiguration().withLogSpans(true).withSender(new SenderConfiguration());
    Configuration config = new Configuration(configName).withSampler(samplerConfig).withReporter(reporterConfig);
    return config.getTracer();
  }

  @Override
  public void buildFinished(@NotNull final SRunningBuild build) {
    long finishTimeinMs = System.currentTimeMillis();
    if (!isTracingEnabled(build)) {
      return;
    }


    long startTime = build.getStartDate().getTime() * 1000;

    try (Scope scope = getTracerForBuild(build)
            .buildSpan("build")
            .withTag("buildId", build.getBuildId())
            .withTag("buildTypeId", build.getBuildTypeId())
            .withTag("projectId", build.getProjectId())
            .withStartTimestamp(startTime)
            .startActive(false)) {
      try {
        doHandle(build, finishTimeinMs);
      } finally {
        scope.span().finish(finishTimeinMs * 1000);
      }
    }
  }

  private void doHandle(SBuild build, long finishTime) {
    final SBuildType buildType = build.getBuildType();

    if (buildType == null) {
      throw new IllegalArgumentException("Unknown build type");
    }

    final Map<String, BigDecimal> statisticValues = build.getStatisticValues();

    final Map<String, String> buildRunners = buildType.getBuildRunners().stream().map(descriptor ->
            new Runner(descriptor.getId(), descriptor.getName().isEmpty()
                    ? descriptor.getRunType().getDisplayName()
                    : descriptor.getName())).collect(Collectors.toMap(r -> r.id, r -> r.name, (e1, e2) -> e2,
            LinkedHashMap::new));

    final List runnersIds = new ArrayList<>(buildRunners.keySet());

    final List<Pair<String, BigDecimal>> passedSteps = statisticValues.entrySet()
            .stream()
            .filter(entry -> entry.getKey().startsWith(BUILD_STEP_PREFIX)).map(entry -> {
              String runnerId = entry.getKey().replace(BUILD_STEP_PREFIX, "");
              return new Pair<>(runnerId, entry.getValue());
            }).sorted((entry1, entry2) -> {
              final Integer i1 = runnersIds.indexOf(entry1.first);
              final Integer i2 = runnersIds.indexOf(entry2.first);
              return i1.compareTo(i2);
            }).collect(Collectors.toList());


    final long start = build.getStartDate().getTime();

    BigDecimal lastStageTime = addStages(build, start, buildRunners, passedSteps, statisticValues);
    BigDecimal finishTimeBigDecimal = new BigDecimal(finishTime);

    addBuildRunnerStage(build, lastStageTime, finishTimeBigDecimal.subtract(lastStageTime), "Not Calculated Yet Finish Stages");
  }

  private BigDecimal addStages(SBuild build,
                               long start,
                               Map<String, String> buildRunners,
                               List<Pair<String, BigDecimal>> passedSteps,
                               Map<String, BigDecimal> statisticValues) {


    BigDecimal currentTime = new BigDecimal(start);

    currentTime = addBuildStage(build, statisticValues, BuildStats.CHECKOUT, currentTime);
    currentTime = addBuildStage(build, statisticValues, BuildStats.ARTIFACT_DEPENDENCY_RESOLVING, currentTime);
    currentTime = addBuildStage(build, statisticValues, BuildStats.PREPARATION, currentTime);

    for (Pair<String, BigDecimal> step : passedSteps) {
      String runnerId = step.first.replace(BUILD_STEP_PREFIX, "");
      String runnerName = buildRunners.get(runnerId);
      currentTime = addBuildRunnerStage(build, currentTime, step.second, runnerName != null ? runnerName : runnerId);
    }

    currentTime = addBuildStage(build, statisticValues, BuildStats.ARTIFACT_PUBLISHING, currentTime);
    currentTime = addBuildStage(build, statisticValues, BuildStats.BUILD_FINISHING, currentTime);

    return currentTime;
  }

  private BigDecimal addBuildRunnerStage(SBuild build,
                                         @NotNull BigDecimal startTime,
                                         @Nullable BigDecimal diff,
                                         @NotNull String name) {
    final JaegerTracer tracer = getTracerForBuild(build);
    Span buildSpan = tracer.activeSpan();
    if (buildSpan == null) {
      return startTime;
    }

    BigDecimal finishTime = diff != null ? startTime.add(diff) : startTime;

    tracer.buildSpan(name)
            .withStartTimestamp(startTime.longValue() * 1000)
            .asChildOf(buildSpan)
            .start()
            .finish(finishTime.longValue() * 1000);

    return finishTime;
  }

  private BigDecimal addBuildStage(SBuild build,
                                   Map<String, BigDecimal> statisticValues,
                                   BuildStats stats,
                                   BigDecimal startTime) {
    final BigDecimal data = statisticValues.get(stats.key);
    if (data == null) {
      return startTime;
    }

    BigDecimal nextTime = startTime.add(data);

    addBuildStage(build, stats, startTime, nextTime);
    return nextTime;
  }

  private void addBuildStage(SBuild build,
                             @NotNull BuildStats stats,
                             @NotNull BigDecimal startTime,
                             @NotNull BigDecimal finishDate) {
    final JaegerTracer tracer = getTracerForBuild(build);
    Span buildSpan = tracer.activeSpan();
    if (buildSpan == null) {
      return;
    }

    getTracerForBuild(build)
            .buildSpan(stats.name)
            .withStartTimestamp(startTime.longValue() * 1000)
            .asChildOf(buildSpan)
            .start()
            .finish(finishDate.longValue() * 1000);

  }

  static class Runner {
    String id;
    String name;

    Runner(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  public enum BuildStats {
    ARTIFACT_DEPENDENCY_RESOLVING("buildStageDuration:dependenciesResolving", "Artifact Dependencies Resolving Time"),
    ARTIFACT_PUBLISHING("buildStageDuration:artifactsPublishing", "Build Artifacts Publishing Time"),
    CHECKOUT("buildStageDuration:sourcesUpdate", "Build Checkout Time"),
    //    BUILD_DURATION("BuildDuration", "Build Duration (all stages)"),
    BUILD_FINISHING("buildStageDuration:buildFinishing", "Build Finishing"),
    PREPARATION("buildStageDuration:firstStepPreparation", "Build Preparation");
//    UNKNOWN("", "Unknown/not yet resolved stage"),
//    COMPOSITE_BUILD_STAGE("COMPOSITE_BUILD", "Composite build"),
//    RUNNER("RUNNER", "Runner");


    @NotNull
    private final String name;
    @NotNull
    private final String key;

    BuildStats(@NotNull String key, @NotNull String name) {
      this.key = key;
      this.name = name;
    }
  }


  private static boolean isTracingEnabled(SRunningBuild build) {
    @Nullable
    Branch branch = build.getBranch();
    boolean isDefaultBranch = branch == null || branch.isDefaultBranch();

    if (!isDefaultBranch ||
            build.isPersonal() ||
            build.getBuildType() == null) {
      return false;
    }

    return isBuildFeatureEnabled(build);
  }

  private static boolean isBuildFeatureEnabled(@NotNull SBuild sBuild) {
    Collection<SBuildFeatureDescriptor> descriptors = sBuild.getBuildFeaturesOfType(TracingDescriptor.BUILD_FEATURE_TYPE);

    return !descriptors.isEmpty();
  }
}