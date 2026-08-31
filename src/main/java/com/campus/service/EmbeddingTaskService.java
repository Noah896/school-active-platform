package com.campus.service;

public interface EmbeddingTaskService {

    void touchActivity(Long activityId, String trigger);

    void touchUser(Long userId, String trigger);

    void processPendingTasks();

    void ensureActivityBackfill();

    void ensureUserProfileEmbedding(Long userId);
}
