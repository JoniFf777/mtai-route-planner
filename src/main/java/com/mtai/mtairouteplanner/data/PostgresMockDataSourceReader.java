package com.mtai.mtairouteplanner.data;

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
