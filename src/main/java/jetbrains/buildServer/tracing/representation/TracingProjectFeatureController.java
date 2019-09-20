/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.tracing.representation;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TracingProjectFeatureController extends BaseController {
  public static final String CONTROLLER_URL = "editTracingSettings.html";

  public TracingProjectFeatureController(@NotNull final SBuildServer server,
                                         @NotNull final WebControllerManager controllerManager,
                                         @NotNull final PluginDescriptor descriptor) {
    super(server);
    String myResourcePath = descriptor.getPluginResourcesPath(CONTROLLER_URL);
    controllerManager.registerController(myResourcePath, this);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull final HttpServletRequest request,
                                  @NotNull final HttpServletResponse response) {

    return new ModelAndView("tracingProjectFeature.jsp");
  }
}
