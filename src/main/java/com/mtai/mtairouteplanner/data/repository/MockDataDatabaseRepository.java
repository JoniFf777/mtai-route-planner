package com.mtai.mtairouteplanner.data.repository;

import com.mtai.mtairouteplanner.data.model.MockDataBundle;

import java.util.Map;

public interface MockDataDatabaseRepository {

    MockDataBundle loadBundle();

    void replaceAll(MockDataBundle bundle);

    Map<String, Integer> rowCounts();
}

