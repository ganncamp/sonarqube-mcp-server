/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetProjectSecurityIssuesTool extends Tool {

  public static final String TOOL_NAME = "get_project_security_issues";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String ORGANIZATION_KEY_PROPERTY = "organizationKey";
  public static final String BRANCH_PROPERTY = BranchPullRequestContext.BRANCH_PROPERTY;
  public static final String PULL_REQUEST_PROPERTY = BranchPullRequestContext.PULL_REQUEST_PROPERTY;

  private static final String[] SECURITY_SEVERITIES = {"HIGH", "BLOCKER"};
  private static final String[] SECURITY_QUALITIES = {"SECURITY"};

  private final ServerApiProvider serverApiProvider;

  public GetProjectSecurityIssuesTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud) {
    super(createToolDefinition(isSonarQubeCloud), ToolCategory.ISSUES);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarQubeCloud) {
    var scope = isSonarQubeCloud ? "SonarQube Cloud" : "SonarQube Server";
    var description = "Retrieve high and blocker security vulnerabilities for a specific project on " + scope + ". " +
      "Returns issues filtered to SECURITY software quality at HIGH or BLOCKER severity.";

    return SchemaToolBuilder.forOutput(GetProjectSecurityIssuesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get Project Security Issues")
      .setDescription(description)
      .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The SonarQube project key to query.")
      .addStringProperty(ORGANIZATION_KEY_PROPERTY,
        "Organization key for the query. Defaults to the organization associated with your token.")
      .addBranchAndPullRequestProperties()
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var branchPullRequest = BranchPullRequestContext.from(arguments);
    var validationError = branchPullRequest.validationError();
    if (validationError.isPresent()) {
      return validationError.get();
    }

    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, null);
    var organization = arguments.getOptionalString(ORGANIZATION_KEY_PROPERTY);
    if (organization == null) {
      organization = serverApiProvider.get().getOrganization();
    }

    var searchParams = new IssuesApi.SearchParams(
      List.of(projectKey),
      branchPullRequest.branch(),
      null,
      branchPullRequest.pullRequest(),
      List.of(SECURITY_SEVERITIES),
      List.of(SECURITY_QUALITIES),
      null,
      null,
      null,
      null,
      null,
      null
    );

    var response = serverApiProvider.get().issuesApi().search(searchParams, organization);
    return Tool.Result.success(buildResponse(response));
  }

  private static GetProjectSecurityIssuesToolResponse buildResponse(SearchResponse response) {
    var issues = response.issues().stream()
      .map(issue -> new GetProjectSecurityIssuesToolResponse.SecurityIssue(
        issue.key(),
        issue.rule(),
        issue.project(),
        issue.component(),
        issue.severity(),
        issue.status(),
        issue.message(),
        issue.creationDate()
      ))
      .toList();
    return new GetProjectSecurityIssuesToolResponse(issues, response.paging().total());
  }

}
