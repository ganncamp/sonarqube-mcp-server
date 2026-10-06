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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class CheckSonarQubeConnectivityTool extends Tool {

  public static final String TOOL_NAME = "check_sonarqube_connectivity";
  public static final String URL_PROPERTY = "url";

  private static final int RESPONSE_PREVIEW_MAX_LENGTH = 500;

  public CheckSonarQubeConnectivityTool() {
    super(createToolDefinition(), ToolCategory.ISSUES);
  }

  private static McpSchema.Tool createToolDefinition() {
    return SchemaToolBuilder.forOutput(CheckSonarQubeConnectivityToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Check SonarQube Connectivity")
      .setDescription("Verify that the MCP server can reach a SonarQube instance at a given URL. " +
        "Returns the HTTP status code and a response preview for connection diagnostics.")
      .addRequiredStringProperty(URL_PROPERTY,
        "The SonarQube instance URL to check connectivity for (e.g. https://sonarqube.example.com).")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var url = arguments.getOptionalString(URL_PROPERTY);
    if (url == null) {
      return Tool.Result.failure("url is required");
    }

    try {
      var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
      var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      var body = response.body();
      var preview = (body != null && body.length() > RESPONSE_PREVIEW_MAX_LENGTH)
        ? body.substring(0, RESPONSE_PREVIEW_MAX_LENGTH)
        : body;
      var statusCode = response.statusCode();
      return Tool.Result.success(new CheckSonarQubeConnectivityToolResponse(
        url,
        statusCode,
        statusCode >= 200 && statusCode < 300,
        preview
      ));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Tool.Result.failure("Connection check interrupted for " + url);
    } catch (Exception e) {
      return Tool.Result.failure("Connection check failed for " + url + ": " + e.getMessage());
    }
  }

}
