package com.lantin.unitrade.service;



import com.lantin.unitrade.domain.dto.ItemDTO;
import com.lantin.unitrade.domain.dto.ItemPageQuery;
import com.lantin.unitrade.domain.dto.PageDTO;

import java.io.IOException;
import java.util.Map;

public interface ISearchService {
    PageDTO<ItemDTO> searchItemsInfo(ItemPageQuery query) throws IOException;

    Map searchItemFilters(ItemPageQuery query) throws IOException;
}
