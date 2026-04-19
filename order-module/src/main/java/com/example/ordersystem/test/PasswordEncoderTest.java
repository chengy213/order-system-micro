package com.example.ordersystem.test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncoderTest {
    public static void main(String[] args) {
        long gene = 606311204230561794L & 0x7F;
        System.out.println("shardingKey="+gene%100);
        //mysql数据存储仍然是使用BCryptPasswordEncoder
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("123456"));
        System.out.println(encoder.encode("test123"));
    }
}