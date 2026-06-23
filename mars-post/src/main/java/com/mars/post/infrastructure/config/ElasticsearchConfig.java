package com.mars.post.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 仓库扫描配置
 * 当 search.elasticsearch.enabled=true 时启用 ES 仓库
 * 当 ES 不可用时，设为 false 即可让应用正常启动（搜索功能降级）
 */
@Configuration
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
@EnableElasticsearchRepositories(basePackages = "com.mars.post.domain.search")
public class ElasticsearchConfig {
}