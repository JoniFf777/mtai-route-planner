package com.mtai.mtairouteplanner.data;

import java.util.Map;

public interface MockDataDatabaseRepository {

    MockDataBundle loadBundle();

    void replaceAll(MockDataBundle bundle);

    Map<String, Integer> rowCounts();
}
