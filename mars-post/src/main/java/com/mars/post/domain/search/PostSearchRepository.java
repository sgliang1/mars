package com.mars.post.domain.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostSearchRepository extends ElasticsearchRepository<PostDocument, Long> {
    Page<PostDocument> findByTitleOrContentContaining(String title, String content, Pageable pageable);
}