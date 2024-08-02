package com.lantin.unitrade.controller;


import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.ItemPageQuery;
import com.lantin.unitrade.domain.dto.PageDTO;
import com.lantin.unitrade.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.SearchService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Api(tags = "搜索相关接口")
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final ISearchService searchService;

    /**
     * 在elasticsearch中搜索商品信息
     * @param query 请求参数用ItemPageQuery接收
     * @return
     */
    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) throws IOException {
        log.info("商品搜索：query={}", query);
        return searchService.searchItemsInfo(query);
    }


    /**
     * 根据关键字搜索后找出对应的过滤条件，如品牌、分类等
     * @param query
     * @return
     */
    @ApiOperation("搜索过滤项")
    @PostMapping("/filters")
    public Map searchFilters(@RequestBody ItemPageQuery query) throws IOException {
        log.info("根据关键字获取过滤项：query={}", query);
        return searchService.searchItemFilters(query);
    }
}
