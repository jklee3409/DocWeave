package com.docweave.server.doc.repository;

import com.docweave.server.doc.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.createdAt DESC LIMIT 6")
    List<ChatMessage> findTop6ByChatRoomIdOrderByCreatedAtDesc(@Param("roomId") Long roomId);
}
