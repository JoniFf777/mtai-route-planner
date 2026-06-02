package com.mtai.mtairouteplanner.data.source;

import com.mtai.mtairouteplanner.data.model.MockDataBundle;
import com.mtai.mtairouteplanner.data.repository.MockDataDatabaseRepository;

public class PostgresMockDataSourceReader implements MockDataSourceReader {

    private final MockDataDatabaseRepository repository;

    public PostgresMockDataSourceReader(MockDataDatabaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public MockDataBundle load() {
        return repository.loadBundle();
    }
}

