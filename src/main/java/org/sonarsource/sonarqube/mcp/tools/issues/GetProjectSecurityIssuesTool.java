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
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetProjectSecurityIssuesTool extends Tool {

  public static final String TOOL_NAME = "get_project_security_issues";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String ORGANIZATION_KEY_PROPERTY = "organizationKey";
  public static final String BRANCH_PROPERTY = "branch";

  private final ServerApiProvider serverApiProvider;

  public GetProjectSecurityIssuesTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud) {
    super(createToolDefinition(isSonarQubeCloud), ToolCategory.ISSUES);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarQubeCloud) {
    var description = "Get high and blocker severity security vulnerabilities for a specific project. " +
      "More targeted than search_sonar_issues_in_projects — returns only open security issues at HIGH or BLOCKER severity. " +
      (isSonarQubeCloud ? "Use organizationKey to query a project in a different organization your token has access to." : "");

    return SchemaToolBuilder.forOutput(SearchIssuesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get Project Security Issues")
      .setDescription(description)
      .addRequiredStringProperty(PROJECT_KEY_PROPERTY,
        "The project key to retrieve security issues for. Use search_my_sonarqube_projects to find the key.")
      .addStringProperty(ORGANIZATION_KEY_PROPERTY,
        "Organization key to scope the query. Defaults to the organization bound to your token. " +
        "Specify this to access a project that belongs to a different organization your token has access to.")
      .addStringProperty(BRANCH_PROPERTY, "Branch to analyze. Defaults to the main branch.")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, null);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);

    // Use a caller-supplied organization if provided; fall back to the organization bound to the
    // configured token. This supports tokens with access to projects across multiple organizations.
    var organization = arguments.getOptionalString(ORGANIZATION_KEY_PROPERTY);
    if (organization == null) {
      organization = serverApiProvider.get().getOrganization();
    }

    var params = new IssuesApi.SearchParams(
      List.of(projectKey),
      branch,
      null,
      null,
      List.of("HIGH", "BLOCKER"),
      List.of("SECURITY"),
      List.of("OPEN", "CONFIRMED"),
      null,
      null,
      null,
      null,
      null
    );
    var response = serverApiProvider.get().issuesApi().search(params, organization);
    return Tool.Result.success(buildStructuredContent(response));
  }

  private static SearchIssuesToolResponse buildStructuredContent(SearchResponse response) {
    var issues = response.issues().stream()
      .map(issue -> {
        SearchIssuesToolResponse.TextRange textRange = null;
        if (issue.textRange() != null) {
          textRange = new SearchIssuesToolResponse.TextRange(
            issue.textRange().startLine(),
            issue.textRange().endLine()
          );
        }
        return new SearchIssuesToolResponse.Issue(
          issue.key(),
          issue.rule(),
          issue.project(),
          issue.component(),
          issue.severity(),
          issue.status(),
          issue.message(),
          issue.cleanCodeAttribute(),
          issue.cleanCodeAttributeCategory(),
          issue.author(),
          issue.creationDate(),
          textRange
        );
      })
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchIssuesToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    return new SearchIssuesToolResponse(issues, pagingResponse);
  }

}
