package com.lantin.unitrade.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.ItemPageQuery;
import com.lantin.unitrade.domain.dto.PageDTO;
import com.lantin.unitrade.service.ISearchService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements ISearchService {

    private RestHighLevelClient client;

    @PostConstruct  // 在bean创建之后就会执行该方法，用于创建RestHighLevelClient实例。
    public void initRestHighLevelClient() {
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://127.0.0.1:9200")
        ));
    }

    @PreDestroy // 确保在Bean销毁之前被调用，用于关闭RestHighLevelClient实例。
    public void closeRestHighLevelClient() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                // 处理异常，例如记录日志或抛出运行时异常
                throw new RuntimeException("Failed to close RestHighLevelClient", e);
            }
        }
    }


    /**
     * 在elasticsearch中搜索商品信息
     * 加入竞价排名（算分函数），购买了广告位的商品排在最前面
     * @param query
     * @return
     */
    @Override
    public PageDTO<ItemDTO> searchItemsInfo(ItemPageQuery query) throws IOException {
        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");

        // 2. 组织DSL参数
        // 2.1 根据前端传来的查询条件选择性添加bool查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 2.1.1 按关键字搜索并将广告位商品排在最前
        // 构建原始查询
        QueryBuilder queryBuilder;
        if (StrUtil.isNotBlank(query.getKey())) {
            queryBuilder = QueryBuilders.matchQuery("name", query.getKey());// 关键字非空则原始查询按照关键字匹配
        } else {
            queryBuilder = QueryBuilders.matchAllQuery();   // 若关键字未输入则查询所有
        }
        // 构建function_score查询的算分函数
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] filterFunctionBuilders = {
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        QueryBuilders.termQuery("isAD", true),  // 过滤条件：是广告的文档才会重新算分
                        ScoreFunctionBuilders.weightFactorFunction(100f)    // 算分函数：函数结果为常量100
                )
        };
        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(
                queryBuilder,   // 原始查询，基于此条件搜索文档会按照内部算法进行原始打分
                filterFunctionBuilders  // 过滤条件（符合该条件的文档才会重新算分）及算分函数（得到函数算分）
        ).boostMode(CombineFunction.REPLACE);// 运算模式，这里采用将原始算分替换为函数算分，确保广告位的商品相关性算分最高
        // 将算分查询加入到bool查询的must中
        boolQueryBuilder.must(functionScoreQueryBuilder);
        // 2.1.2 分类条件非空
        if (StrUtil.isNotBlank(query.getCategory())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));  // 按分类过滤但不参与算分
        }
        // 2.1.3 品牌条件非空
        if (StrUtil.isNotBlank(query.getBrand())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));    // 按品牌过滤但不参与算分
        }
        // 2.1.4 价格区间条件非空
        if (query.getMaxPrice() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").lte(query.getMaxPrice()));    // 价格最大值过滤
        }
        if (query.getMinPrice() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()));    // 价格最小值过滤
        }
        // 2.1.5 设置query查询条件
        request.source().query(boolQueryBuilder);

        // 2.2 根据前端传来的参数设置排序规则
        if (StrUtil.isNotBlank(query.getSortBy())) {
            // 2.2.1 前端如果传了排序规则就按传的排
            request.source().sort(query.getSortBy(), query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        } else {
            // 2.2.2 前端如果没传则默认按更新时间降序排
            request.source().sort("updateTime", SortOrder.DESC);
        }

        // 2.3 根据前端传来的分页参数设置分页
        // 如果前端没传，PageQuery中也给pageNo和pageSize设置了默认值，因此不用判空
        request.source().from((query.getPageNo() - 1) * query.getPageSize()).size(query.getPageSize());

        // 3. 发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        // 4. 解析结果并封装后返回
        return parseSearchResult(response, query.getPageNo());
    }


    /**
     * 解析es查询结果并封装
     * @param response
     * @param pageNo
     * @return
     */
    private PageDTO<ItemDTO> parseSearchResult(SearchResponse response, Integer pageNo) {
        SearchHits searchHits = response.getHits();
        PageDTO<ItemDTO> pageDTO = new PageDTO<>();
        // 1. 总条数
        pageDTO.setTotal(searchHits.getTotalHits().value);

        // 2. 查到的符合条件的文档数据
        List<ItemDTO> itemDTOS = Arrays.stream(searchHits.getHits())
                .map(hit -> JSONUtil.toBean(hit.getSourceAsString(), ItemDTO.class))    // 获取source结果（原始json）并转为ItemDTO
                .collect(Collectors.toList());  // 收集到List
        pageDTO.setList(itemDTOS);

        // 3. 设置页码
        pageDTO.setPages(pageNo.longValue());
        return pageDTO;
    }


    /**
     * 根据关键字搜索后找出对应的过滤条件，如品牌、分类等
     * @param query
     * @return
     * @throws IOException
     */
    @Override
    public Map searchItemFilters(ItemPageQuery query) throws IOException {
        // 1. 创建request对象
        SearchRequest request = new SearchRequest("items");
        // 2. 组织DSL参数
        // 2.1 query条件——关键词
        if (StrUtil.isNotBlank(query.getKey())) {
            request.source().query(QueryBuilders.matchQuery("name", query.getKey()));
        }
        // 2.2 聚合条件——Bucket聚合
        String categoryAggName = "categoryAgg";
        String brandAggName = "brandAgg";
        request.source().aggregation(
                AggregationBuilders.terms(categoryAggName)  // 聚合类型、聚合名称
                .field("category") // 聚合字段
                .size(10));
        request.source().aggregation(
                AggregationBuilders.terms(brandAggName)  // 聚合类型、聚合名称
                        .field("brand") // 聚合字段
                        .size(10));
        // 2.3 设置size为0，让返回只包含聚合结果，不包含文档
        request.source().size(0);
        // 3. 发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        // 4. 解析聚合结果
        Aggregations aggregations = response.getAggregations();
        Map<String, List<String>> resultMap = new HashMap<>(2);
        if (aggregations != null) {
            // 4.1 根据聚合名称获取对应的聚合结果
            Terms categoryAgg = aggregations.get(categoryAggName);
            Terms brandAgg = aggregations.get(brandAggName);

            if (categoryAgg != null) {
                // 4.2 获取其中的buckets
                List<? extends Terms.Bucket> categoryBuckets = categoryAgg.getBuckets();
                // 4.3 将buckets中的key存入结果map中
                resultMap.put("category", categoryBuckets.stream()
                        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
                        .collect(Collectors.toList()));
            }

            if (brandAgg != null) {
                // 4.2 获取其中的buckets
                List<? extends Terms.Bucket> brandBuckets = brandAgg.getBuckets();
                // 4.3 将buckets中的key存入结果map中
                resultMap.put("brand", brandBuckets.stream()
                        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
                        .collect(Collectors.toList()));
            }
        }

        // 5. 返回结果
        return resultMap;
    }
}
