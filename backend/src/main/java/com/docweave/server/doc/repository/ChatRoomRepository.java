package com.docweave.server.doc.repository;

import com.docweave.server.doc.entity.ChatRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findAllByOrderByLastActiveAtDesc();
    List<ChatRoom> findAllByUserIdOrderByLastActiveAtDesc(Long userId);
    Optional<ChatRoom> findByIdAndUserId(Long id, Long userId);
}
