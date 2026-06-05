package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogicTimeEntity {
    LocalDateTime time;
    Object data;
}
