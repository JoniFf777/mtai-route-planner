package com.mtai.mtairouteplanner.data;

import java.util.Map;

public class MockDataDatabaseLoader {

    private final MockDataSourceReader sourceReader;
    private final MockDataDatabaseRepository repository;

    public MockDataDatabaseLoader(MockDataDatabaseRepository repository) {
        this(new ClasspathMockDataSourceReader(), repository);
    }

    public MockDataDatabaseLoader(MockDataSourceReader sourceReader, MockDataDatabaseRepository repository) {
        this.sourceReader = sourceReader;
        this.repository = repository;
    }

    public LoadSummary loadToDatabase() {
        MockDataBundle bundle = sourceReader.load();
        repository.replaceAll(bundle);
        return new LoadSummary(repository.rowCounts());
    }

    public record LoadSummary(Map<String, Integer> rowCounts) {
    }
}
