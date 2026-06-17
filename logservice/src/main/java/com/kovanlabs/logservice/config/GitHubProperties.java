package com.kovanlabs.logservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "github.api")
@Getter
@Setter
public class GitHubProperties {
    private String token = "";
    private String repoOwner = "";
    private String repoName = "";
    private String branch = "main";
}
