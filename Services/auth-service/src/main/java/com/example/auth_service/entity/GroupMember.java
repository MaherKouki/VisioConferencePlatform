package com.example.auth_service.entity;


import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_members")
@Data
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @ManyToOne
    @MapsId("groupId")
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false)
    private String role = "MEMBER";

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

}