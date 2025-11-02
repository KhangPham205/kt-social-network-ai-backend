package com.kt.social.domain.react.service.impl;

import com.kt.social.domain.react.dto.ReactRequest;
import com.kt.social.domain.react.dto.ReactResponse;
import com.kt.social.domain.react.enums.TargetType;
import com.kt.social.domain.react.model.React;
import com.kt.social.domain.react.model.ReactType;
import com.kt.social.domain.react.repository.ReactRepository;
import com.kt.social.domain.react.repository.ReactTypeRepository;
import com.kt.social.domain.post.model.Post;
import com.kt.social.domain.post.repository.PostRepository;
import com.kt.social.domain.react.service.ReactService;
import com.kt.social.domain.user.model.User;
import com.kt.social.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReactServiceImpl implements ReactService {

    private final ReactRepository reactRepository;
    private final ReactTypeRepository reactTypeRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @Override
    @Transactional
    public ReactResponse toggleReact(Long userId, ReactRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        TargetType tType = req.getTargetType();
        Long targetId = req.getTargetId();

        // check target exists if POST
        if (tType == TargetType.POST) {
            postRepository.findById(targetId).orElseThrow(() -> new RuntimeException("Post not found"));
        }

        var opt = reactRepository.findByUserAndTargetIdAndTargetType(user, targetId, tType);
        if (opt.isPresent()) {
            React existing = opt.get();
            // toggle: if same react type -> remove, else update type
            if (existing.getReactType().getId().equals(req.getReactTypeId())) {
                reactRepository.delete(existing);
            } else {
                ReactType newType = reactTypeRepository.findById(req.getReactTypeId())
                        .orElseThrow(() -> new RuntimeException("ReactType not found"));
                existing.setReactType(newType);
                reactRepository.save(existing);
            }
        } else {
            ReactType rt = reactTypeRepository.findById(req.getReactTypeId())
                    .orElseThrow(() -> new RuntimeException("ReactType not found"));
            React r = React.builder()
                    .user(user)
                    .targetId(targetId)
                    .targetType(tType)
                    .reactType(rt)
                    .createdAt(Instant.now())
                    .build();
            reactRepository.save(r);
        }

        // Recount and update post.likeCount if target is POST
        long count = reactRepository.countByTargetIdAndTargetType(targetId, tType);
        if (tType == TargetType.POST) {
            Post p = postRepository.findById(targetId).orElseThrow();
            p.setLikeCount((int) count);
            postRepository.save(p); // optimistic locking will help concurrency
        }

        return ReactResponse.builder()
                .targetId(targetId)
                .targetType(tType)
                .likeCount(count)
                .message("React updated")
                .build();
    }

    @Override
    public long countReacts(Long targetId, TargetType targetType) {
        return reactRepository.countByTargetIdAndTargetType(targetId, targetType);
    }
}