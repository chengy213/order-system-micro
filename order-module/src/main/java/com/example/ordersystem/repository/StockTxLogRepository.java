package com.example.ordersystem.repository;

import com.example.ordersystem.entity.StockTxLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockTxLogRepository extends JpaRepository<StockTxLog, String> {
    Optional<StockTxLog> findByTxId(String txId);
}