package com.example.license.repository;

import com.example.license.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByAppIdAndSessionIdAndIdGreaterThanOrderByIdAsc(String appId, String sessionId, Long afterId);

    Optional<ChatMessage> findFirstByAppIdAndSessionIdOrderByIdDesc(String appId, String sessionId);

    @Query("select m.sessionId from ChatMessage m where m.appId = :appId "
            + "group by m.sessionId order by max(m.id) desc")
    List<String> findSessionIds(@Param("appId") String appId);
}
