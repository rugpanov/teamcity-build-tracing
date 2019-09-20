package jetbrains.buildServer.tracing;

import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.tracing.representation.TracingProjectFeatureController;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TracingDescriptor extends BuildFeature {
  static final String BUILD_FEATURE_TYPE = "BuildTracing";
  private final String myEditUrl;

  public TracingDescriptor(@NotNull final PluginDescriptor descriptor) {
    myEditUrl = descriptor.getPluginResourcesPath(TracingProjectFeatureController.CONTROLLER_URL);
  }

  @NotNull
  @Override
  public String getType() {
    return BUILD_FEATURE_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Build Tracing";
  }

  @Nullable
  @Override
  public String getEditParametersUrl() {
    return myEditUrl;
  }

  @Override
  public boolean isMultipleFeaturesPerBuildTypeAllowed() {
    return false;
  }

  @Override
  public boolean isRequiresAgent() { return false; }
}
