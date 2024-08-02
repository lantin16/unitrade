package com.lantin.unitrade.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 滚动分页返回值的封装类
 * @Author lantin
 * @Date 2024/8/1
 */

@Builder
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
