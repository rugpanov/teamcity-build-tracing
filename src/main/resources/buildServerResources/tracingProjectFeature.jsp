<%@ include file="/include-internal.jsp" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ page import="jetbrains.buildServer.tracing.common.Constants" %>
<jsp:useBean id="buildForm" type="jetbrains.buildServer.controllers.admin.projects.BuildTypeForm" scope="request"/>

<tr>
  <td colspan="2">
    <em>This build feature automatically assigns investigations of build failures to users.
      <a class='helpIcon' onclick='BS.AutoAssignerFeature.showHomePage()' title='View help'>
        <i class='icon icon16 tc-icon_help_small'></i>
      </a>
    </em>
  </td>
</tr>
<tr>
<tr>
  <th>
    <label for="<%= Constants.REPORTER_URL%>">Reporter URL:</label>
  </th>
  <td>
    <props:textProperty name="<%= Constants.REPORTER_URL%>" className="longField textProperty_max-width js_max-width"/>
    <span class="smallNote">Jaeger Tracing Reporter URL (default: localhost:5778).</span>
  </td>
</tr>