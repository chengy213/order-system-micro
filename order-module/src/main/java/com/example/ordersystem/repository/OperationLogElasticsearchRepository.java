package com.example.ordersystem.repository;

import com.example.ordersystem.entity.OperationLog;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationLogElasticsearchRepository extends ElasticsearchRepository<OperationLog, String> {
}